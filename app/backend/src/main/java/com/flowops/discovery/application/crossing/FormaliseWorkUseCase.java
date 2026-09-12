package com.flowops.discovery.application.crossing;

import java.util.List;
import java.util.UUID;

public interface FormaliseWorkUseCase {
    Formalised execute(Formalise command);

    record Formalise(UUID nodeId, String title, String detail, List<String> steps) {}

    record Formalised(UUID nodeId, UUID taskTemplateId) {}
}
