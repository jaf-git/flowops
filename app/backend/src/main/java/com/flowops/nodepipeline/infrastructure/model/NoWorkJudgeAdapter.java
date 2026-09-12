package com.flowops.nodepipeline.infrastructure.model;

import com.flowops.nodepipeline.application.port.WorkJudgePort;
import com.flowops.nodepipeline.domain.ai.Judgement;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(name = "ollamaWorkJudgeAdapter")
public class NoWorkJudgeAdapter implements WorkJudgePort {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isEnabled(Judgement.PlugPoint plugPoint) {
        return false;
    }

    @Override
    public Optional<Judgement.Verdict> judge(Judgement.Question question) {
        return Optional.empty();
    }

    @Override
    public int callsRemaining() {
        return 0;
    }

    @Override
    public String modelId() {
        return null;
    }

    @Override
    public String promptVersion() {
        return null;
    }
}
