import com.google.protobuf.gradle.id

// 서비스간 공개 계약 모듈.
// - src/main/proto/ 의 gRPC 서비스 정의를 protoc + protoc-gen-grpc-java 로 컴파일
// - src/main/java/com/reservation/contracts/event/ 의 Kafka 이벤트 record 를 함께 포함
// 서비스 모듈은 implementation(project(":contracts")) 로 참조한다.
// 생성 stub · 이벤트 record 는 api() 로 전이 노출한다.
plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

dependencies {
    api(libs.protobuf.java)
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    api(libs.grpc.netty.shaded)

    // gRPC Java 가 생성하는 stub 이 @javax.annotation.Generated 를 참조한다.
    // JDK 11+ 에는 javax.annotation 패키지가 번들되지 않으므로 compileOnly 로 추가.
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// NOTE: Gradle 9 + protobuf-gradle-plugin 0.9.4 조합에서 protobuf {} 블록 scope 의
// `libs.versions.*.get()` 이 resolve 되지 않아 (VersionCatalog typed accessor 이슈)
// 버전 문자열을 직접 박아 사용한다. libs.versions.toml 의 값과 수동으로 싱크를 유지할 것.
// 라이브러리 의존성(api(libs.protobuf.java) 등)은 영향 없이 카탈로그에서 관리된다.
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.68.1"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}

// protobuf plugin 이 build/generated/source/proto/ 에 생성한 Java 소스를
// main/test sourceSet 의 compileJava 가 자동으로 인식하도록 plugin 이 설정해 준다.
// 추가 sourceSets 설정 불필요.
