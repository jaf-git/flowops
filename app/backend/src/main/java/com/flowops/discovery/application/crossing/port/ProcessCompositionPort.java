package com.flowops.discovery.application.crossing.port;

import java.util.List;
import java.util.UUID;

public interface ProcessCompositionPort {
    UUID composeProcessFrom(String name, List<UUID> taskTemplateIds);
}
