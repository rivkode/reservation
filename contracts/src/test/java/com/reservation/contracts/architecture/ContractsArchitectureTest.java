package com.reservation.contracts.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * contracts 모듈의 공개 계약(proto stub + 이벤트 record) 이 프레임워크 중립성과
 * 역방향 의존 금지 규칙을 지키는지 검증한다.
 *
 * <p>proto 로 생성된 클래스(com.reservation.contracts.*.proto 등) 는 gRPC runtime 에
 * 의존하지만 그 의존성은 contracts 모듈의 api() 로 이미 명시되어 있으므로 본 테스트의
 * 경계는 주로 event/ 하위에 집중한다.
 */
@AnalyzeClasses(
    packages = "com.reservation.contracts",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ContractsArchitectureTest {

    /**
     * Kafka 이벤트 record 는 어떤 프레임워크에도 의존하지 않는 프로세스간 평문 계약이어야 한다.
     * Jackson 직렬화는 소비자 측(common-infrastructure 또는 각 서비스) 에서 수행한다.
     */
    @ArchTest
    static final ArchRule eventPackagesHaveNoFrameworkDependency =
        noClasses()
            .that().resideInAPackage("com.reservation.contracts.event..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "com.fasterxml.jackson..",
                "io.grpc..",
                "org.apache.kafka..")
            .because("contracts.event 는 프레임워크 중립 record 만 보관한다");

    /**
     * contracts 는 서비스 내부 패키지를 몰라야 한다 (역방향 의존 금지).
     */
    @ArchTest
    static final ArchRule noServiceInternalDependency =
        noClasses()
            .that().resideInAPackage("com.reservation.contracts..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.reservation.hotel..",
                "com.reservation.rate..",
                "com.reservation.guest..",
                "com.reservation.reservation..")
            .because("contracts 는 서비스 내부 클래스에 의존하지 않는다");

    /**
     * contracts.event.** 의 클래스(interface 제외) 는 모두 record 여야 한다.
     * {@code DomainEvent} 같은 interface 는 제외 (sealed 대신 open interface 로 유지 — ADR 0001).
     */
    @ArchTest
    static final ArchRule eventConcreteClassesAreRecords =
        classes()
            .that().resideInAPackage("com.reservation.contracts.event..")
            .and().areNotInterfaces()
            .and().areNotAnnotations()
            .should().beRecords()
            .because("contracts.event 의 구체 클래스는 불변 record 여야 함 (ADR 0001)");
}
