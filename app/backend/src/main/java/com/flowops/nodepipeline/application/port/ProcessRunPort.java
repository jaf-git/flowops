package com.flowops.nodepipeline.application.port;

import java.util.List;
import java.util.UUID;

public interface ProcessRunPort {
    List<StartableProcess> startable();

    UUID startRun(UUID templateId, String name, UUID processOwner);

    record StartableProcess(UUID templateId, String name, String overview, int stepCount) {}
}
