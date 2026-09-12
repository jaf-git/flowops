package com.flowops.process.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.process.domain.exception.CycleWouldFormException;
import com.flowops.process.domain.exception.TemplateNeedsAStepException;
import com.flowops.process.domain.model.Applicability;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.ProcessTemplate.StepDraft;
import com.flowops.process.domain.model.StepDefinition;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TemplateId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("PROCESS-EDIT-TEMPLATE-01")
class TemplateEditTest {
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-11T09:00:00Z");

    private final TemplateWorkbench library = new TemplateWorkbench();

    private ProcessTemplate threeSteps() {
        return ProcessTemplate.authored(
                TemplateId.of(UUID.randomUUID()),
                "Integrare angajat nou",
                "Cum procedăm",
                List.of(
                        StepDraft.added(library.work("Pregătește echipamentul"), 4),
                        StepDraft.added(library.work("Prima zi"), 8),
                        StepDraft.added(library.work("Evaluare la o lună"), 2)),
                MARIA,
                NOW);
    }

    private static StepDraft keep(StepDefinition step) {
        return new StepDraft(step.id(), step.taskTemplateId(), step.expectedDurationHours(), step.applicability());
    }

    @Test
    void changesTheOverviewAndLeavesTheStepsAlone() {
        ProcessTemplate template = threeSteps();

        ProcessTemplate edited = template.editedTo(
                "Varianta scurtă",
                template.steps().stream().map(TemplateEditTest::keep).toList());

        assertThat(edited.overview()).isEqualTo("Varianta scurtă");
        assertThat(edited.steps())
                .extracting(s -> library.titleOf(s.taskTemplateId()))
                .containsExactlyElementsOf(template.steps().stream()
                        .map(s -> library.titleOf(s.taskTemplateId()))
                        .toList());
    }

    @Test
    void repointsAStepAtDifferentWorkWithoutChangingItsIdentity() {
        ProcessTemplate template = threeSteps();
        StepId first = template.steps().getFirst().id();

        List<StepDraft> drafts = List.of(
                new StepDraft(first, library.work("Pregătește laptopul"), 4, Applicability.always()),
                keep(template.steps().get(1)),
                keep(template.steps().get(2)));

        ProcessTemplate edited = template.editedTo(template.overview(), drafts);

        assertThat(edited.steps().getFirst().id()).isEqualTo(first);
        assertThat(library.titleOf(edited.steps().getFirst().taskTemplateId())).isEqualTo("Pregătește laptopul");
    }

    @Test
    void addsAStepThatArrivesWithoutAnIdentifier() {
        ProcessTemplate template = threeSteps();

        List<StepDraft> drafts = new ArrayList<>(
                template.steps().stream().map(TemplateEditTest::keep).toList());
        drafts.add(StepDraft.added(library.work("Feedback la trei luni"), 1));

        ProcessTemplate edited = template.editedTo(template.overview(), drafts);

        assertThat(edited.steps()).hasSize(4);
        assertThat(library.titleOf(edited.steps().getLast().taskTemplateId())).isEqualTo("Feedback la trei luni");
        assertThat(edited.steps().getLast().id()).isNotNull();
    }

    @Test
    void removingAStepRemovesTheEdgesThatTouchedIt() {
        ProcessTemplate template = threeSteps();
        StepId first = template.steps().get(0).id();
        StepId second = template.steps().get(1).id();
        ProcessTemplate linked = template.dependingOn(second, first);

        List<StepDraft> withoutTheSecond =
                List.of(keep(linked.steps().get(0)), keep(linked.steps().get(2)));
        ProcessTemplate edited = linked.editedTo(linked.overview(), withoutTheSecond);

        assertThat(edited.steps()).hasSize(2);
        assertThat(edited.dependencies()).isEmpty();
        edited.graph().requireValid();
    }

    @Test
    void refusesToRemoveTheLastStep() {
        ProcessTemplate template = threeSteps();

        assertThatThrownBy(() -> template.editedTo(template.overview(), List.of()))
                .isInstanceOf(TemplateNeedsAStepException.class);
    }

    @Test
    void refusesAnEdgeThatWouldCloseACycle() {
        ProcessTemplate template = threeSteps();
        StepId a = template.steps().get(0).id();
        StepId b = template.steps().get(1).id();

        ProcessTemplate linked = template.dependingOn(b, a);

        assertThatThrownBy(() -> linked.dependingOn(a, b)).isInstanceOf(CycleWouldFormException.class);
    }

    @Test
    void addingAnEdgeThatIsAlreadyDrawnIsReportedAsAlreadyDrawn() {
        ProcessTemplate template = threeSteps();
        StepId a = template.steps().get(0).id();
        StepId b = template.steps().get(1).id();

        ProcessTemplate linked = template.dependingOn(b, a);

        assertThat(linked.alreadyDependsOn(b, a)).isTrue();
        assertThat(linked.dependingOn(b, a).dependencies()).hasSize(1);
    }

    @Test
    void removingAnEdgeLeavesTheTemplateValid() {
        ProcessTemplate template = threeSteps();
        StepId a = template.steps().get(0).id();
        StepId b = template.steps().get(1).id();

        ProcessTemplate unlinked = template.dependingOn(b, a).withoutDependency(b, a);

        assertThat(unlinked.dependencies()).isEmpty();
        unlinked.graph().requireValid();
    }
}
