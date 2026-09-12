package com.flowops.nodepipeline.application.port;

import java.util.Optional;
import java.util.UUID;

public interface PipelineCallerPort {
    Optional<UUID> currentCaller();
}
