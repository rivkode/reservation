# UUID v7 라이브러리 선택 분석

- **날짜**: 2026-04-22
- **관련 PR**: #8 (PR-base, 커밋 `207d252`)
- **관련 ADR**: [`0001-domain-event-serialization`](../../docs/adr/0001-domain-event-serialization.md) — ADR 의 "UUID ↔ BINARY(16) 변환 경계" 가 본 분석의 전제
- **관련 PRD 섹션**: §11.2 (reservationId = UUID v7)

## 배경

PR-base 에서 `com.reservation.common.domain.UuidV7` 을 **직접 구현** 으로 작성했다 (RFC 9562 §5.7 기반, `SecureRandom` 사용, `Clock` 주입 가능, 약 62줄).

프로젝트 오너가 리뷰 중 다음을 지적:

> "왜 라이브러리를 쓰지 않고 직접 만들었는가? `com.fasterxml.uuid:java-uuid-generator:5.1.0` 를 쓰면
> `Generators.timeBasedEpochGenerator().generate()` 한 줄로 끝난다."

이 지적을 계기로 자체 구현 vs 라이브러리 선택을 재평가.

## 처음 자체 구현을 택한 이유

1. **의존성 최소화 선호** — Plan §5 D10 의 "필요 시점에만 의존성 추가" 원칙
2. **Clock 주입 테스트 친화성** — 라이브러리가 Clock 주입을 지원하는지 즉시 확인하지 않음
3. **RFC 9562 §5.7 사양이 단순해 보임** — 타임스탬프 + version nibble + random → 30줄이면 될 것으로 판단
4. **JDK 표준 지원 대기 심리** — JDK 24 에서 v7 native 지원 예정 (추정) → 그때까지 자체 구현으로 버티자

이 판단들은 겉으로는 합리적이지만 **본질적인 결함을 놓쳤다.**

## 분석 — 자체 구현의 숨은 결함 3건

### 1. Same-millisecond 내 lexicographic 순서 보장 부재 ⚠️

RFC 9562 §5.7 의 UUID v7 **존재 이유** 는 "시간 순으로 정렬 가능한 UUID" 다.

- 자체 구현: 타임스탬프 48bit + version 4bit + **random 74bit** 만으로 구성
- 같은 millisecond 에서 생성된 두 UUID 는 **완전 random suffix** 만 다름
- 결과: lexicographic 정렬이 시간 순서와 **일치하지 않는다**

영향:
- MySQL 클러스터드 인덱스(PK) 의 순차 삽입 이점 소실 → 페이지 split 증가
- 이벤트 스트림에서 UUID 만으로 순서 추론 불가

**라이브러리 해결**: JUG 의 `timeBasedEpochGenerator()` 는 RFC 9562 §5.7 의 **rand_a 12bit 를 sub-millisecond counter 로** 활용 — 동일 ms 에서 counter 증가로 정렬 보장. 이게 v7 의 실전 가치.

### 2. Clock-goes-backwards 방어 부재

NTP 보정 · VM 일시 정지 · 서버 시각 수동 조정 등으로 시스템 시계가 뒤로 돌아가는 상황이 드물지만 발생.

- 자체 구현: 뒤로 감긴 시각 그대로 UUID 생성 → 정렬 역전
- JUG: last-seen timestamp 를 내부에 track, 역전 감지 시 이전 ms + counter 증가 로직으로 **단조 증가 강제**

### 3. `SecureRandom` 전역 공유로 contention hotspot

- 자체 구현: `private static final SecureRandom RANDOM = new SecureRandom();`
- `SecureRandom` 은 thread-safe 이지만 내부에 `synchronized` 구간이 존재 (구현에 따라)
- 예약 폭주 (초당 수천 ID) 시 lock 경쟁

**라이브러리 해결**: JUG 는 ThreadLocal 기반 random source 로 경쟁 없음.

## Trade-off 비교표

