package com.io.shortly;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LayeredArchitectureTest {

    private JavaClasses importedClasses;

    @BeforeEach
    void setUp() {
        importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.io.shortly");
    }

    @Test
    @DisplayName("Domain 계층은 다른 계층에 의존하지 않는다")
    void domainLayerShouldNotDependOnOtherLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..api..",
                "..application..",
                "..infrastructure..",
                "..interfaces.."
            );

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Application 계층은 Domain 계층에만 의존한다")
    void applicationLayerShouldOnlyDependOnDomain() {
        ArchRule rule = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..application..",
                "..domain..",
                "java..",
                "jakarta..",
                "org.springframework..",
                "reactor..",
                "lombok..",
                "org.slf4j.."
            );

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("API 계층은 API/Application 계층에만 의존한다")
    void apiLayerShouldOnlyDependOnApplicationLayer() {
        ArchRule rule = classes()
            .that().resideInAPackage("..api..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..api..",
                "..application..",
                "java..",
                "jakarta..",
                "org.springframework..",
                "reactor..",
                "lombok..",
                "org.slf4j.."
            );

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Infrastructure 계층은 Domain 계층에만 의존한다")
    void infrastructureLayerShouldOnlyDependOnDomain() {
        ArchRule rule = classes()
            .that().resideInAPackage("..infrastructure..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..infrastructure..",
                "..domain..",
                "java..",
                "jakarta..",
                "org.springframework..",
                "reactor..",
                "lombok..",
                "com.fasterxml.jackson..",
                "org.slf4j..",
                "com.github.benmanes.caffeine..",
                "org.hibernate.."
            );

        rule.check(importedClasses);
    }
}
