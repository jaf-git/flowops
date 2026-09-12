package com.flowops.discovery.application.crossing;

import java.util.UUID;

public interface ComposeProcessUseCase {
    Composed execute(Compose command);

    record Compose(UUID trackId, String name) {}

    record Composed(UUID trackId, UUID processTemplateId) {}
}