| 기준 | 직접 구현 | **JUG 5.1.0** |
|---|---|---|
| 의존성 추가 | 없음 | ~200 KB jar (Apache 2.0) |
| RFC 9562 §5.7 전부 준수 | timestamp + version + random만 ✓<br>monotonic ✗<br>clock-backwards ✗ | **전부 ✓** |
| Same-ms 정렬 | ❌ | ✅ |
| 성능 (고부하) | `SecureRandom` lock 경쟁 | ThreadLocal 최적화 |
| 유지보수 | 우리가 유지 + 테스트 6개 | Jackson 저자가 관리 (Tatu Saloranta), 활발한 프로젝트 |
| 테스트 Clock 주입 | `create(Clock)` 오버로드 | 기본 API 미지원, 필요 시 `new TimeBasedEpochGenerator(UUIDClock)` |
| 코드 규모 | `UuidV7` 62줄 + `UuidV7Test` 80줄 ≈ 142줄 | wrapper 20줄 + smoke 테스트 3건 |

## 판단 · 결정

**JUG 채택이 명확히 우세.**

의사결정의 핵심은 "의존성 최소화" 의 가치 < "monotonic + clock-backwards 방어" 의 가치라는 점이다. v7 의 존재 이유가 시간 순 정렬이고, 정렬 보장 없는 자체 구현은 **이름만 v7 이고 실질은 v4** 에 가깝다.

### 채택: `com.fasterxml.uuid:java-uuid-generator:5.1.0`

- Apache 2.0 라이선스
- Tatu Saloranta (Jackson 원저자) 가 유지
- GitHub: `cowtowncoder/java-uuid-generator`
- v1 / v3 / v4 / v5 / v6 / v7 전부 지원

### 구현 변경

```java
// common-infra/src/main/java/com/reservation/common/domain/UuidV7.java
public final class UuidV7 {
    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();
    private UuidV7() {}
    public static UUID create() { return GENERATOR.generate(); }
}
```

- `create(Clock)` 오버로드 제거 — 테스트에서 고정 시각이 필요하면 JUG 의 `UUIDClock` 을 주입한 별도 generator 인스턴스로 처리 (현재 사용처 없음)

### 테스트 변경

6개 테스트 → **smoke 3개** 로 축소:
1. `version == 7 · variant == 2` 비트 세팅
2. 연속 1000회 생성 시 lexicographic 순서 단조 증가 (라이브러리 계약 검증)
3. 10000회 생성 시 고유성 (collision 없음)

비트 레이아웃 · clock-backwards · contention 등 라이브러리 내부 보장은 JUG 의 자체 테스트에 위임.

## 결과 · 후속

- **PR #8 에 추가 커밋 `207d252`** 으로 반영
- diff: +60 / -102 (net 42줄 감소)
- `./gradlew :common-infrastructure:build` 11초 통과 · 3 smoke test 모두 pass

### 남은 교훈

1. **"의존성 최소화" 는 항상 선이 아니다** — 의존성 200KB vs 결함 있는 자체 구현을 유지할 비용·리스크 비교 필요
2. **테스트 수는 커버리지가 아니다** — 자체 구현의 테스트 6개가 있었음에도 "same-ms 정렬" 이라는 v7 의 핵심 시나리오를 검증 항목에 포함하지 않아 결함이 숨겨졌다
3. **"표준 라이브러리가 없어서 직접 구현" 은 보통 마지막 선택지여야 한다** — 생태계에 이미 검증된 라이브러리가 있으면 우선 검토

### 이 결정을 뒤집어야 할 조건

- JDK 가 `UUID.createV7()` 같은 표준 API 를 제공하고 monotonic · clock-backwards 까지 보장 → JUG 제거 검토
- JUG 라이브러리가 유지 중단 → 대체 라이브러리 (`uuid-creator` f4b6a3, Hypersistence Utils 내장 generator 등) 로 교체
