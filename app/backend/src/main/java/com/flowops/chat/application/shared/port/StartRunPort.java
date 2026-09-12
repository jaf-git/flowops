package com.flowops.chat.application.shared.port;

import java.util.List;
import java.util.UUID;

public interface StartRunPort {
    StartableTemplates startableTemplates();

    UUID startRun(UUID templateId, UUID processOwner);

    record StartableTemplate(UUID id, String name, String overview, int stepCount) {}

    record StartableTemplates(boolean permitted, List<StartableTemplate> templates) {}
}
