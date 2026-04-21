// 루트 프로젝트는 설정만 담당하며 자체 소스는 없다. Java 플러그인은 subprojects 에만 적용.
allprojects {
    group = "com.reservation"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Spring Boot 플러그인이 적용된 서비스 모듈은 plain jar 를 비활성화한다.
    // 이유: build/libs/ 에 bootJar 와 함께 생성되는 *-plain.jar 가 Dockerfile 의
    // glob 복사(`*-SNAPSHOT.jar`) 와 겹쳐 이미지 빌드가 비결정적으로 실패할 수 있다.
    // bootJar 만 남기면 배포 산출물이 단일해 예측 가능하다.
    plugins.withId("org.springframework.boot") {
        tasks.named("jar") {
            enabled = false
        }
    }
}
