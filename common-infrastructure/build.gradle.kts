import org.gradle.api.artifacts.VersionCatalogsExtension

// 공통 Spring 설정 · 로깅 · 예외 표준 · 공용 빈을 담는 공유 모듈.
// 각 서비스가 implementation(project(":common-infrastructure")) 로 참조해 Spring
// 컨텍스트에 자동 포함시킨다. Spring Boot 관련 타입을 서비스 모듈이 직접 참조할 수
// 있도록 `api()` 로 노출한다.
plugins {
    `java-library`
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

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
