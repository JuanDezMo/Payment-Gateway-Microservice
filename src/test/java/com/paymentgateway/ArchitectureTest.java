package com.paymentgateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

@AnalyzeClasses(packages = "com.paymentgateway", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.paymentgateway.";

    @ArchTest
    static final ArchRule layeredArchitecture = Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Controller").definedBy(BASE + "controller..")
            .layer("Service").definedBy(BASE + "service..")
            .layer("Repository").definedBy(BASE + "repository..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllerDoesNotUseRepositoryOrModel = noClasses().that().resideInAPackage(BASE + "controller..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + "repository..", BASE + "model..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule serviceDoesNotKnowHttp = noClasses().that().resideInAPackage(BASE + "service..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + "controller..", "org.springframework.http..",
                    "org.springframework.web..", "jakarta.servlet..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule modelIsIndependent = noClasses().that().resideInAPackage(BASE + "model..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + "service..", BASE + "controller..",
                    BASE + "repository..", BASE + "dto..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule dtoDoesNotUseModelOrRepository = noClasses().that().resideInAPackage(BASE + "dto..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + "model..", BASE + "repository..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noPackageCycles = slices().matching(BASE + "(*)..").should().beFreeOfCycles()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noFieldInjection = fields().should().notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule namingConventions = classes().that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().haveSimpleNameEndingWith("Controller")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule serviceNaming = classes().that().resideInAPackage(BASE + "service..")
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Service").orShould().haveSimpleName("CardRules")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule repositoryNaming = classes().that().resideInAPackage(BASE + "repository..")
            .should().haveSimpleNameEndingWith("Repository")
            .allowEmptyShould(true);
}
