package com.flowops.tasklib.application.published;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TemplateContentUseCase {
    Optional<Content> of(UUID templateId);

    Map<UUID, Content> ofAll(Collection<UUID> templateIds);

    Map<UUID, Content> wordsForDisplay(Collection<UUID> templateIds);

    record Content(String title, String description, List<String> checklist) {}
}
