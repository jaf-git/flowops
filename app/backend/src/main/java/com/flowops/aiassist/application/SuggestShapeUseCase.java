package com.flowops.aiassist.application;

import com.flowops.aiassist.domain.GroundedSuggestion;
import java.util.Optional;
import java.util.UUID;

public interface SuggestShapeUseCase {
    Optional<GroundedSuggestion> shapeOf(UUID subjectId);

    boolean isAvailable();
}
