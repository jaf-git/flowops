package com.flowops.process.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.process.domain.exception.StepNeedsTaskTemplateException;
import com.flowops.process.domain.exception.TemplateNameRequiredException;
import com.flowops.process.domain.exception.TemplateNeedsAStepException;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.ProcessTemplate.StepDraft;
import com.flowops.process.domain.model.TemplateId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("PROCESS-AUTHOR-TEMPLATE-01")
class ProcessTemplateTest {
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-11T09:00:00Z");

    private final TemplateWorkbench library = new TemplateWorkbench();

    private static ProcessTemplate authored(String name, List<StepDraft> steps) {
        return ProcessTemplate.authored(TemplateId.of(UUID.randomUUID()), name, "How we do it", steps, MARIA, NOW);
    }

    private StepDraft step(String title) {
        return StepDraft.added(library.work(title), 4);
    }

    @Test
    void keepsTheNameTheOverviewAndEveryStepItWasGiven() {
        ProcessTemplate template =
                authored("Integrare angajat nou", List.of(step("Pregătește echipamentul"), step("Prima zi")));

        assertThat(template.name()).isEqualTo("Integrare angajat nou");
        assertThat(template.overview()).isEqualTo("How we do it");
        assertThat(template.author()).isEqualTo(MARIA);
        assertThat(template.active()).isTrue();
        assertThat(template.steps())
                .extracting(s -> library.titleOf(s.taskTemplateId()))
                .containsExactly("Pregătește echipamentul", "Prima zi");
    }

    @Test
    void numbersTheStepsInTheOrderTheyWereGiven() {
        ProcessTemplate template =
                authored("Închidere lunară", List.of(step("Colectează"), step("Verifică"), step("Trimite")));

        assertThat(template.steps()).extracting(s -> s.position()).containsExactly(0, 1, 2);
    }

    @Test
    void refusesABlankName() {
        assertThatThrownBy(() -> authored("   ", List.of(step("Pregătește"))))
                .isInstanceOf(TemplateNameRequiredException.class);
    }

    @Test
    void refusesATemplateWithNoSteps() {
        assertThatThrownBy(() -> authored("Integrare", List.of())).isInstanceOf(TemplateNeedsAStepException.class);
    }

    @Test
    void refusesAStepNamingNoWorkAndSaysWhichOne() {
        assertThatThrownBy(() ->
                        authored("Integrare", List.of(step("Pregătește"), StepDraft.added(null, 4), step("Prima zi"))))
                .isInstanceOf(StepNeedsTaskTemplateException.class)
                .extracting(e -> ((StepNeedsTaskTemplateException) e).position())
                .isEqualTo(1);
    }

    @Test
    void acceptsAStepWithNoExpectedDuration() {
        ProcessTemplate template = authored("Integrare", List.of(StepDraft.added(library.work("Pregătește"), null)));

        assertThat(template.steps().getFirst().expectedDurationHours()).isNull();
    }

    @Test
    void letsTwoStepsShareOneTaskTemplate() {
        ProcessTemplate template =
                authored("Livrare", List.of(step("Verificare"), step("Livrează"), step("Verificare")));

        assertThat(template.steps()).extracting(s -> s.taskTemplateId()).hasSize(3);
        assertThat(template.steps().get(0).taskTemplateId())
                .isEqualTo(template.steps().get(2).taskTemplateId());
    }

    @Test
    void startsWithNoDependenciesAndEveryStepAnEntryStep() {
        ProcessTemplate template = authored("Integrare", List.of(step("A"), step("B")));

        assertThat(template.dependencies()).isEmpty();
        assertThat(template.graph().entrySteps()).hasSize(2);
    }
}
