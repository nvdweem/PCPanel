package com.getpcpanel.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * {@code dev.niels.*} (Wave Link, Discord) and {@code re.walk.*} (Sonar) hold the clients for external apps as
 * standalone libraries that another project could reuse; everything about how PCPanel uses them lives in
 * {@code com.getpcpanel.integration.*}. So a library class may depend on neither PCPanel nor the
 * application framework — the app constructs and wires the clients itself.
 */
@AnalyzeClasses(packages = { "dev.niels", "re.walk" }, importOptions = ImportOption.DoNotIncludeTests.class)
class LibraryIsolationArchTest {
    @ArchTest
    static final ArchRule librariesDoNotDependOnTheApp = noClasses()
            .that().resideInAnyPackage("dev.niels..", "re.walk..")
            .should().dependOnClassesThat().resideInAPackage("com.getpcpanel..")
            .because("dev.niels.* and re.walk.* are reusable libraries; PCPanel-specific code belongs in com.getpcpanel.integration.*");

    @ArchTest
    static final ArchRule librariesDoNotDependOnTheFramework = noClasses()
            .that().resideInAnyPackage("dev.niels..", "re.walk..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.enterprise..", "jakarta.inject..", "io.quarkus..")
            .because("dev.niels.* and re.walk.* are plain Java; the app creates and injects them itself");
}
