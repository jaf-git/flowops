package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.TrackHandover;
import java.util.List;

public interface TrackClosurePort {
    void close(Track track);

    void recordHandover(TrackHandover handover);

    List<Track> closedQualifyingTracks(JobId job);
}
