package com.flowops.process.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.flowops.process.domain.exception.CycleWouldFormException;
import com.flowops.process.domain.exception.UnknownStepException;
import com.flowops.process.domain.model.DependencyGraph;
import com.flowops.process.domain.model.StepDependency;
import com.flowops.process.domain.model.StepId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("PROCESS-DEFINE-DEPENDENCIES-01")
class DependencyGraphTest {
    private static final StepId A = StepId.of(UUID.randomUUID());
    private static final StepId B = StepId.of(UUID.randomUUID());
    private static final StepId C = StepId.of(UUID.randomUUID());

    private static DependencyGraph graph(StepDependency... edges) {
        return DependencyGraph.of(List.of(A, B, C), List.of(edges));
    }

    private static StepDependency waits(StepId dependent, StepId dependsOn) {
        return StepDependency.of(dependent, dependsOn);
    }

    @Test
    void aStepThatDependsOnNothingIsAnEntryStep() {
        DependencyGraph g = graph(waits(B, A));

        assertThat(g.entrySteps()).containsExactlyInAnyOrder(A, C);
    }

    @Test
    void withNoEdgesAtAllEveryStepIsAnEntryStep() {
        DependencyGraph g = graph();

        assertThat(g.entrySteps()).containsExactlyInAnyOrder(A, B, C);
        g.requireValid();
    }

    @Test
    void refusesAnEdgeThatWouldCloseATransitiveCycle() {
        DependencyGraph chain = graph(waits(B, A), waits(C, B));

        assertThatThrownBy(() -> chain.with(waits(A, C))).isInstanceOf(CycleWouldFormException.class);
    }

    @Test
    void namesTheStepsOfTheCycleItRefuses() {
        DependencyGraph chain = graph(waits(B, A), waits(C, B));

        CycleWouldFormException refused =
                catchThrowableOfType(CycleWouldFormException.class, () -> chain.with(waits(A, C)));

        assertThat(refused.cycle()).contains(A, B, C);
    }

    @Test
    void refusesADirectCycleBetweenTwoSteps() {
        DependencyGraph g = graph(waits(B, A));

        assertThatThrownBy(() -> g.with(waits(A, B))).isInstanceOf(CycleWouldFormException.class);
    }

    @Test
    void refusesAStepThatDependsOnItself() {
        assertThatThrownBy(() -> StepDependency.of(A, A)).isInstanceOf(CycleWouldFormException.class);
    }

    @Test
    void refusesAnEdgeNamingAStepThisTemplateDoesNotHave() {
        StepId elsewhere = StepId.of(UUID.randomUUID());

        assertThatThrownBy(() -> DependencyGraph.of(List.of(A, B), List.of(waits(A, elsewhere))))
                .isInstanceOf(UnknownStepException.class);
    }

    @Test
    void addingTheSameEdgeTwiceLeavesOneEdge() {
        DependencyGraph once = graph().with(waits(B, A));
        DependencyGraph twice = once.with(waits(B, A));

        assertThat(twice.edges()).hasSize(1);
        assertThat(once.contains(waits(B, A))).isTrue();
    }

    @Test
    void removingAnEdgeLeavesTheGraphValidAndMakesItsStepAnEntryStep() {
        DependencyGraph g = graph(waits(B, A)).without(waits(B, A));

        assertThat(g.edges()).isEmpty();
        assertThat(g.entrySteps()).containsExactlyInAnyOrder(A, B, C);
    }

    @Test
    void reportsTheStepsThatNoEntryStepReaches() {
        DependencyGraph cyclic =
                DependencyGraph.of(List.of(A, B), List.of(new StepDependency(A, B), new StepDependency(B, A)));

        assertThat(cyclic.entrySteps()).isEmpty();
        assertThat(cyclic.stepsUnreachableFromAnyEntry()).containsExactlyInAnyOrder(A, B);

        assertThatThrownBy(cyclic::requireValid).isInstanceOf(CycleWouldFormException.class);
    }

    @Test
    void removingAStepRemovesEveryEdgeThatTouchedIt() {
        DependencyGraph g = graph(waits(B, A), waits(C, B)).withoutStep(B);

        assertThat(g.edges()).isEmpty();
        assertThat(g.entrySteps()).containsExactlyInAnyOrder(A, C);
        g.requireValid();
    }

    @Test
    void aValidGraphReportsNothingUnreachable() {
        assertThat(graph(waits(B, A), waits(C, B)).stepsUnreachableFromAnyEntry())
                .isEmpty();
    }
}
