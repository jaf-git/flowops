package com.flowops.discovery.application.analysis;

import com.flowops.discovery.domain.analysis.GraphWindow;
import java.time.Instant;

public interface AnalysisGraphPort {
    GraphWindow read(Instant from, Instant to);

    int waitCount(Instant from, Instant to);
}
