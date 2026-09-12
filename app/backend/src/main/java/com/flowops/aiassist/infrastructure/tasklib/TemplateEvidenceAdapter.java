package com.flowops.aiassist.infrastructure.tasklib;

import com.flowops.aiassist.application.port.SubjectEvidencePort;
import com.flowops.aiassist.domain.EvidencePacket;
import com.flowops.tasklib.application.published.TemplateContentUseCase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TemplateEvidenceAdapter implements SubjectEvidencePort {
    private final TemplateContentUseCase templates;

    public TemplateEvidenceAdapter(TemplateContentUseCase templates) {
        this.templates = templates;
    }

    @Override
    public Optional<EvidencePacket> gather(UUID subjectId) {
        return templates
                .of(subjectId)
                .map(content -> EvidencePacket.about(content.title(), content.description(), content.checklist()));
    }
}
