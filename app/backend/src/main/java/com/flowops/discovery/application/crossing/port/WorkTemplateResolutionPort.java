package com.flowops.discovery.application.crossing.port;

import java.util.UUID;

public interface WorkTemplateResolutionPort {
    UUID templateFor(String title, String provenance, UUID author);
}
