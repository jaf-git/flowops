package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.application.DiscoverShapes;
import com.flowops.nodepipeline.application.DiscoverShapesUseCase;
import com.flowops.nodepipeline.domain.discovery.DiscoveredProcess;
import com.flowops.nodepipeline.domain.discovery.DiscoveryWeights;
import com.flowops.nodepipeline.domain.discovery.ProcessDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("NODEPIPE-DISCOVER-SHAPES-01")
class TheWrapDoesNotChangeWhatTheClustererFindsTest {
    private static final String CORPUS = "multichat-extreme";

    private record BothSides(List<StepKind> kinds, List<DiscoveredProcess> processes) {}

    private static BothSides clusterDirectly() throws Exception {
        DiscoveryFixture fixture = DiscoveryFixture.load(CORPUS);
        DiscoveryWeights weights = DiscoveryWeights.reference();

        List<StepKind> kinds =
                new StepDiscovery(weights, fixture.directConversations()).discover(fixture.nodes(), fixture.excluded());
        List<DiscoveredProcess> processes =
                new ProcessDiscovery(weights).discover(fixture.jobs(), fixture.nodes(), kinds);
        return new BothSides(kinds, processes);
    }

    @Test
    void everyStepKindCrossesTheBoundaryUnchanged() throws Exception {
        BothSides direct = clusterDirectly();

        List<DiscoverShapesUseCase.StepKindView> translated = direct.kinds().stream()
                .map(TheWrapDoesNotChangeWhatTheClustererFindsTest::translate)
                .toList();

        assertThat(translated).hasSize(direct.kinds().size()).hasSize(12);
        assertThat(translated.stream()
                        .map(DiscoverShapesUseCase.StepKindView::id)
                        .sorted()
                        .toList())
                .isEqualTo(direct.kinds().stream().map(StepKind::id).sorted().toList());

        for (int index = 0; index < translated.size(); index++) {
            assertThat(translated.get(index).nodeIds())
                    .as("membership of %s", translated.get(index).id())
                    .isEqualTo(direct.kinds().get(index).nodeIds());
        }
    }

    @Test
    void theThreeSubprocessesAreStillSubprocessesAfterTranslation() throws Exception {
        BothSides direct = clusterDirectly();

        List<String> subprocesses = direct.kinds().stream()
                .map(TheWrapDoesNotChangeWhatTheClustererFindsTest::translate)
                .filter(DiscoverShapesUseCase.StepKindView::subprocess)
                .map(DiscoverShapesUseCase.StepKindView::id)
                .toList();

        assertThat(subprocesses).containsExactlyInAnyOrder("K:WRITER/dm-sk", "K:PHOTO/dm-tk", "K:DESIGN/dm-ka");
    }

    @Test
    void everyProcessCrossesTheBoundaryUnchanged() throws Exception {
        BothSides direct = clusterDirectly();

        List<DiscoverShapesUseCase.ProcessView> translated = direct.processes().stream()
                .map(TheWrapDoesNotChangeWhatTheClustererFindsTest::translate)
                .toList();

        assertThat(translated).hasSize(3);
        for (int index = 0; index < translated.size(); index++) {
            assertThat(translated.get(index).steps())
                    .isEqualTo(direct.processes().get(index).core());
            assertThat(translated.get(index).runs())
                    .isEqualTo(direct.processes().get(index).runs());
            assertThat(translated.get(index).jobIds())
                    .isEqualTo(direct.processes().get(index).jobIds());
        }
    }

    @Test
    void anOrderTheClustererWithheldIsNotLeakedByTheTranslation() throws Exception {
        BothSides direct = clusterDirectly();

        for (DiscoveredProcess process : direct.processes()) {
            DiscoverShapesUseCase.ProcessView view = translate(process);

            assertThat(view.orderReliable()).isEqualTo(process.orderReliable());
            assertThat(view.order()).isEqualTo(process.displayOrder());

            if (!process.orderReliable()) {
                assertThat(view.order())
                        .isEqualTo(process.core().stream().sorted().toList());
            }
        }
    }

    private static DiscoverShapesUseCase.StepKindView translate(StepKind kind) {
        return invoke("view", StepKind.class, kind);
    }

    private static DiscoverShapesUseCase.ProcessView translate(DiscoveredProcess process) {
        return invoke("view", DiscoveredProcess.class, process);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(String name, Class<?> parameter, Object argument) {
        try {
            java.lang.reflect.Method method = DiscoverShapes.class.getDeclaredMethod(name, parameter);
            method.setAccessible(true);
            return (T) method.invoke(null, argument);
        } catch (ReflectiveOperationException unreachable) {
            throw new IllegalStateException(
                    "DiscoverShapes." + name + "(" + parameter.getSimpleName() + ") is the translation under "
                            + "test; if it was renamed this test must be updated rather than deleted",
                    unreachable);
        }
    }
}
