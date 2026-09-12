package com.flowops.analyser.application.viewevidence;

import com.flowops.analyser.application.shared.port.FindingEvidencePort;
import java.util.UUID;

public interface ViewFindingEvidenceUseCase {
    FindingEvidencePort.Evidence execute(UUID findingId);
}
