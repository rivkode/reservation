package com.reservation.common.architecture;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * MSA · DDD 아키텍처 원칙을 런타임 테스트로 강제하는 공용 규칙 모음.
 *
 * <p>각 서비스 모듈의 {@code XxxServiceArchitectureTest} 가 본 유틸의 정적 메서드를
 * {@code @ArchTest} 필드로 꽂아 쓴다. 도메인 코드가 비어 있는 Phase 0 에서는 대부분
 * vacuously true 이지만 Phase 1 이후 실제 코드가 들어오면 곧바로 enforce 된다.
 *
 * <p>본 유틸은 {@code common-infrastructure} 모듈의 {@code testFixtures} 에 위치하며
 * 서비스 모듈은 {@code testImplementation(testFixtures(project(":common-infrastructure")))}
 * 로 받아 쓴다.
 */
public final class ArchitectureRules {

    private ArchitectureRules() {
    }

    /**
     * 본 서비스가 다른 서비스의 내부 패키지를 직접 import 하지 않음을 강제한다.
     * 화이트리스트 방식: {@code com.reservation..} 중 본 서비스 · {@code contracts} · {@code common}
     * 외 모든 패키지 참조를 금지하므로, 향후 서비스가 추가되어도 규칙 수정이 불필요하다.
     */
    public static ArchRule noCrossServiceImports(String ownService) {
        return noClasses()
            .that().resideInAPackage("com.reservation." + ownService + "..")
            .should().dependOnClassesThat(
                resideInAPackage("com.reservation..")
                    .and(resideOutsideOfPackages(
                        "com.reservation." + ownService + "..",
                        "com.reservation.contracts..",
                        "com.reservation.common..")))
            .because("서비스 간 직접 import 금지 — contracts / common 모듈만 허용");
    }

    /**
     * DDD Layered 구조를 선언한다. "누가 날 부르는가" 만 제어하므로 DIP 역방향
     * (Application → Infrastructure) 위반은 {@link #applicationDoesNotDependOnInfrastructure}
     * 등 전용 규칙으로 별도 강제한다.
     */
    public static LayeredArchitecture layerDependencies(String basePackage) {
        // withOptionalLayers(true): Phase 0 단계에서는 4 개 레이어 패키지가 모두
        // 비어 있어도 규칙이 통과해야 하므로 각 layer 를 optional 로 선언한다.
        // Phase 3 이후 레이어들이 안정적으로 채워지면 서비스별 override 로
        // `false` 전환 검토. 비어 있더라도 의존 방향 규칙은 그대로 enforce 된다.
        return layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(basePackage + ".domain..")
            .layer("Application").definedBy(basePackage + ".application..")
            .layer("Infrastructure").definedBy(basePackage + ".infrastructure..")
            .layer("Presentation").definedBy(basePackage + ".presentation..")
            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation", "Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            .withOptionalLayers(true);
    }

    /**
     * Application 은 Infrastructure 를 알지 못한다 — 의존성 역전 원칙.
     * Infrastructure 는 Application 의 Port(Repository 인터페이스 등) 를 구현할 뿐,
     * Application 이 Infrastructure 의 구상 타입을 import 해서는 안 된다.
     */
    public static ArchRule applicationDoesNotDependOnInfrastructure(String basePackage) {
        return noClasses()
            .that().resideInAPackage(basePackage + ".application..")
            .should().dependOnClassesThat().resideInAPackage(basePackage + ".infrastructure..")
            .because("Application 은 Infrastructure 를 모른다 (CLAUDE.md 원칙 #4)");
    }

    /**
     * Presentation 은 Infrastructure 를 직접 접근하지 않는다 — Application Service 를 경유한다.
     */
    public static ArchRule presentationDoesNotDependOnInfrastructure(String basePackage) {
        return noClasses()
            .that().resideInAPackage(basePackage + ".presentation..")
            .should().dependOnClassesThat().resideInAPackage(basePackage + ".infrastructure..")
            .because("Presentation 은 Infrastructure 를 모르고 Application Service 만 경유한다");
    }

