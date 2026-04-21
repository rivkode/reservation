package com.reservation.common.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link ArchitectureRules} 유틸 자체에 대한 self-test.
 *
 * <p>각 서비스의 {@code XxxServiceArchitectureTest} 는 규칙의 **소비** 만 검증하므로
 * 유틸 내부의 패키지 계산 · 화이트리스트 로직 자체가 깨지면 소비자 테스트가
 * vacuously 통과할 위험이 있다. 본 클래스는 유틸이 던지는 ArchRule 의 description
 * 을 점검해 드리프트를 잡는다.
 */
class ArchitectureRulesTest {

    private static final String BASE = "com.reservation.example";

    @Test
    @DisplayName("noCrossServiceImports 는 contracts · common 을 화이트리스트에 포함하고 자기 서비스를 대상에서 제외한다")
    void noCrossServiceImportsWhitelistsContractsAndCommon() {
        String description = ArchitectureRules.noCrossServiceImports("hotel").getDescription();

        // 검사 대상: 본 서비스 클래스
        assertThat(description).contains("com.reservation.hotel..");
        // 화이트리스트: contracts · common · own service 는 허용
        assertThat(description).contains("com.reservation.contracts..");
        assertThat(description).contains("com.reservation.common..");
    }

    @Test
    @DisplayName("모든 ArchitectureRules 정적 메서드가 예외 없이 ArchRule 을 생성한다")
    void allRulesCompileWithoutException() {
        assertThatCode(() -> {
            ArchitectureRules.noCrossServiceImports("example");
            ArchitectureRules.layerDependencies(BASE);
            ArchitectureRules.applicationDoesNotDependOnInfrastructure(BASE);
            ArchitectureRules.presentationDoesNotDependOnInfrastructure(BASE);
            ArchitectureRules.domainHasNoFrameworkDependency(BASE);
            ArchitectureRules.domainMustNotReferenceContracts(BASE);
            ArchitectureRules.domainRepositoriesAreInterfaces(BASE);
            ArchitectureRules.repositoryImplsOnlyInInfrastructure(BASE);
            ArchitectureRules.domainExceptionsEndWithExceptionSuffix(BASE);
            ArchitectureRules.grpcServiceImplsOnlyInServerPackage(BASE);
            ArchitectureRules.kafkaListenerOnlyInMessaging(BASE);
            ArchitectureRules.jpaEntitiesOnlyInInfrastructure(BASE);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("layerDependencies 는 4개 레이어 이름(Domain/Application/Infrastructure/Presentation)을 모두 포함한다")
    void layerDependenciesDeclaresAllFourLayers() {
        String description = ArchitectureRules.layerDependencies(BASE).getDescription();

        assertThat(description)
            .contains("Domain")
            .contains("Application")
            .contains("Infrastructure")
            .contains("Presentation");
    }
}
