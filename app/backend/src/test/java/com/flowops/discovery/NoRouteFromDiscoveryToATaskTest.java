package com.flowops.discovery;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("DISCOVERY-MARK-MESSAGE-01")
@Tag("DISCOVERY-FORMALISE-WORK-01")
@Tag("DISCOVERY-COMPOSE-PROCESS-01")
class NoRouteFromDiscoveryToATaskTest {
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.flowops");

    @Test
    @DisplayName("nothing in Discovery can reach TASK at all — a node never becomes a task")
    void discoveryNeverReachesTask() {
        noClasses()
                .that()
                .resideInAPackage("com.flowops.discovery..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.flowops.task..")
                .because(
                        """
                        DECISION-DISCOVERY-ZONE-01: a node never becomes a task. It becomes a template a \
                        human saved, and the application stamps tasks from that. Reaching TASK from here \
                        would make Discovery an alternative way to create work, which is a second door \
                        onto the lifecycle TASK owns — and it would let observation inherit the shape of \
                        what is already defined.""")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("only Discovery's adapters name the application, and only its published surfaces")
    void discoveryReachesTheApplicationOnlyThroughAnAdapterAndOnlyThroughAPublishedUseCase() {
        noClasses()
                .that()
                .resideInAPackage("com.flowops.discovery..")
                .and()
                .resideOutsideOfPackage("com.flowops.discovery.infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.flowops.process..", "com.flowops.tasklib..")
                .because(
                        """
                        The wall exists to stop the two models being conflated, not to stop a template \
                        being created. Discovery's domain and application rings never learn what a run, \
                        a task or a library is: they hold TemplateCreationPort and \
                        ProcessCompositionPort, written in this zone's own words, and an adapter — the \
                        one ring whose whole job is speaking somebody else's language — answers them. A \
                        service that could name ProcessInstantiationUseCase is a service somebody will \
                        eventually have start a run from an observation, and a run is the case \
                        identifier this zone deliberately does not have.""")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("and even an adapter stops at the published door — never the domain, never the internals")
    void evenAnAdapterReachesOnlyWhatTheOtherFeaturePublished() {
        noClasses()
                .that()
                .resideInAPackage("com.flowops.discovery..")
                .should()
                .dependOnClassesThat(resideInAnyPackage("com.flowops.process..", "com.flowops.tasklib..")
                        .and(resideOutsideOfPackages(
                                "com.flowops.process.application.published..",
                                "com.flowops.tasklib.application.published..")))
                .because(
                        """
                        A published use case speaks in identifiers and plain values, so calling one \
                        imports a question rather than a model. Everything else those features hold — \
                        their domain types, their internal commands, their ports — is the shape of what \
                        is already defined, and Discovery inheriting that shape is the template-capture \
                        failure this zone exists to avoid: a pipeline that can only re-confirm what \
                        somebody already decided, blind to precisely the drift it was built to find.""")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("the crossing is one-way — nothing in the application depends on Discovery")
    void nothingInTheApplicationDependsOnDiscovery() {
        noClasses()
                .that()
                .resideInAnyPackage("com.flowops.task..", "com.flowops.process..", "com.flowops.tasklib..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.flowops.discovery..")
                .because(
                        """
                        The one thing that crosses does so in one direction. If the application came to \
                        depend on Discovery, an unverified observation would be load-bearing for work \
                        somebody is accountable for — and the zone whose whole licence is that it is \
                        cheap to be wrong about would stop being cheap to be wrong about.""")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }
}
