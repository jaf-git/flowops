package com.flowops.aiassist.application.port;

import com.flowops.aiassist.domain.EvidencePacket;
import java.util.Optional;
import java.util.UUID;

public interface SubjectEvidencePort {
    Optional<EvidencePacket> gather(UUID subjectId);
}
