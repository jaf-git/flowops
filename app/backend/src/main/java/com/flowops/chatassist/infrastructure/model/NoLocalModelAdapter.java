package com.flowops.chatassist.infrastructure.model;

import com.flowops.chatassist.application.port.LocalLanguageModelPort;
import com.flowops.chatassist.domain.ConversationExtract;
import com.flowops.chatassist.domain.WorkOpinion;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flowops.ai.enabled", havingValue = "false", matchIfMissing = true)
public class NoLocalModelAdapter implements LocalLanguageModelPort {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<WorkOpinion> readWorkIn(ConversationExtract conversation) {
        return Optional.empty();
    }
}