    /**
     * Domain 계층은 Spring / JPA / Jackson / gRPC / Kafka / protobuf 에 의존하지 않는다.
     * (CLAUDE.md 원칙 #4 "도메인 객체 ≠ DB Entity", 프레임워크 중립성)
     */
    public static ArchRule domainHasNoFrameworkDependency(String basePackage) {
        return noClasses()
            .that().resideInAPackage(basePackage + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "javax.persistence..",
                "com.fasterxml.jackson..",
                "io.grpc..",
                "org.apache.kafka..",
                "com.google.protobuf..")
            .because("도메인은 프레임워크에 의존하지 않는다 (CLAUDE.md 원칙 #4)");
    }

    /**
     * Domain 은 {@code com.reservation.contracts..} 패키지(proto stub, 이벤트 record)를
     * 모른다. 변환은 Infrastructure / Application 에서만 수행.
     */
    public static ArchRule domainMustNotReferenceContracts(String basePackage) {
        return noClasses()
            .that().resideInAPackage(basePackage + ".domain..")
            .should().dependOnClassesThat().resideInAPackage("com.reservation.contracts..")
            .because("Domain 은 contracts 를 알 수 없다 — 변환은 Infrastructure / Application 책임");
    }

    /**
     * {@code domain.repository} 하위의 타입은 모두 interface (도메인 포트) 여야 한다.
     * 구현체는 {@link #repositoryImplsOnlyInInfrastructure} 로 위치가 제약된다.
     */
    public static ArchRule domainRepositoriesAreInterfaces(String basePackage) {
        return classes()
            .that().resideInAPackage(basePackage + ".domain.repository..")
            .should().beInterfaces()
            .because("domain.repository 는 Port 인터페이스만 — 구현은 infrastructure 에서");
    }

    /**
     * {@code *RepositoryImpl} 클래스는 {@code infrastructure/persistence/repository/} 에만 위치한다.
     */
    public static ArchRule repositoryImplsOnlyInInfrastructure(String basePackage) {
        return classes()
            .that().haveSimpleNameEndingWith("RepositoryImpl")
            .should().resideInAPackage(basePackage + ".infrastructure.persistence.repository..")
            .because("Repository 구현은 infrastructure/persistence/repository 에만");
    }

    /**
     * {@code domain.exception} 하위 클래스는 네이밍이 {@code ...Exception} 으로 끝난다.
     */
    public static ArchRule domainExceptionsEndWithExceptionSuffix(String basePackage) {
        return classes()
            .that().resideInAPackage(basePackage + ".domain.exception..")
            .should().haveSimpleNameEndingWith("Exception")
            .because("domain.exception 의 클래스는 Exception suffix 로 끝난다");
    }

    /**
     * {@code @net.devh.boot.grpc.server.service.GrpcService} 로 노출되는 gRPC 서비스 구현은
     * {@code infrastructure/grpc/server/} 에만 둔다.
     */
    public static ArchRule grpcServiceImplsOnlyInServerPackage(String basePackage) {
        return classes()
            .that().areAnnotatedWith("net.devh.boot.grpc.server.service.GrpcService")
            .should().resideInAPackage(basePackage + ".infrastructure.grpc.server..")
            .because("@GrpcService 는 infrastructure/grpc/server 에만");
    }

    /**
     * Kafka 리스너는 {@code infrastructure/messaging/} 패키지에서만 선언한다.
     */
    public static ArchRule kafkaListenerOnlyInMessaging(String basePackage) {
        return methods()
            .that().areAnnotatedWith("org.springframework.kafka.annotation.KafkaListener")
            .should().beDeclaredInClassesThat().resideInAPackage(basePackage + ".infrastructure.messaging..")
            .because("@KafkaListener 는 infrastructure/messaging 에만");
    }

    /**
     * {@code jakarta.persistence.Entity} 로 표시된 JPA 엔티티는
     * {@code infrastructure/persistence/entity/} 에만 있으며 클래스명은 {@code JpaEntity} 로 끝난다.
     */
    public static ArchRule jpaEntitiesOnlyInInfrastructure(String basePackage) {
        return classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage(basePackage + ".infrastructure.persistence.entity..")
            .andShould().haveSimpleNameEndingWith("JpaEntity")
            .because("JPA @Entity 는 infrastructure/persistence/entity 에만 · JpaEntity suffix");
    }
}
