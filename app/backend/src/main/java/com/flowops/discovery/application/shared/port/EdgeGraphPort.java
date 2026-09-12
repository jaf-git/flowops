package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.Loop;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkEdge;
import java.util.List;

public interface EdgeGraphPort {
    void save(WorkEdge edge);

    List<WorkEdge> edgesWithin(TrackId track);

    void save(Loop loop);

    List<Loop> loopsOf(JobId job);
}
