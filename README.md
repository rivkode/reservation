# Hotel Reservation (MSA)

호텔 예약 시스템을 **MSA + DDD + Gradle 멀티모듈** 로 구현한다. 서비스간 통신은 동기 **gRPC** · 비동기 **Kafka**. 데이터는 **Database per Service (MySQL)**, 가용성 조회는 **Redis Read Model**.

> 상세 요구사항·아키텍처 결정은 [`docs/prd/hotel_reservation_prd.md`](docs/prd/hotel_reservation_prd.md) 와 [`docs/adr/`](docs/adr/) 참조.

> ⚠️ **본 리포지토리의 `docker-compose.yml` 과 `.env.example` 은 로컬 개발 전용**이며, 현재 단계의 HTTP API 에는 **인증·인가가 적용되지 않았다** (별도 보안 PRD 예정). 외부 노출 금지. 호스트 포트는 전부 `127.0.0.1` 루프백으로 제한되어 있다. 운영 배포 전 필수 상향 항목은 §11 "운영 배포 전 체크리스트" 참조.

---

## 1. 아키텍처 한눈에

```
┌──────────────────┐      ┌──────────────────┐
│   hotel-service  │      │    rate-service  │
│  (마스터 CRUD +  │      │   (요금 정책)    │
│   Redis Read     │      │                  │
│     Model)       │      │                  │
└────────┬─────────┘      └────────┬─────────┘
         │  hotel-events · rate-events (Kafka)
         ▼                         ▼
┌───────────────────────────────────────────┐
│           reservation-service              │
│   (예약 수명주기 · 재고 SoT)               │
│   ReservationCreated / Cancelled 발행     │
└────────┬──────────────────────────────────┘
         │ gRPC · Kafka
         ▼
┌──────────────────┐
│   guest-service  │
│ (투숙객 정보)    │
└──────────────────┘
```

**공유 모듈**
- `contracts` — 서비스간 **공개 계약**: gRPC proto + Kafka 이벤트 record
- `common-infrastructure` — 공통 Spring 설정 (Clock, ErrorResponse 등) · ArchUnit 규칙

---

## 2. 요구사항

| 도구 | 최소 버전 | 비고 |
|---|---|---|
| Docker Desktop (또는 docker + docker compose v2) | 24+ | macOS · Linux · Windows WSL2 |
| JDK | 21 | IDE 실행 · `./gradlew` 로컬 빌드용 (Docker 빌드는 불필요) |
| Git | 2.30+ | — |

> Gradle 은 `./gradlew` (wrapper, 8.10.2) 가 자동 다운로드하므로 별도 설치 불요.

---

## 3. 빠른 시작 (Quick Start)

### 3-1. 환경 변수 파일 준비
```bash
cp .env.example .env
```
기본 샘플 값을 그대로 써도 로컬 구동 가능. 필요에 따라 포트·비밀번호만 조정한다.
(실제 `.env` 는 `.gitignore` 로 제외.)

### 3-2. 전체 스택 기동 (인프라 + 4 서비스)
```bash
docker compose up -d --build
```
- 첫 실행: MySQL · Redis · Kafka 이미지 pull + 각 서비스 Gradle 빌드로 **5~10 분** 소요.
- 두 번째 실행부터는 레이어 캐시로 **< 30초**.

### 3-3. 기동 상태 확인
```bash
docker compose ps
```
모든 서비스가 `Up (healthy)` 이 될 때까지 1~2 분 대기.

### 3-4. 서비스 응답 확인
```bash
curl http://127.0.0.1:8081/actuator/health   # hotel-service
curl http://127.0.0.1:8082/actuator/health   # rate-service
curl http://127.0.0.1:8083/actuator/health   # guest-service
curl http://127.0.0.1:8084/actuator/health   # reservation-service
```
> Phase 0 단계에서는 **실제 API 구현이 없음** — `/actuator/health` 로 기동만 검증. 비즈니스 엔드포인트는 Phase 1 이후 각 서비스 PR 에서 추가.

### 3-5. 전체 정리
```bash
docker compose down           # 컨테이너만 내림 (볼륨 유지)
docker compose down -v        # 볼륨까지 완전 초기화
```

---

## 4. 서비스 · 포트 매핑

호스트 측 포트는 모두 **`127.0.0.1`(로컬 루프백)** 에만 바인딩된다. 외부 노출 X.

