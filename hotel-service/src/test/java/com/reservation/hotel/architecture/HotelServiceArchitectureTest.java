package com.reservation.hotel.architecture;

import com.reservation.common.architecture.ArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.reservation.hotel",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class HotelServiceArchitectureTest {

    private static final String BASE = "com.reservation.hotel";

    @ArchTest
    static final ArchRule noCrossServiceImports = ArchitectureRules.noCrossServiceImports("hotel");

    @ArchTest
    static final ArchRule layerDependencies = ArchitectureRules.layerDependencies(BASE);

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure = ArchitectureRules.applicationDoesNotDependOnInfrastructure(BASE);

    @ArchTest
    static final ArchRule presentationDoesNotDependOnInfrastructure = ArchitectureRules.presentationDoesNotDependOnInfrastructure(BASE);

    @ArchTest
    static final ArchRule domainHasNoFrameworkDependency = ArchitectureRules.domainHasNoFrameworkDependency(BASE);

    @ArchTest
    static final ArchRule domainMustNotReferenceContracts = ArchitectureRules.domainMustNotReferenceContracts(BASE);

    @ArchTest
    static final ArchRule domainRepositoriesAreInterfaces = ArchitectureRules.domainRepositoriesAreInterfaces(BASE);

    @ArchTest
    static final ArchRule repositoryImplsOnlyInInfrastructure = ArchitectureRules.repositoryImplsOnlyInInfrastructure(BASE);

    @ArchTest
    static final ArchRule domainExceptionsEndWithExceptionSuffix = ArchitectureRules.domainExceptionsEndWithExceptionSuffix(BASE);

    @ArchTest
    static final ArchRule grpcServiceImplsOnlyInServerPackage = ArchitectureRules.grpcServiceImplsOnlyInServerPackage(BASE);

    @ArchTest
    static final ArchRule kafkaListenerOnlyInMessaging = ArchitectureRules.kafkaListenerOnlyInMessaging(BASE);

    @ArchTest
    static final ArchRule jpaEntitiesOnlyInInfrastructure = ArchitectureRules.jpaEntitiesOnlyInInfrastructure(BASE);
}
