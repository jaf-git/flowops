package com.flowops.discovery.application.crossing.port;

import java.util.List;
import java.util.UUID;

public interface TemplateCreationPort {
    UUID createTemplateFor(String title, String detail, List<String> steps, UUID author);
}
