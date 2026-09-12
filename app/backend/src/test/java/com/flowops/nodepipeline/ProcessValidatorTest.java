package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.compose.DraftProcess;
import com.flowops.nodepipeline.domain.compose.ProcessValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessValidatorTest {
    @Test
    void aProcessCannotBeApprovedOnStepsThatAreThemselvesDrafts() {
        DraftProcess composed = composed(
                step("S1", "TT-D01", "DRAFT", 0, 1),
                step("S2", "T-CAPS", "APPROVED", 0, 2),
                step("S3", "TT-D02", "DRAFT", 0, 3));

        ProcessValidator.Result asDraft = ProcessValidator.validate(composed, "DRAFT");
        assertThat(asDraft.isAllowed())
                .as("as a draft it is perfectly legal — that is what composition produces")
                .isTrue();

        ProcessValidator.Result asApproved = ProcessValidator.validate(composed, "APPROVED");
        assertThat(asApproved.isAllowed()).isFalse();
        assertThat(asApproved.errors())
                .as("and it names each blocking step rather than failing on the first")
                .hasSize(2)
                .allSatisfy(e -> assertThat(e).contains("cannot be approved on unapproved steps"));
    }

    @Test
    void approvingTheTasksFirstMakesTheProcessApprovable() {
        DraftProcess ready = composed(
                step("S1", "TT-D01", "APPROVED", 0, 1),
                step("S2", "T-CAPS", "APPROVED", 0, 2),
                step("S3", "TT-D02", "APPROVED", 0, 3));

        assertThat(ProcessValidator.validate(ready, "APPROVED").isAllowed()).isTrue();
    }

    @Test
    void observedEdgesNeitherCycleNorStrandHoweverWrongTheyAre() {
        DraftProcess circular = new DraftProcess(
                "P1",
                "Launch",
                "DRAFT",
                "COMPOSED_FROM_DISCOVERY",
                List.of(step("S1", "T-A", "APPROVED", 0, 1), step("S2", "T-B", "APPROVED", 0, 2)),
                List.of(
                        new DraftProcess.DraftEdge("S1", "S2", "OBSERVED", 0.8),
                        new DraftProcess.DraftEdge("S2", "S1", "OBSERVED", 0.7)),
                evidence());

        ProcessValidator.Result result = ProcessValidator.validate(circular, "APPROVED");

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings())
                .as("but the consequence is said out loud: everything will be reachable at once")
                .anySatisfy(w -> assertThat(w).contains("all order is observed"));
    }

    @Test
    void promotingBothEdgesToConfirmedRevealsTheCycle() {
        DraftProcess circular = new DraftProcess(
                "P1",
                "Launch",
                "DRAFT",
                "COMPOSED_FROM_DISCOVERY",
                List.of(step("S1", "T-A", "APPROVED", 0, 1), step("S2", "T-B", "APPROVED", 0, 2)),
                List.of(
                        new DraftProcess.DraftEdge("S1", "S2", "CONFIRMED", null),
                        new DraftProcess.DraftEdge("S2", "S1", "CONFIRMED", null)),
                evidence());

        ProcessValidator.Result result = ProcessValidator.validate(circular, "APPROVED");

        assertThat(result.errors()).contains("the confirmed dependencies form a cycle");
        assertThat(result.errors()).contains("every step waits for another — nothing can start");
    }

    @Test
    void aComposedProcessMustCarryItsEvidence() {
        DraftProcess unevidenced = new DraftProcess(
                "P1",
                "Launch",
                "DRAFT",
                "COMPOSED_FROM_DISCOVERY",
                List.of(step("S1", "T-A", "APPROVED", 0, 1)),
                List.of(),
                null);

        assertThat(ProcessValidator.validate(unevidenced, "DRAFT").errors())
                .contains("a composed process must carry the jobs it was composed from");

        DraftProcess authored = new DraftProcess(
                "P2", "Launch", "DRAFT", "AUTHORED", List.of(step("S1", "T-A", "APPROVED", 0, 1)), List.of(), null);

        assertThat(ProcessValidator.validate(authored, "DRAFT").isAllowed())
                .as("a person writing one by hand owes no evidence — they are the evidence")
                .isTrue();
    }

    @Test
    void anEdgeStaysInsideTheProcessAndNeverLoopsToItself() {
        DraftProcess broken = new DraftProcess(
                "P1",
                "Launch",
                "DRAFT",
                "AUTHORED",
                List.of(step("S1", "T-A", "APPROVED", 0, 1)),
                List.of(
                        new DraftProcess.DraftEdge("S1", "S1", "CONFIRMED", null),
                        new DraftProcess.DraftEdge("S1", "SOMEWHERE-ELSE", "CONFIRMED", null)),
                null);

        assertThat(ProcessValidator.validate(broken, "DRAFT").errors())
                .contains("a step cannot depend on itself", "a dependency points outside this process");
    }

    @Test
    void aRetiredTaskTemplateIsRefusedEvenForADraft() {
        DraftProcess withRetired = composed(step("S1", "T-OLD", "RETIRED", 0, 1), step("S2", "T-B", "APPROVED", 0, 2));

        assertThat(ProcessValidator.validate(withRetired, "DRAFT").errors())
                .anySatisfy(e -> assertThat(e).contains("retired task template"));
    }

    @Test
    void repeatingATemplateWithoutALabelIsAWarningRatherThanARefusal() {
        DraftProcess twoRounds = composed(
                step("S1", "T-EDIT", "APPROVED", 0, 1),
                step("S2", "T-EDIT", "APPROVED", 0, 2),
                step("S3", "T-B", "APPROVED", 0, 3));

        ProcessValidator.Result result = ProcessValidator.validate(twoRounds, "APPROVED");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("cannot tell the two occurrences apart"));
    }

    @Test
    void anEmptyProcessIsRefused() {
        DraftProcess empty = new DraftProcess("P1", "Nothing", "DRAFT", "AUTHORED", List.of(), List.of(), null);

        assertThat(ProcessValidator.validate(empty, "DRAFT").errors())
                .contains("a process with no steps is not a process");
    }

    private static DraftProcess composed(DraftProcess.DraftStep... steps) {
        return new DraftProcess(
                "P1", "Launch", "DRAFT", "COMPOSED_FROM_DISCOVERY", List.of(steps), List.of(), evidence());
    }

    private static DraftProcess.DraftStep step(
            String id, String templateId, String templateStatus, int lane, int position) {
        return new DraftProcess.DraftStep(id, null, "Step " + id, templateId, templateStatus, lane, position, false);
    }

    private static DraftProcess.Evidence evidence() {
        return new DraftProcess.Evidence(
                List.of("J1", "J3", "J5"), List.of("n1", "n2"), 0.75, 0.78, List.of("seen in 3 of 4 launches"));
    }
}
