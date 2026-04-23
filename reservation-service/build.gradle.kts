plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common-infrastructure"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)

    // PR-2.1 — 재고 SoT 영속화 · 스키마 마이그레이션
    // spring-kafka · contracts 는 common-infrastructure 가 api() 로 노출하므로 별도 선언 불요.
    // hotel-events consumer 는 @KafkaListener 를 transitive 로 사용.
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector.j)

    // PR-2.2 — guest-service · rate-service 동기 gRPC 호출용 (FR-RSV-02).
    // contracts 의 stub 은 common-infrastructure 가 api() 로 노출하므로 별도 선언 불요.
    // 본 스타터는 @GrpcClient 주입 · 채널 설정만 추가한다.
    implementation(libs.grpc.client.spring.boot.starter)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(testFixtures(project(":common-infrastructure")))
    testRuntimeOnly(libs.junit.platform.launcher)

    // contextLoads 스모크 테스트는 MySQL 컨테이너를 기동하지 않고 H2 in-memory 로
    // 검증한다 (Testcontainers 는 @DataJpaTest · Kafka 통합 테스트에서만 사용).
    testRuntimeOnly(libs.h2)

    // PR-2.1 — hotel-events Kafka consumer 통합 테스트용. common-infrastructure 의
    // MysqlContainerExtension 에 대응하는 Kafka 컨테이너 확장은 reservation-service
    // 로컬에 먼저 두고, 다른 서비스에서도 Kafka consumer 가 생기면 common-infra 로 승격.
    testImplementation(libs.testcontainers.kafka)
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // PR-2.2 — gRPC 클라이언트 단위 테스트용 in-process 서버. 외부 네트워크 없이
    // 진짜 gRPC stub ↔ Status 매핑을 검증한다 (NOT_FOUND/UNAVAILABLE/INVALID_ARGUMENT).
    testImplementation(libs.grpc.inprocess)
}
