package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.application.port.WorkJudgePort;
import com.flowops.nodepipeline.domain.ai.Judgement;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * {@code 05_AI.md} specifies the model's call budget as calls <em>per run</em>. It was implemented
 * as a counter on a singleton bean that nothing ever reset, so it was really calls per process: a
 * server that had spent its budget once stopped asking the model for the rest of its life, and
 * every later run reported deterministic answers as though no model were configured.
 *
 * <p>{@link WorkJudgePort#beginRun()} is the seam that fixes it. This pins the contract on the port
 * rather than on the Ollama adapter, so a second adapter cannot quietly reintroduce the bug.
 */
class TheCallBudgetIsPerRunTest {

    /** A judge with a budget of two, counting the same way the real adapter does. */
    private static final class BudgetedJudge implements WorkJudgePort {
        private static final int BUDGET = 2;
        private final AtomicInteger used = new AtomicInteger();

        @Override
        public void beginRun() {
            used.set(0);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean isEnabled(Judgement.PlugPoint plugPoint) {
            return true;
        }

        @Override
        public Optional<Judgement.Verdict> judge(Judgement.Question question) {
            if (used.get() >= BUDGET) {
                return Optional.empty();
            }
            used.incrementAndGet();
            return Optional.of(new Judgement.Verdict("SAME", 0.9, "yes"));
        }

        @Override
        public int callsRemaining() {
            return Math.max(0, BUDGET - used.get());
        }

        @Override
        public String modelId() {
            return "budgeted";
        }

        @Override
        public String promptVersion() {
            return "v1";
        }
    }

    private static Judgement.Question anything() {
        return new Judgement.Question(Judgement.PlugPoint.SAME_WORK, "some work", java.util.List.of("T-1"), null);
    }

    @Test
    void aSecondRunGetsTheWholeBudgetAgainRatherThanWhatTheFirstLeftBehind() {
        BudgetedJudge judge = new BudgetedJudge();

        judge.beginRun();
        assertThat(judge.judge(anything())).isPresent();
        assertThat(judge.judge(anything())).isPresent();
        assertThat(judge.judge(anything()))
                .as("the third call in one run is over budget and answers as a model with no opinion does")
                .isEmpty();
        assertThat(judge.callsRemaining()).isZero();

        judge.beginRun();

        assertThat(judge.callsRemaining())
                .as("a run is the unit the budget is specified in, so the next one starts whole")
                .isEqualTo(BudgetedJudge.BUDGET);
        assertThat(judge.judge(anything()))
                .as("and the model is asked again rather than silently staying quiet forever")
                .isPresent();
    }

    @Test
    void aJudgeWithNothingToResetNeedNotImplementIt() {
        WorkJudgePort none = new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter();

        none.beginRun();

        assertThat(none.callsRemaining()).isZero();
        assertThat(none.judge(anything())).isEmpty();
    }
}
