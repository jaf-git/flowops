package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.exception.ApprovalScoreOutOfRangeException;
import com.flowops.task.domain.exception.ApprovalScoreRequiredException;
import com.flowops.task.domain.model.ApprovalScore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("TASK-APPROVE-01")
class ApprovalScoreTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void everyScoreFromOneToFiveIsAccepted(int offered) {
        assertThat(ApprovalScore.of(offered).value()).isEqualTo(offered);
    }

    @Test
    void noScoreAtAllIsRefused() {
        assertThatThrownBy(() -> ApprovalScore.of(null)).isInstanceOf(ApprovalScoreRequiredException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 6, 100})
    void aScoreOutsideOneToFiveIsRefusedAndNamesWhatItRefused(int offered) {
        assertThatThrownBy(() -> ApprovalScore.of(offered))
                .isInstanceOf(ApprovalScoreOutOfRangeException.class)
                .satisfies(failure -> assertThat(((ApprovalScoreOutOfRangeException) failure).offered())
                        .isEqualTo(offered));
    }

    @Test
    void zeroIsAScoreThatWasNeverGivenRatherThanTheLowestOne() {
        assertThatThrownBy(() -> ApprovalScore.of(0)).isInstanceOf(ApprovalScoreOutOfRangeException.class);
        assertThat(ApprovalScore.LOWEST).isEqualTo(1);
    }
}
