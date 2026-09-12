package com.flowops.aiassist.infrastructure.model;

import com.flowops.aiassist.application.port.LanguageModelPort;
import com.flowops.aiassist.domain.EvidencePacket;
import com.flowops.aiassist.domain.ShapeOpinion;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flowops.ai.enabled", havingValue = "false", matchIfMissing = true)
public class NoLanguageModelAdapter implements LanguageModelPort {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<ShapeOpinion> readShapeOf(EvidencePacket evidence) {
        return Optional.empty();
    }
}
