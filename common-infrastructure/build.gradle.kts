import org.gradle.api.artifacts.VersionCatalogsExtension

// 공통 Spring 설정 · 로깅 · 예외 표준 · 공용 빈을 담는 공유 모듈.
// 각 서비스가 implementation(project(":common-infrastructure")) 로 참조해 Spring
// 컨텍스트에 자동 포함시킨다. Spring Boot 관련 타입을 서비스 모듈이 직접 참조할 수
// 있도록 `api()` 로 노출한다.
//
// 추가로 ArchUnit 기반 공용 아키텍처 규칙을 testFixtures 로 제공한다
// (com.reservation.common.architecture.ArchitectureRules). 각 서비스는
// testImplementation(testFixtures(project(":common-infrastructure"))) 로 이를 받아
// 자체 ArchitectureTest 에서 재사용한다.
plugins {
    `java-library`
    `java-test-fixtures`
    alias(libs.plugins.spring.dependency.management)
}

private val springBootVersion = extensions.getByType<VersionCatalogsExtension>()
    .named("libs").findVersion("spring-boot").get().requiredVersion

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
    }
}

dependencies {
    // common-infrastructure 가 @AutoConfiguration 으로 공용 빈(ClockConfig 등) 을
    // 제공하기 위해 필요. 소비 서비스 모듈에서도 @AutoConfiguration · @ConditionalOnXxx
    // 같은 타입을 참조할 수 있도록 api() 로 노출한다. 각 서비스가 spring-boot-starter-*
    // 를 통해 같은 의존성을 이미 들여오지만, 공통 모듈이 starter 에 의존하지 않도록
    // autoconfigure 를 직접 표기한다.
    api("org.springframework.boot:spring-boot-autoconfigure")

    // testFixtures 의 ArchitectureRules 가 ArchUnit 타입을 public API 로 노출하므로
    // testFixturesApi 로 선언. 이 모듈을 testFixtures() 로 받는 서비스는 별도의
    // archunit 의존성 선언 없이 규칙과 assertion 을 사용할 수 있다.
    testFixturesApi(libs.archunit.junit5)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
