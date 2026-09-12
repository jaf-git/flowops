package com.flowops.tasklib.application.port;

import java.util.UUID;

public interface RecordTaskProvenancePort {
    void stampedFrom(UUID task, UUID template);
}
