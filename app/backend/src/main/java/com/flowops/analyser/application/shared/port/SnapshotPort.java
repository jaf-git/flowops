package com.flowops.analyser.application.shared.port;

import com.flowops.analyser.domain.Snapshot;
import java.time.Instant;

public interface SnapshotPort {
    Snapshot readWindow(Instant from, Instant to);
}
