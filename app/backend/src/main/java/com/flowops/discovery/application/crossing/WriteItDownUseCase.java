package com.flowops.discovery.application.crossing;

import java.util.List;
import java.util.UUID;

public interface WriteItDownUseCase {
    record WriteItDown(UUID recommendationId, String name) {}

    record WrittenDown(UUID recommendationId, UUID processTemplateId, String name, List<String> steps) {}

    WrittenDown execute(WriteItDown command);
}
