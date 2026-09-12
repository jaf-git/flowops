package com.flowops.process.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.process.domain.enums.InstanceState;
import com.flowops.process.domain.enums.StepCondition;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.ProcessTemplate.StepDraft;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TemplateId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("PROCESS-INSTANTIATE-01")
@Tag("PROCESS-VIEW-INSTANCE-01")
class ProcessInstanceTest {
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId IOANA = PersonId.of(UUID.randomUUID());
    private static final Instant NOON = Instant.parse("2026-08-12T09:00:00Z");

    private final TemplateWorkbench library = new TemplateWorkbench();

    private ProcessTemplate chain() {
        ProcessTemplate template = ProcessTemplate.authored(
                TemplateId.of(UUID.randomUUID()),
                "Integrare angajat nou",
                "Cum procedăm",
                List.of(
                        StepDraft.added(library.work("Pregătește echipamentul"), 4),
                        StepDraft.added(library.work("Prima zi"), 8),
                        StepDraft.added(library.work("Evaluare la o lună"), 2)),
                MARIA,
                NOON);
        StepId one = template.steps().get(0).id();
        StepId two = template.steps().get(1).id();
        StepId three = template.steps().get(2).id();
        return template.dependingOn(two, one).dependingOn(three, two);
    }

    private ProcessInstance cut(ProcessTemplate template) {
        return ProcessInstance.cutFrom(
                InstanceId.of(UUID.randomUUID()),
                template,
                library.resolved(),
                "Integrare — Andrei",
                IOANA,
                MARIA,
                NOON);
    }

    @Test
    void copiesEveryStepAndEveryEdge() {
        ProcessTemplate template = chain();

        ProcessInstance instance = cut(template);

        assertThat(instance.steps()).hasSize(3);
        assertThat(instance.dependencies()).hasSize(2);
        assertThat(instance.steps())
                .extracting(InstanceStep::title)
                .containsExactly("Pregătește echipamentul", "Prima zi", "Evaluare la o lună");
    }

    @Test
    void remembersTheTemplateItWasCutFromAndWhoStartedIt() {
        ProcessTemplate template = chain();

        ProcessInstance instance = cut(template);

        assertThat(instance.template()).isEqualTo(template.id());
        assertThat(instance.owner()).isEqualTo(IOANA);
        assertThat(instance.startedBy()).isEqualTo(MARIA);
        assertThat(instance.state()).isEqualTo(InstanceState.RUNNING);
    }

    @Test
    void eachStepRemembersTheDefinitionItWasCutFrom() {
        ProcessTemplate template = chain();

        ProcessInstance instance = cut(template);

        assertThat(instance.steps())
                .extracting(InstanceStep::definitionId)
                .containsExactlyElementsOf(
                        template.steps().stream().map(s -> s.id()).toList());
    }

    @Test
    void entryStepsAreReachableAndEverythingElseIsPending() {
        ProcessInstance instance = cut(chain());

        assertThat(instance.steps().get(0).condition()).isEqualTo(StepCondition.REACHABLE);
        assertThat(instance.steps().get(1).condition()).isEqualTo(StepCondition.PENDING);
        assertThat(instance.steps().get(2).condition()).isEqualTo(StepCondition.PENDING);
    }

    @Test
    void withNoEdgesAtAllEveryStepIsReachableAtOnce() {
        ProcessTemplate parallel = ProcessTemplate.authored(
                TemplateId.of(UUID.randomUUID()),
                "Închidere lunară",
                null,
                List.of(StepDraft.added(library.work("Colectează"), 1), StepDraft.added(library.work("Verifică"), 1)),
                MARIA,
                NOON);

        ProcessInstance instance = cut(parallel);

        assertThat(instance.steps()).allMatch(step -> step.condition() == StepCondition.REACHABLE);
        assertThat(instance.awaitingAssignment()).hasSize(2);
    }

    @Test
    void surfacesTheReachableStepsNobodyHasAssigned() {
        ProcessInstance instance = cut(chain());

        assertThat(instance.awaitingAssignment())
                .containsExactly(instance.steps().get(0).id());
    }

    @Test
    void reportsProgressAsClosedAgainstTotal() {
        ProcessInstance instance = cut(chain());

        assertThat(instance.progress()).isEqualTo(new ProcessInstance.Progress(0, 3));
        assertThat(instance.isComplete()).isFalse();
    }

    @Test
    void namesTheStepThatHasWaitedLongestAndForHowLong() {
        ProcessInstance instance = cut(chain());

        ProcessInstance.Bottleneck bottleneck =
                instance.bottleneck(NOON.plus(Duration.ofHours(30))).orElseThrow();

        assertThat(bottleneck.step()).isEqualTo(instance.steps().get(0).id());
        assertThat(bottleneck.waitedFor()).isEqualTo(Duration.ofHours(30));
    }

    @Test
    void anInstanceWhoseEveryStepIsClosedIsComplete() {
        ProcessInstance instance = cut(chain());
        List<InstanceStep> allClosed = instance.steps().stream()
                .map(step -> new InstanceStep(
                        step.id(),
                        step.definitionId(),
                        step.taskTemplateId(),
                        step.title(),
                        step.description(),
                        step.expectedDurationHours(),
                        step.position(),
                        step.applicability(),
                        StepCondition.CLOSED,
                        com.flowops.process.domain.model.TaskRef.of(UUID.randomUUID()),
                        PersonId.of(UUID.randomUUID()),
                        NOON,
                        NOON.plus(Duration.ofHours(2)),
                        null))
                .toList();

        ProcessInstance finished = ProcessInstance.existing(
                instance.id(),
                instance.name(),
                instance.template(),
                instance.owner(),
                instance.startedBy(),
                InstanceState.COMPLETE,
                NOON,
                NOON.plus(Duration.ofHours(6)),
                allClosed,
                instance.dependencies(),
                null,
                null);

        assertThat(finished.isComplete()).isTrue();
        assertThat(finished.progress()).isEqualTo(new ProcessInstance.Progress(3, 3));
        assertThat(finished.totalDuration()).contains(Duration.ofHours(6));
        assertThat(finished.awaitingAssignment()).isEmpty();
        assertThat(finished.bottleneck(NOON.plus(Duration.ofHours(30)))).isEmpty();
    }
}
