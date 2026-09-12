package com.flowops.automation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("AUTOMATION-ESCALATE-01")
@Tag("AUTOMATION-DETECT-STALLED-STEP-01")
@Tag("AUTOMATION-DETECT-LONG-BLOCK-01")
@Tag("AUTOMATION-DETECT-STALE-REVIEW-01")
class NothingInAutomationCanChangeWorkTest {
    private static final JavaClasses AUTOMATION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.flowops.automation");

    @Test
    void noPortInAutomationCanExpressAChangeToAnythingButTheLaddersOwnMemory() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("com.flowops.automation.application.shared.port..")
                .and()
                .areDeclaredInClassesThat()
                .areInterfaces()
                .and()
                .areDeclaredInClassesThat()
                .doNotHaveSimpleName("EscalationStatePort")
                .should()
                .haveNameMatching("(set|update|write|save|delete|create|assign|close|reopen).*")
                .because("AUTOMATION_03 section 7: the engine reads work and cannot change it, and the "
                        + "absence of the port is the guarantee rather than a rule somebody follows")
                .check(AUTOMATION);
    }

    @Test
    void noAdapterInAutomationHoldsAWriteAgainstAnythingButEscalationState() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("com.flowops.automation.infrastructure..")
                .and()
                .areDeclaredInClassesThat()
                .doNotHaveSimpleName("EscalationStateAdapter")
                .should()
                .haveNameMatching("(set|update|write|save|delete|create|assign|close|reopen).*")
                .because("the read adapters read; the one adapter that writes writes one table")
                .check(AUTOMATION);
    }
}
