package com.flowops.discovery.domain.analysis;

import java.util.List;

public interface Detector {
    String name();

    Stage stage();

    default int sampleFloor() {
        return 3;
    }

    List<Finding> detect(GraphWindow window);
}
