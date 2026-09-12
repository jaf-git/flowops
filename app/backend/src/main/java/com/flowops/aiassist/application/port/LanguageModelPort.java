package com.flowops.aiassist.application.port;

import com.flowops.aiassist.domain.EvidencePacket;
import com.flowops.aiassist.domain.ShapeOpinion;
import java.util.Optional;

public interface LanguageModelPort {
    boolean isAvailable();

    Optional<ShapeOpinion> readShapeOf(EvidencePacket evidence);
}
