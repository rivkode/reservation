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
}
