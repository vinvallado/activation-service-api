package com.deroyque.activationserviceapi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.deroyque.activationserviceapi", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {
    @ArchTest static final ArchRule controllersDoNotUsePersistence = noClasses().that().resideInAPackage("..controller..").should().dependOnClassesThat().resideInAnyPackage("..repository..", "..entity..", "..client..");
    @ArchTest static final ArchRule domainIsInfrastructureFree = noClasses().that().resideInAPackage("..model..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..");
    @ArchTest static final ArchRule dtoIsPersistenceFree = noClasses().that().resideInAPackage("..dto..").should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.data..");
}
