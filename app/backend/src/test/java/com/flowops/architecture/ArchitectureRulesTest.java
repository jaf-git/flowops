package com.flowops.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {
    private static final List<String> FEATURES = List.of(
            "auth",
            "workspace",
            "task",
            "process",
            "canvas",
            "chat",
            "aiexport",
            "aiinsight",
            "tasklib",
            "aiassist",
            "chatassist",
            "discovery",
            "notification",
            "automation",
            "nodepipeline",
            "analyser");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.flowops");

    @Test
    void everyArchitectureRuleHasSomethingToCheck() {
        assertThat(PRODUCTION_CLASSES)
                .as("the importer found no production classes at all, so every rule below is vacuous")
                .isNotEmpty();

        record Population(String pkg, String guards) {}
        List<Population> populations = List.of(
                new Population("com.flowops.analyser.domain", "an analyser is a pure function over a snapshot"),
                new Population("com.flowops.analyser.application", "the application ring reaches no adapter"),
                new Population("com.flowops.nodepipeline.domain", "the domain ring imports no framework"),
                new Population("com.flowops.discovery.domain", "no feature reaches into another's domain"),
                new Population("com.flowops.task.domain", "no aggregate groups by the person"),
                new Population("com.flowops.process.domain", "the domain ring depends on nothing further out"));

        for (Population population : populations) {
            assertThat(PRODUCTION_CLASSES.stream()
                            .anyMatch(clazz -> clazz.getPackageName().startsWith(population.pkg())))
                    .as(
                            "%s holds no classes, so the rule that %s can no longer fail",
                            population.pkg(), population.guards())
                    .isTrue();
        }
    }

    @Test
    void theQueryRuleCanStillFindTheFilesItReads() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("src/main/java/com/flowops/analyser");

        assertThat(java.nio.file.Files.exists(root))
                .as(
                        "%s is gone, so the rule that refuses a query grouping work by a person reads nothing",
                        root.toAbsolutePath())
                .isTrue();

        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(root)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java")).count())
                    .as("the analyser package holds no java files, so the query rule scans nothing")
                    .isGreaterThan(0);
        }
    }

    @Test
    void theDomainRingImportsNoFramework() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "com.fasterxml..")
                .because("the domain ring holds rules only; a framework import there is a defect")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void ananalyserIsAPureFunctionOverItsSnapshot() {
        noClasses()
                .that()
                .resideInAPackage("com.flowops.analyser.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "javax.sql..",
                        "java.sql..",
                        "jakarta.persistence..",
                        "..infrastructure..")
                .orShould()
                .dependOnClassesThat()
                .haveNameMatching(".*(Repository|Adapter|JdbcTemplate|DataSource|EntityManager)")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.time.Clock")
                .because("an analyser is a pure function over a snapshot: no I/O, no writes, no clock. "
                        + "Without this rule one that injected a repository would compile and pass")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void noAnalysisQueryGroupsWorkByAPerson() {
        List<String> offending = new java.util.ArrayList<>();
        java.nio.file.Path root = java.nio.file.Path.of("src/main/java/com/flowops/analyser");
        if (java.nio.file.Files.exists(root)) {
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                    String body;
                    try {
                        body = java.nio.file.Files.readString(path);
                    } catch (java.io.IOException unreadable) {
                        throw new IllegalStateException("could not read " + path, unreadable);
                    }
                    String sql = body.toLowerCase(java.util.Locale.ROOT);
                    for (String person : List.of("creator_id", "marker_id", "performer_id", "actor_user_id")) {
                        int at = sql.indexOf("group by");
                        while (at >= 0) {
                            String clause = sql.substring(at, Math.min(sql.length(), at + 200));
                            if (clause.contains(person)) {
                                offending.add(path.getFileName() + " groups by " + person);
                            }
                            at = sql.indexOf("group by", at + 1);
                        }
                    }
                });
            } catch (java.io.IOException unreadable) {
                throw new IllegalStateException("could not walk " + root, unreadable);
            }
        }

        org.assertj.core.api.Assertions.assertThat(offending)
                .as("no analysis query may aggregate work by a person -- that is how "
                        + "'coordination hands to content slowly' becomes 'Andrei is slow'")
                .isEmpty();
    }

    @Test
    void theDomainRingDependsOnNothingFurtherOut() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..application..", "..api..", "..infrastructure..")
                .because("dependencies point inward only")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void theApplicationRingDependsOnNoAdapter() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..api..", "..infrastructure..")
                .because("the application ring reaches the outside through ports, not adapters")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void persistenceEntitiesStayInTheInfrastructureRing() {
        classes()
                .that()
                .areAnnotatedWith("jakarta.persistence.Entity")
                .should()
                .resideInAPackage("..infrastructure.persistence.entity..")
                .because("a JPA entity is a persistence shape, never a domain type")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void controllersStayInTheApiRing() {
        classes()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should()
                .resideInAPackage("..api..")
                .because("the inbound adapter is the api ring")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void featuresAreFreeOfCycles() {
        SlicesRuleDefinition.slices()
                .matching("com.flowops.(*)..")
                .should()
                .beFreeOfCycles()
                .because("features collaborate through ports, never by reaching into each other")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void noFeatureReachesIntoAnotherFeaturesDomain() {
        for (String feature : FEATURES) {
            noClasses()
                    .that()
                    .resideInAPackage("com.flowops." + feature + "..")
                    .and()
                    .resideOutsideOfPackage("..seed..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(otherFeatureDomains(feature))
                    .because(feature + " may call another feature's use cases, but never reach into its domain")
                    .allowEmptyShould(true)
                    .check(PRODUCTION_CLASSES);
        }
    }

    @Test
    void onlySeedersMayNameAnotherFeaturesDomain() {
        noClasses()
                .that()
                .resideInAPackage("..seed..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..infrastructure.persistence..")
                .because("a seeder stands in for a person, and a person cannot write a row: driving the "
                        + "application services is what makes the seed a proof that the rules work rather "
                        + "than a fixture wearing the shape they would have produced")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void noModelAuthoredValueReachesAnApiResponse() {
        noClasses()
                .that()
                .resideInAPackage("..api..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.flowops.aiassist.domain.ShapeOpinion")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.flowops.chatassist.domain.WorkOpinion")
                .because("a model's own words must not reach a response; DECISION-AI-OUTPUT-INVARIANT-01's"
                        + " filter is unbuilt, and this absence is what holds the invariant instead")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void noAggregateGroupsByThePersonRatherThanTheWork() throws Exception {
        java.nio.file.Path sources = java.nio.file.Path.of("src/main/java");
        java.util.regex.Pattern offending = java.util.regex.Pattern.compile(
                "group\\s+by[^;]{0,200}?\\b(creator_id|marker_id)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);

        for (String offender : List.of(
                "select count(*) from work_node group by creator_id",
                "GROUP BY n.creator_id",
                "select x from t\n            group by\n              marker_id, y",
                "group by job_id, creator_id")) {
            org.assertj.core.api.Assertions.assertThat(
                            offending.matcher(offender).find())
                    .as("the guard's own probe must catch: %s", offender)
                    .isTrue();
        }
        for (String legitimate :
                List.of("group by work_type", "select creator_id from work_node", "group by b.work_type, b.job_id")) {
            org.assertj.core.api.Assertions.assertThat(
                            offending.matcher(legitimate).find())
                    .as("and must not catch legitimate SQL: %s", legitimate)
                    .isFalse();
        }

        List<String> offenders = new java.util.ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(sources)) {
            for (java.nio.file.Path file :
                    files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String body = java.nio.file.Files.readString(file);
                if (offending.matcher(body).find()) {
                    offenders.add(sources.relativize(file).toString());
                }
            }
        }

        org.assertj.core.api.Assertions.assertThat(offenders)
                .as("ADR-001: creator_id and marker_id are notification addresses, never measured units. "
                        + "Coverage groups by work role. A manager must see no number about a person that "
                        + "the person cannot see about themselves.")
                .isEmpty();
    }

    @Test
    void nothingReachesALanguageModelExceptThroughItsPort() {
        noClasses()
                .that()
                .resideOutsideOfPackages("..infrastructure.model..")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("LanguageModelAdapter")
                .orShould()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("WorkModelAdapter")
                .orShould()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("WorkJudgeAdapter")
                .because("the AI off-switch is the adapter not being built; reaching one directly bypasses"
                        + " it, and CONSTRAINT-AI-OPTIONAL-01 says the product works without a model")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    private static String[] otherFeatureDomains(String feature) {
        return FEATURES.stream()
                .filter(other -> !other.equals(feature))
                .map(other -> "com.flowops." + other + ".domain..")
                .toArray(String[]::new);
    }
}