| 서비스 / 인프라 | 호스트 포트 | 컨테이너 내부 | 접속 |
|---|---|---|---|
| hotel-service | **8081** | 8080 | `http://127.0.0.1:8081` |
| rate-service | **8082** | 8080 | `http://127.0.0.1:8082` |
| guest-service | **8083** | 8080 | `http://127.0.0.1:8083` |
| reservation-service | **8084** | 8080 | `http://127.0.0.1:8084` |
| hotel-db (MySQL) | **3307** | 3306 | `mysql -h 127.0.0.1 -P 3307 -u reservation -p` |
| rate-db (MySQL) | **3308** | 3306 | 위와 동일 포트만 교체 |
| guest-db (MySQL) | **3309** | 3306 | 〃 |
| reservation-db (MySQL) | **3310** | 3306 | 〃 |
| redis | **6379** | 6379 | `redis-cli -h 127.0.0.1 -a $REDIS_PASSWORD` |
| kafka | **29092** | 9092 | `kafka-topics.sh --bootstrap-server 127.0.0.1:29092 --list` |

포트는 `.env` 에서 `*_PORT` 변수로 오버라이드 가능.

---

## 5. DB · Kafka · Redis 접속

### MySQL
```bash
# 예: hotel-db 접속 (호스트에서)
docker exec -it hotel-db mysql -u reservation -p hotel
# 또는 외부 클라이언트 (DBeaver 등) 로 127.0.0.1:3307 ~ 3310 로 붙는다.
```

### Redis
```bash
# 컨테이너 내부의 REDIS_PASSWORD env 를 그대로 사용 (shell history · process list 노출 회피)
docker exec -it hotel-redis sh -lc 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning'
> PING
PONG
```

### Kafka (호스트에서 CLI)
```bash
# 컨테이너 내부 kafka-topics.sh 사용이 가장 간편
docker exec -it hotel-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# 토픽 생성 (Phase 1 이후 사용)
docker exec -it hotel-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create \
  --topic hotel-events --partitions 3 --replication-factor 1
```
> 토픽은 자동 생성되지 않도록 `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false` 로 설정되어 있다. 각 서비스 Application Layer 에서 명시적으로 생성한다 (Phase 2~3).

---

## 6. 로컬 개발 (IDE 에서 개별 서비스 실행)

전체 스택 대신 **인프라만 Docker 로 띄우고 app 은 IDE 에서 디버깅**하는 패턴을 권장한다.

### 6-1. 인프라만 기동
```bash
# Docker app 서비스를 내려 포트 충돌(8081~8084) 회피 후 인프라만 기동
docker compose stop hotel-service rate-service guest-service reservation-service 2>/dev/null || true
docker compose up -d hotel-db rate-db guest-db reservation-db redis kafka
```
> IDE 에서 실행할 서비스는 `application.yml` 의 기본 포트 (hotel=8081 · rate=8082 · guest=8083 · reservation=8084) 를 사용하므로 **동일 포트의 Docker app 컨테이너는 반드시 정지** 해야 한다.

### 6-2. IDE 에서 서비스 실행
IntelliJ 등에서 각 서비스의 `XxxServiceApplication.main()` Run 설정에 환경 변수 주입:

```
SPRING_PROFILES_ACTIVE=local
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3307/hotel?useSSL=false&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=reservation
SPRING_DATASOURCE_PASSWORD=change-me-app
SPRING_DATA_REDIS_HOST=127.0.0.1
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=change-me-redis
SPRING_KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:29092
```

rate/guest/reservation 서비스도 같은 패턴으로 포트만 교체 (3308/3309/3310).

### 6-3. 전체 빌드 · 테스트
```bash
./gradlew clean build      # 전체 모듈
./gradlew :hotel-service:build   # 단일 서비스
```

---

## 7. 자주 쓰는 명령

| 목적 | 명령 |
|---|---|
| 특정 서비스 로그 실시간 | `docker compose logs -f hotel-service` |
| 특정 서비스 재시작 | `docker compose restart hotel-service` |
| 코드 변경 후 이미지 재빌드 | `docker compose up -d --build hotel-service` |
| 전체 상태 요약 | `docker compose ps` |
| 볼륨 포함 완전 초기화 | `docker compose down -v` |
| 인프라만 남기고 app 내리기 | `docker compose stop hotel-service rate-service guest-service reservation-service` |

