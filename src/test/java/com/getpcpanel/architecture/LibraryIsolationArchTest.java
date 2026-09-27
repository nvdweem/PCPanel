package com.getpcpanel.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * {@code dev.niels.*} holds the clients for external apps (Wave Link, Discord, Sonar) as standalone
 * libraries that another project could reuse; everything about how PCPanel uses them lives in
 * {@code com.getpcpanel.integration.*}. So a library class may depend on neither PCPanel nor the
 * application framework — the app constructs and wires the clients itself.
 */
@AnalyzeClasses(packages = "dev.niels", importOptions = ImportOption.DoNotIncludeTests.class)
class LibraryIsolationArchTest {
    @ArchTest
    static final ArchRule librariesDoNotDependOnTheApp = noClasses()
            .that().resideInAPackage("dev.niels..")
            .should().dependOnClassesThat().resideInAPackage("com.getpcpanel..")
            .because("dev.niels.* are reusable libraries; PCPanel-specific code belongs in com.getpcpanel.integration.*");

    @ArchTest
    static final ArchRule librariesDoNotDependOnTheFramework = noClasses()
            .that().resideInAPackage("dev.niels..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.enterprise..", "jakarta.inject..", "io.quarkus..")
            .because("dev.niels.* are plain Java; the app creates and injects them itself");
}
