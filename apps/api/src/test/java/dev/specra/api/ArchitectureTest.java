package dev.specra.api;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * The layering rules, as a test rather than as a paragraph nobody reads.
 *
 * <p>The shape is deliberately plain — {@code web → service → domain}, one folder per layer inside
 * one folder per feature. These rules are about keeping the arrows pointing one way; there are no
 * ports, no adapters and no hexagon to learn:
 *
 * <ul>
 *   <li>{@code core} is shared plumbing and must stay ignorant of every feature
 *   <li>features may use each other, but never in a circle
 *   <li>a class sits in the folder its role names, and only the layer above may reach it
 *   <li>HTTP stops at the controller; the service layer never sees a request
 *   <li>no vendor is named in code — the provider is {@code spring.ai.model.*}, at runtime
 * </ul>
 *
 * <p>A failure here reads as "this dependency points the wrong way", and the fix is normally to
 * move the class rather than to relax the rule.
 */
@AnalyzeClasses(packages = "dev.specra.api", importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String CORE = "dev.specra.api.core..";
  private static final String FEATURE = "dev.specra.api.feature..";

  private static final String WEB = "dev.specra.api.feature.*.web..";
  private static final String SERVICE = "dev.specra.api.feature.*.service..";
  private static final String MAPPER = "dev.specra.api.feature.*.mapper..";
  private static final String TOOL = "dev.specra.api.feature.*.tool..";
  private static final String DOMAIN = "dev.specra.api.feature.*.domain..";

  @ArchTest
  static final ArchRule coreKnowsNoFeature =
      noClasses()
          .that()
          .resideInAPackage(CORE)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(FEATURE)
          .because(
              "core is the shared layer: a class that a second feature would need belongs in core,"
                  + " and a class that names a feature belongs in that feature");

  @ArchTest
  static final ArchRule featuresAreFreeOfCycles =
      SlicesRuleDefinition.slices()
          .matching("dev.specra.api.feature.(*)..")
          .should()
          .beFreeOfCycles()
          .because("note may use ai; the day ai also uses note, neither can be changed alone");

  /**
   * The folders are the layering, so a class in the wrong folder would quietly escape every rule
   * below it. Mappers and AI tools count as service-layer collaborators: they are wired into a
   * service, never into a controller.
   */
  @ArchTest
  static final ArchRule theLayersAreRespected =
      Architectures.layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Web")
          .definedBy(WEB)
          .layer("Service")
          .definedBy(SERVICE, MAPPER, TOOL)
          .layer("Domain")
          .definedBy(DOMAIN)
          .whereLayer("Service")
          .mayOnlyBeAccessedByLayers("Web")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Service")
          .because(
              "an entity or a repository reaching the web layer is how a JPA proxy ends up being"
                  + " serialised, and how a query ends up inside a request handler");

  @ArchTest
  static final ArchRule aControllerLivesInWeb =
      classes()
          .that()
          .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
          .should()
          .resideInAPackage(WEB)
          .because("if a controller can sit anywhere, the layer rules above mean nothing");

  @ArchTest
  static final ArchRule anEntityOrRepositoryLivesInDomain =
      classes()
          .that()
          .areAnnotatedWith(jakarta.persistence.Entity.class)
          .or()
          .areAssignableTo(org.springframework.data.repository.Repository.class)
          .should()
          .resideInAPackage(DOMAIN)
          .because("storage is one folder, so it is obvious what a feature actually persists");

  /**
   * The predicate is spelled out rather than using {@code dependOnClassesThat().haveSimpleName…}
   * because Spring's own {@code @RestController} annotation ends in "Controller" too, and every
   * controller would report itself.
   */
  @ArchTest
  static final ArchRule nothingDependsOnAController =
      noClasses()
          .that()
          .haveSimpleNameNotEndingWith("Controller")
          .should()
          .dependOnClassesThat(
              simpleNameEndingWith("Controller").and(resideInAPackage("dev.specra.api..")))
          .because("a controller is an entry point; work it needs belongs in a service");

  @ArchTest
  static final ArchRule httpStopsAtTheController =
      noClasses()
          .that()
          .resideInAnyPackage(SERVICE, MAPPER, DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("jakarta.servlet..", "org.springframework.web..")
          .because(
              "a service is called by a controller, a test and a scheduler alike, so it must not"
                  + " need a request to exist");

  @ArchTest
  static final ArchRule noVendorIsNamedInCode =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.ai.anthropic..",
              "org.springframework.ai.openai..",
              "org.springframework.ai.ollama..",
              "org.springframework.ai.transformers..")
          .because(
              "the chat and embedding provider is chosen by spring.ai.model.*, so importing one"
                  + " turns a configuration change back into a code change");

  @ArchTest
  static final ArchRule dependenciesArriveThroughTheConstructor =
      fields()
          .should()
          .notBeAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
          .because(
              "constructor injection makes a class buildable with new in a unit test, and makes a"
                  + " missing collaborator a compile error rather than a null at runtime");
}
