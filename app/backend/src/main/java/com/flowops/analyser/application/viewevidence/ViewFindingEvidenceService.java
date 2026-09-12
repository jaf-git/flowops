package com.flowops.analyser.application.viewevidence;

import com.flowops.analyser.application.dismissfinding.DismissFindingUseCase;
import com.flowops.analyser.application.shared.port.FindingEvidencePort;
import com.flowops.analyser.application.shared.port.FindingReadPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewFindingEvidenceService implements ViewFindingEvidenceUseCase {
    private final FindingReadPort findings;
    private final FindingEvidencePort evidence;

    public ViewFindingEvidenceService(FindingReadPort findings, FindingEvidencePort evidence) {
        this.findings = findings;
        this.evidence = evidence;
    }

    @Override
    @Transactional(readOnly = true)
    public FindingEvidencePort.Evidence execute(UUID findingId) {
        findings.byId(findingId).orElseThrow(() -> new DismissFindingUseCase.NoSuchFinding(findingId));

        return evidence.evidenceFor(findingId);
    }
}
