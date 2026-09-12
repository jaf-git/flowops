package com.flowops.tasklib.application.published;

import java.util.Optional;
import java.util.UUID;

public interface TemplateResolutionUseCase {
    UUID resolve(String title, String description, UUID author);

    Optional<UUID> findMeaning(String title);
}
