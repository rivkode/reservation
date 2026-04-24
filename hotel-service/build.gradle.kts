plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common-infrastructure"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)

    // Phase 1 — 마스터 데이터 영속화 · 스키마 마이그레이션
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector.j)

    // PR-3.1 — RoomAvailabilityView Read Model 을 Redis Hash 로 유지 (HINCRBY 기반
    // 원자 증감). spring-kafka · contracts 는 common-infrastructure 가 api() 로 노출
    // 하므로 별도 선언 불요 — reservation-events Kafka consumer 는 @KafkaListener 를
    // transitive 로 사용한다.
    implementation(libs.spring.boot.starter.data.redis)

    // PR-3.3 — 캐시 재구축 배치(FR-H-08) 가 reservation-service 의 StreamInventory
    // gRPC 를 호출하기 위한 클라이언트 스타터. contracts 모듈의 stub 과 결합.
    implementation(libs.grpc.client.spring.boot.starter)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(testFixtures(project(":common-infrastructure")))
    testRuntimeOnly(libs.junit.platform.launcher)

    // contextLoads 스모크 테스트는 MySQL 컨테이너를 기동하지 않고 H2 in-memory 로
    // 검증한다 (Testcontainers 는 @DataJpaTest · 통합 테스트에서만 사용).
    testRuntimeOnly(libs.h2)

    // PR-3.3 — ReservationInventoryGrpcClient 단위 테스트용 in-process 서버. 외부 네트워크
    // 없이 진짜 gRPC stub ↔ Status 매핑을 검증한다 (UNAVAILABLE/DEADLINE_EXCEEDED/정상).
    testImplementation(libs.grpc.inprocess)
}
