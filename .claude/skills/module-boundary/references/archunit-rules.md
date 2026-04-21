# ArchUnit 규칙 모음

서비스 경계와 계층 원칙을 **테스트로 강제**한다.

---

## 1. 계층 규칙 (각 서비스 공통)

```java
@AnalyzeClasses(
    packages = "com.example.reservation",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule domainHasNoFrameworkDependency =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "com.fasterxml.jackson..",
                "io.grpc..",
                "org.apache.kafka..")
            .because("도메인은 프레임워크에 의존하지 않는다");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule presentationDoesNotDependOnInfrastructure =
        noClasses().that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule controllersOnlyDependOnApplication =
        classes().that().resideInAPackage("..presentation.controller..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                "..presentation..",
                "..application..",
                "..domain.model..",    // ID, VO 사용 위해 최소 허용
                "java..",
                "org.springframework.web..",
                "jakarta.validation..");
}
```

---

## 2. 서비스 경계 규칙

```java
@AnalyzeClasses(packages = "com.example.reservation")
class ReservationServiceBoundaryTest {

    @ArchTest
    static final ArchRule doesNotImportOtherServices =
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "com.example.hotel..",
                "com.example.rate..",
                "com.example.guest..")
            .because("다른 서비스 내부 패키지 import 금지. contracts 만 사용.");
}
```

---

## 3. gRPC 위치 규칙

```java
@ArchTest
static final ArchRule grpcServiceImplsOnlyInServerPackage =
    classes().that().areAnnotatedWith(
            "net.devh.boot.grpc.server.service.GrpcService")
        .should().resideInAPackage("..infrastructure.grpc.server..")
        .because("@GrpcService는 infrastructure/grpc/server 에만 위치");

@ArchTest
static final ArchRule grpcClientInjectionOnlyInClientPackage =
    fields().that().areAnnotatedWith("net.devh.boot.grpc.client.inject.GrpcClient")
        .should().beDeclaredInClassesThat().resideInAPackage("..infrastructure.grpc.client..")
        .because("@GrpcClient 주입은 infrastructure/grpc/client 에만");

@ArchTest
static final ArchRule protoGeneratedClassesNotInDomain =
    noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("com.example.contracts.proto..")
        .because("Domain은 proto 생성 클래스를 모른다. 변환은 Infrastructure에서.");
```

---

## 4. Kafka 위치 규칙

```java
@ArchTest
static final ArchRule kafkaListenerOnlyInMessaging =
    methods().that().areAnnotatedWith("org.springframework.kafka.annotation.KafkaListener")
        .should().beDeclaredInClassesThat().resideInAPackage("..infrastructure.messaging..")
        .because("@KafkaListener 는 infrastructure/messaging 에만");

@ArchTest
static final ArchRule domainDoesNotDependOnKafka =
    noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.apache.kafka..",
            "org.springframework.kafka..");
```

---

## 5. JPA / Persistence 규칙

```java
@ArchTest
static final ArchRule jpaEntitiesOnlyInInfrastructure =
    classes().that().areAnnotatedWith(jakarta.persistence.Entity.class)
        .should().resideInAPackage("..infrastructure.persistence.entity..")
        .andShould().haveSimpleNameEndingWith("JpaEntity");

@ArchTest
static final ArchRule domainRepositoriesAreInterfaces =
    classes().that().resideInAPackage("..domain.repository..")
        .should().beInterfaces();

@ArchTest
static final ArchRule repositoryImplsOnlyInInfrastructure =
    classes().that().haveSimpleNameEndingWith("RepositoryImpl")
        .should().resideInAPackage("..infrastructure.persistence.repository..");
```

---

## 6. 네이밍 컨벤션

```java
@ArchTest
static final ArchRule commandsEndWithCommand =
    classes().that().resideInAPackage("..application.dto..")
        .and().haveSimpleNameContaining("Command")
        .should().haveSimpleNameEndingWith("Command");

@ArchTest
static final ArchRule controllersEndWithController =
    classes().that().resideInAPackage("..presentation.controller..")
        .should().haveSimpleNameEndingWith("Controller");

@ArchTest
static final ArchRule exceptionsEndWithException =
    classes().that().resideInAPackage("..domain.exception..")
        .should().haveSimpleNameEndingWith("Exception");
```

---

## 7. Contracts 모듈 규칙

```java
@AnalyzeClasses(packages = "com.example.contracts")
class ContractsArchitectureTest {

    @ArchTest
    static final ArchRule noSpringDependency =
        noClasses().that().resideInAPackage("com.example.contracts.event..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..")
            .because("contracts/event 는 프레임워크 중립이어야 함");

    @ArchTest
    static final ArchRule noServiceInternalDependency =
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "com.example.hotel..",
                "com.example.rate..",
                "com.example.guest..",
                "com.example.reservation..")
            .because("contracts 는 서비스 내부를 몰라야 함");

    @ArchTest
    static final ArchRule eventClassesAreRecords =
        classes().that().resideInAPackage("com.example.contracts.event..")
            .should().beRecords()
            .because("contracts/event 는 불변 record 만 허용");
}
```

---

## 실행

```bash
# 특정 서비스
./gradlew :reservation-service:test --tests '*ArchitectureTest'

# 전체
./gradlew test --tests '*ArchitectureTest'
```

**CI 에서 반드시 실행**. 아키텍처 위반은 즉시 감지되어야 한다.