---

## 8. 트러블슈팅

### `docker compose up` 이 `MYSQL_ROOT_PASSWORD must be set` 으로 실패
→ `.env` 파일이 없음. `cp .env.example .env` 먼저 실행.

### 포트 충돌 (`bind: address already in use`)
→ `.env` 의 `*_PORT` 값을 비어 있는 포트로 수정 후 재기동.

### app 서비스가 `unhealthy` 로 남음
→ DB/Kafka 보다 app 이 먼저 떠서 Spring context 초기화 실패인 경우가 많다.
```bash
docker compose logs hotel-service --tail 100
```
로 예외를 확인한 뒤 `docker compose restart hotel-service`.

### MySQL 데이터를 초기화하고 싶을 때
```bash
docker compose down
docker volume rm hotel-reservation_hotel-db-data   # 또는 전부
docker compose up -d
```

### Kafka CLI 가 호스트에 없는데 토픽을 확인하고 싶을 때
→ 컨테이너 내부 CLI 사용:
```bash
docker exec -it hotel-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

---

## 9. 관련 문서

- **PRD**: [`docs/prd/hotel_reservation_prd.md`](docs/prd/hotel_reservation_prd.md)
- **ADR**: [`docs/adr/`](docs/adr/)
  - `0001-domain-event-serialization.md` — Kafka 이벤트 다형성 전략
- **Claude Code 워크플로우**: [`CLAUDE.md`](CLAUDE.md)
- **아키텍처 규칙 (ArchUnit)**: `.claude/skills/module-boundary/references/archunit-rules.md`
- **서비스간 경계 원칙**: `.claude/skills/module-boundary/SKILL.md`

---

## 11. 운영 배포 전 체크리스트 (LOCAL ONLY → Staging/Prod 전환 시)

본 `docker-compose.yml` 은 **로컬 전용** 단순화를 포함한다. 동일 구성을 운영에 그대로 이전하면 안 된다. Staging/Prod 환경 이관 시 최소한 아래를 상향해야 한다.

| 카테고리 | 로컬 상태 | 운영 요구 |
|---|---|---|
| 네트워크 바인딩 | `127.0.0.1:` 루프백만 | Ingress / LB 뒤로 배치, 외부 호스트 포트 노출 금지 |
| MySQL 전송 | `useSSL=false` (평문) | `useSSL=true` + CA 인증서 pinning |
| MySQL 계정 | 4 DB 동일 `MYSQL_USER` 공유 | 서비스별 독립 계정 (`hotel_app` · `rate_app` · …) + 최소 권한 |
| Redis | `requirepass` + PLAINTEXT | TLS (`rediss://`) + ACL 유저 분리 |
| Kafka | KRaft 단일 브로커 · PLAINTEXT | 최소 3-broker 클러스터 + SASL/SCRAM + TLS, 토픽별 ACL |
| 인증/인가 | 전부 미적용 | 별도 보안 PRD (OAuth2 · JWT) 적용, Gateway 에서 통합 |
| Actuator 노출 | 기본값 (`health` · `info`) | `management.endpoints.web.exposure.include` 를 필요한 엔드포인트만 화이트리스트 |
| Observability | 없음 | Prometheus + Grafana + 로그 집계 (PR-4.3) |
| 컨테이너 User | `useradd --system` 기본 UID | 고정 UID (`--uid 10001`) + 이미지 digest pin |
| Secret 관리 | `.env` 파일 | Vault / AWS Secrets Manager 연동, compose 환경변수 직접 주입 금지 |

## 10. 현재 진행 단계 (Phase 0 — 기반 구축)

| PR | 내용 | 상태 |
|---|---|---|
| PR-0.1 | Gradle Kotlin DSL 멀티모듈 골격 | ✅ |
| PR-0.2 | contracts: gRPC proto + Kafka 이벤트 record + DomainEvent | ✅ |
| PR-0.3 | common-infrastructure: Clock · ErrorResponse · ADR 0001 | ✅ |
| PR-0.4 | ArchUnit 아키텍처 규칙 세팅 | ✅ |
| **PR-0.5** | **Docker Compose MSA 재구성 + 본 README** | 🚧 |
| Phase 1+ | 서비스별 도메인 구현 (병렬) | ⏳ |
