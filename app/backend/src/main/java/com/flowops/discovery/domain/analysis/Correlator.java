package com.flowops.discovery.domain.analysis;

import java.util.List;

public interface Correlator {
    String name();

    default int sampleFloor() {
        return 4;
    }

    List<Finding> correlate(GraphWindow window, List<Finding> found);
}
