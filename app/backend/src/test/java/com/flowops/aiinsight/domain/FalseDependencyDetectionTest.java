package com.flowops.aiinsight.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.aiinsight.domain.FalseDependencyDetection.EdgeObservation;
import com.flowops.aiinsight.domain.FalseDependencyDetection.FalseDependency;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FalseDependencyDetectionTest {
    private static final UUID RUN_1 = UUID.randomUUID();
    private static final UUID RUN_2 = UUID.randomUUID();
    private static final UUID RUN_3 = UUID.randomUUID();

    private static EdgeObservation seen(UUID run, String dependent, String dependsOn, boolean startedEarly) {
        return new EdgeObservation(run, dependent, dependsOn, startedEarly);
    }

    private static List<EdgeObservation> idleAcross(UUID... runs) {
        List<EdgeObservation> observations = new ArrayList<>();
        for (UUID run : runs) {
            observations.add(seen(run, "Pregătește materialele", "Rezervă sala", true));
        }
        return observations;
    }

    @Test
    @DisplayName("an edge that never caused a wait in three runs is reported")
    void neverWaited() {
        List<FalseDependency> found = FalseDependencyDetection.over(idleAcross(RUN_1, RUN_2, RUN_3));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).dependentTitle()).isEqualTo("Pregătește materialele");
        assertThat(found.get(0).dependsOnTitle()).isEqualTo("Rezervă sala");
        assertThat(found.get(0).runsObserved()).isEqualTo(3);
        assertThat(found.get(0).instances()).containsExactlyInAnyOrder(RUN_1, RUN_2, RUN_3);
    }

    @Test
    @DisplayName("waiting even once disqualifies the edge entirely")
    void oneWaitIsEnough() {
        List<EdgeObservation> mostlyIdle = new ArrayList<>(idleAcross(RUN_1, RUN_2));
        mostlyIdle.add(seen(RUN_3, "Pregătește materialele", "Rezervă sala", false));

        assertThat(FalseDependencyDetection.over(mostlyIdle)).isEmpty();
    }

    @Test
    @DisplayName("two runs are not enough, however idle the edge looks")
    void belowTheThreshold() {
        assertThat(FalseDependencyDetection.over(idleAcross(RUN_1, RUN_2))).isEmpty();
    }

    @Test
    @DisplayName("edges are told apart even when their titles concatenate to the same string")
    void keysDoNotCollide() {
        List<EdgeObservation> observations = new ArrayList<>();
        for (UUID run : List.of(RUN_1, RUN_2, RUN_3)) {
            observations.add(seen(run, "A", "B C", true));
            observations.add(seen(run, "A B", "C", false));
        }

        List<FalseDependency> found = FalseDependencyDetection.over(observations);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).dependentTitle()).isEqualTo("A");
        assertThat(found.get(0).dependsOnTitle()).isEqualTo("B C");
    }

    @Test
    @DisplayName("the strongest evidence is reported first")
    void orderedByEvidence() {
        List<EdgeObservation> observations = new ArrayList<>(idleAcross(RUN_1, RUN_2, RUN_3));
        UUID fourth = UUID.randomUUID();
        for (UUID run : List.of(RUN_1, RUN_2, RUN_3, fourth)) {
            observations.add(seen(run, "Trimite factura", "Aprobă devizul", true));
        }

        List<FalseDependency> found = FalseDependencyDetection.over(observations);

        assertThat(found).hasSize(2);

        assertThat(found.get(0).dependentTitle()).isEqualTo("Trimite factura");
        assertThat(found.get(0).runsObserved()).isEqualTo(4);
    }

    @Test
    @DisplayName("nothing observed is nothing reported")
    void nothingToSay() {
        assertThat(FalseDependencyDetection.over(List.of())).isEmpty();
    }
}
