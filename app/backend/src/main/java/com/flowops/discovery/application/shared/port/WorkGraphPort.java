package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.enums.EvidenceOrigin;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.TrackKey;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkGraphPort {
    void save(Job job);

    Optional<Job> findJob(JobId id);

    JobId nextJobId();

    TrackId nextTrackId();

    void save(Track track);

    Optional<Track> findOpenTrack(JobId job, TrackKey key);

    Optional<Track> findOpenTrackForPerformer(JobId job, UUID performer);

    void save(WorkNode node);

    Optional<WorkNode> findNode(WorkNodeId id);

    Optional<Track> findTrack(TrackId id);

    List<WorkNode> nodesOf(TrackId track);

    List<WorkNode> orphansOf(JobId job);

    void deleteNode(WorkNodeId id);

    void deleteTrack(TrackId id);

    void deleteJob(JobId id);

    boolean holdsOtherWork(JobId job, WorkNodeId excluding);

    boolean holdsOtherNodes(JobId job, WorkNodeId excluding);

    Optional<Instant> evidenceMessageSentAt(WorkNodeId id);

    Optional<Job> openJobForCounterpartySince(UUID counterpartyId, Instant since);

    List<Job> openJobsTouchedIn(UUID conversationId, int limit);

    List<Job> jobsTouchedIn(UUID conversationId, int limit);

    List<Job> recentlyTouchedOpenJobs(int limit);

    void recordEvidence(WorkNode node, UUID messageId, EvidenceOrigin origin);

    Optional<WorkNode> findMarkOf(UUID messageId, JobId job, UUID performerId, NodeKind kind);
}
