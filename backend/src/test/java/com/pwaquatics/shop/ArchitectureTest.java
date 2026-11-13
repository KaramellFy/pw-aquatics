package com.pwaquatics.shop;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Enforces the module and layer rules from AGENTS.md §7. */
class ArchitectureTest {

    private static final String ROOT = "com.pwaquatics.shop";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void modulesNeverReachIntoAnotherModulesInfrastructure() {
        Set<String> modules = CLASSES.stream()
                .map(javaClass -> javaClass.getPackageName())
                .filter(name -> name.startsWith(ROOT + "."))
                .map(name -> name.substring(ROOT.length() + 1).split("\\.")[0])
                .collect(Collectors.toSet());
        for (String module : modules) {
            noClasses()
                    .that().resideOutsideOfPackage(ROOT + "." + module + "..")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + "." + module + ".infrastructure..")
                    .because("modules talk through application services, not repositories")
                    .allowEmptyShould(true)
                    .check(CLASSES);
        }
    }

    @Test
    void controllersDoNotUseRepositoriesDirectly() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void domainDoesNotDependOnOuterLayers() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..", "..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void entitiesStayOutOfTheApiLayer() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .because("API responses use DTO records, never entities")
                .check(CLASSES);
    }

    /**
     * springdoc names OpenAPI schemas after the simple class name, so two request or response
     * records with the same name would silently share one schema (and one generated type).
     */
    @Test
    void apiRecordsHaveUniqueSimpleNames() {
        Map<String, List<String>> byName = CLASSES.stream()
                .filter(JavaClass::isRecord)
                .filter(javaClass -> javaClass.getPackageName().contains(".api")
                        || javaClass.getPackageName().endsWith(".view"))
                .collect(Collectors.groupingBy(JavaClass::getSimpleName,
                        Collectors.mapping(JavaClass::getName, Collectors.toList())));
        assertThat(byName.entrySet().stream().filter(entry -> entry.getValue().size() > 1).toList())
                .as("records in api and view packages that share a simple name")
                .isEmpty();
    }
}
