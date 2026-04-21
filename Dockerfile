# syntax=docker/dockerfile:1.7
# 멀티모듈 MSA 공통 Dockerfile.
# `SERVICE_MODULE` build arg 에 대상 Gradle 모듈명(예: hotel-service)을 전달해
# 각 서비스 이미지로 재사용한다. docker-compose.yml 의 각 app 서비스 block 이
# build.args.SERVICE_MODULE 로 지정.

FROM eclipse-temurin:21-jdk-jammy AS builder
ARG SERVICE_MODULE
RUN test -n "${SERVICE_MODULE}" \
    || { echo "ERROR: --build-arg SERVICE_MODULE=<module> is required"; exit 2; }
WORKDIR /workspace

# Gradle 래퍼 · 루트 설정 · 버전 카탈로그 (의존성 다운로드 캐시 레이어)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

# 모든 서브모듈 소스 (contracts · common-infrastructure 는 서비스 빌드 의존성)
COPY contracts ./contracts
COPY common-infrastructure ./common-infrastructure
COPY hotel-service ./hotel-service
COPY rate-service ./rate-service
COPY guest-service ./guest-service
COPY reservation-service ./reservation-service

# 지정된 서비스의 bootJar 만 빌드. 테스트는 CI 단계에서 별도 실행.
# BuildKit cache mount 로 Gradle 의존성 · 빌드 캐시를 레이어 간 보존해
# 소스 변경 시에도 의존성 재다운로드 없이 빠르게 재빌드한다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon ":${SERVICE_MODULE}:bootJar" -x test

FROM eclipse-temurin:21-jre-jammy
ARG SERVICE_MODULE
# HEALTHCHECK 가 /actuator/health 를 조회하기 위해 wget 필요. jre-jammy 기본 이미지에
# 포함되지 않아 별도 설치. ca-certificates 는 HTTPS 호출 시 기본 체인 확보 용.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=builder /workspace/${SERVICE_MODULE}/build/libs/*-SNAPSHOT.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
# Spring Boot Actuator 가 제공하는 /actuator/health 기반 헬스체크.
# common-infrastructure (PR-0.3) 의 spring-boot-starter-actuator 의존으로 기본 활성화.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=20 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
