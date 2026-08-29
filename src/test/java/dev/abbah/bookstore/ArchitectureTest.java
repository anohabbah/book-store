package dev.abbah.bookstore;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import dev.abbah.bookstore.domain.book.BookPort;
import java.util.List;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enforces the hexagonal layering of design D1: dependencies flow inward only
 * ({@code infra/api/rest → domain ← infra/spi/db}), the domain stays free of persistence, web, and
 * serialization technology, and every type carries the {@code <Domain>}-suffix name its position
 * in the layout dictates.
 */
@AnalyzeClasses(packages = "dev.abbah.bookstore", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String DOMAIN = "..domain..";
  private static final String REST_ADAPTER = "..infra.api.rest..";
  private static final String DB_ADAPTER = "..infra.spi.db..";

  /** Adapters may reach the domain; the domain reaches neither, and adapters ignore each other. */
  @ArchTest
  static final ArchRule dependencies_flow_inward_only =
      layeredArchitecture()
          .consideringOnlyDependenciesInAnyPackage("dev.abbah.bookstore..")
          .layer("Domain").definedBy(DOMAIN)
          .layer("RestAdapter").definedBy(REST_ADAPTER)
          .layer("DbAdapter").definedBy(DB_ADAPTER)
          .whereLayer("RestAdapter").mayNotBeAccessedByAnyLayer()
          .whereLayer("DbAdapter").mayNotBeAccessedByAnyLayer()
          .whereLayer("Domain").mayOnlyBeAccessedByLayers("RestAdapter", "DbAdapter")
          .ensureAllClassesAreContainedInArchitectureIgnoring(
              JavaClass.Predicates.resideInAPackage("dev.abbah.bookstore"));

  /**
   * Spring's stereotype and transaction annotations are permitted in the domain (D5), as is Spring
   * Data's pagination vocabulary ({@code org.springframework.data.domain}: {@code Page},
   * {@code Pageable}, {@code Sort}) — those are plain value types the ports speak, not a
   * persistence technology. Persistence, web, and JSON technology stays out; that is what an
   * adapter is for.
   */
  @ArchTest
  static final ArchRule domain_is_free_of_infrastructure_technology =
      ArchRuleDefinition.noClasses()
          .that().resideInAPackage(DOMAIN)
          .should().dependOnClassesThat(
              JavaClass.Predicates.resideInAnyPackage(
                      "org.springframework.data..",
                      "org.springframework.web..",
                      "org.springframework.http..",
                      "jakarta.persistence..",
                      "jakarta.servlet..",
                      "com.fasterxml.jackson..",
                      "tools.jackson..")
                  .and(DescribedPredicate.not(
                      JavaClass.Predicates.resideInAPackage("org.springframework.data.domain.."))))
          .because("the domain owns the ports and shares Spring Data's pagination value types;"
              + " persistence, web and JSON belong to the adapters");

  @ArchTest
  static final ArchRule packages_are_free_of_cycles =
      slices().matching("dev.abbah.bookstore.(**)").should().beFreeOfCycles();

  /** The driven port may only be satisfied from the driven side, by a {@code <Domain>Adapter}. */
  @ArchTest
  static final ArchRule driven_ports_are_implemented_by_driven_adapters =
      ArchRuleDefinition.classes()
          .that().implement(BookPort.class)
          .should().resideInAPackage("..infra.spi..")
          .andShould().haveSimpleNameEndingWith("Adapter")
          .because("BookPort is a driven port, so only a driven adapter may implement it");

  /**
   * The suffixes openspec/config.yaml assigns to each hexagonal position. Types outside the
   * convention — the domain object itself, exceptions, request DTOs, framework repositories —
   * carry none of these and are therefore not constrained by
   * {@link #role_types_are_named_after_their_package}.
   */
  private static final List<String> ROLE_SUFFIXES = List.of(
      "Usecase", "Port", "EntityMapper", "DtoMapper", "Entity", "Dto", "Adapter", "Resource",
      "Producer", "Consumer", "EventMapper", "Event");

  private static final DescribedPredicate<JavaClass> CARRY_A_ROLE_SUFFIX =
      new DescribedPredicate<JavaClass>("carry a hexagonal role suffix") {
        @Override
        public boolean test(JavaClass javaClass) {
          return ROLE_SUFFIXES.stream().anyMatch(javaClass.getSimpleName()::endsWith);
        }
      };

  /**
   * The guard against the drift this layout was refactored out of: {@code <domain_name>} is the
   * package and {@code <Domain>} its UpperCamelCase form, so {@code domain/book} holds
   * {@code BookUsecase} and {@code BookPort} — never {@code CatalogService} or
   * {@code BookRepository} sitting in {@code domain/catalog}.
   */
  @ArchTest
  static final ArchRule role_types_are_named_after_their_package =
      ArchRuleDefinition.classes()
          .that(CARRY_A_ROLE_SUFFIX)
          .should(new ArchCondition<JavaClass>(
              "be prefixed with the UpperCamelCase form of their package") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
              String packageName = javaClass.getPackageName();
              String segment = packageName.substring(packageName.lastIndexOf('.') + 1);
              String expected = Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
              if (!javaClass.getSimpleName().startsWith(expected)) {
                events.add(SimpleConditionEvent.violated(javaClass,
                    javaClass.getSimpleName() + " in package " + segment + " should be named "
                        + expected + "<Role>"));
              }
            }
          });

  /** Business logic is a {@code <Domain>Usecase} in the domain, not a `*Service` anywhere. */
  @ArchTest
  static final ArchRule use_cases_are_named_usecase =
      ArchRuleDefinition.classes()
          .that().areAnnotatedWith(Service.class)
          .should().haveSimpleNameEndingWith("Usecase")
          .andShould().resideInAPackage(DOMAIN);

  /** Driven ports are the only interfaces the domain owns. */
  @ArchTest
  static final ArchRule domain_interfaces_are_ports =
      ArchRuleDefinition.classes()
          .that().resideInAPackage(DOMAIN)
          .and().areInterfaces()
          .and().doNotHaveSimpleName("package-info")
          .should().haveSimpleNameEndingWith("Port");

  @ArchTest
  static final ArchRule rest_controllers_are_named_resource =
      ArchRuleDefinition.classes()
          .that().areAnnotatedWith(RestController.class)
          .should().haveSimpleNameEndingWith("Resource")
          .andShould().resideInAPackage(REST_ADAPTER);

  @ArchTest
  static final ArchRule persistence_aggregates_are_named_entity =
      ArchRuleDefinition.classes()
          .that().areAnnotatedWith(Table.class)
          .should().haveSimpleNameEndingWith("Entity")
          .andShould().resideInAPackage(DB_ADAPTER);

  /** Mapping across a boundary is named for the type it maps to: entity ↔ domain, DTO ↔ domain. */
  @ArchTest
  static final ArchRule db_mappers_are_named_entity_mapper =
      ArchRuleDefinition.classes()
          .that().resideInAPackage(DB_ADAPTER)
          .and().haveSimpleNameEndingWith("Mapper")
          .should().haveSimpleNameEndingWith("EntityMapper");

  @ArchTest
  static final ArchRule rest_mappers_are_named_dto_mapper =
      ArchRuleDefinition.classes()
          .that().resideInAPackage(REST_ADAPTER)
          .and().haveSimpleNameEndingWith("Mapper")
          .should().haveSimpleNameEndingWith("DtoMapper");
}
