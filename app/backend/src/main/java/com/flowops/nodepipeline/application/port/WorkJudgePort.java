package com.flowops.nodepipeline.application.port;

import com.flowops.nodepipeline.domain.ai.Judgement;
import java.util.Optional;

public interface WorkJudgePort {
    /**
     * Tells the judge a run is starting, so a per-run call budget means what it says.
     *
     * <p>{@code 05_AI.md} specifies the budget as calls <em>per run</em>. Without this the counter
     * is per process: a server that has answered its budget once stops asking the model for the
     * rest of its life, and the runs after that quietly report deterministic answers as though no
     * model were configured. Judges with nothing to reset need not implement it.
     */
    default void beginRun() {}

    boolean isAvailable();

    boolean isEnabled(Judgement.PlugPoint plugPoint);

    Optional<Judgement.Verdict> judge(Judgement.Question question);

    int callsRemaining();

    String modelId();

    String promptVersion();
}
