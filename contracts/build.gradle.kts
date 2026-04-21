import com.google.protobuf.gradle.id
import org.gradle.api.artifacts.VersionCatalogsExtension

// 서비스간 공개 계약 모듈.
// - src/main/proto/ 의 gRPC 서비스 정의를 protoc + protoc-gen-grpc-java 로 컴파일
// - src/main/java/com/reservation/contracts/event/ 의 Kafka 이벤트 record 를 함께 포함
// 서비스 모듈은 implementation(project(":contracts")) 로 참조한다.
// 생성 stub · 이벤트 record 는 api() 로 전이 노출한다.
plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

// protobuf { protoc { artifact = ... } } 블록 안에서는 Kotlin DSL 의
// `libs.versions.x.get()` typed accessor 가 외부 플러그인 DSL scope 로 전파되지 않는
// 제약이 있다 (Gradle 8/9 공통). VersionCatalogsExtension API 로 직접 읽어 단일
// 소스(libs.versions.toml)에서 버전을 관리한다.
private val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
private val protobufVersion = versionCatalog.findVersion("protobuf").get().requiredVersion
private val grpcVersion = versionCatalog.findVersion("grpc").get().requiredVersion

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

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
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
