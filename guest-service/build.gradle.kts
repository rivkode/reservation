plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common-infrastructure"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)

    // Phase 1 PR-1.3 — 투숙객 영속화 · 스키마 마이그레이션
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector.j)

    // 프로젝트 최초 gRPC 서버. net.devh starter 가 @GrpcService 스캔 · 서버 포트
    // 자동 구성 · Reflection/Health 서비스 (옵션) 를 제공한다. contracts 모듈이 이미
    // grpc-stub/protobuf/netty-shaded 를 api() 로 노출하므로 여기서는 starter 만 추가.
    implementation(libs.grpc.server.spring.boot.starter)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(testFixtures(project(":common-infrastructure")))
    testRuntimeOnly(libs.junit.platform.launcher)

    // contextLoads 스모크 및 @WebMvcTest 는 MySQL 컨테이너를 기동하지 않고 H2 in-memory
    // 로 검증한다 (Testcontainers 는 @DataJpaTest · 통합 테스트에서만 사용).
    testRuntimeOnly(libs.h2)

    // gRPC 서비스 단위 테스트용 in-process transport — InProcessServerBuilder /
    // InProcessChannelBuilder 를 통해 네트워크 없이 stub 호출 경로를 검증한다.
    testImplementation(libs.grpc.inprocess)
}
