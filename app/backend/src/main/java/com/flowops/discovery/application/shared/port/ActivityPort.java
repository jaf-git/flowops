package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.Activity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ActivityPort {
    UUID nextId();

    void save(Activity activity);

    Optional<Activity> find(UUID id);

    Optional<Activity> findBySlug(String slug);

    List<Activity> all();

    List<Activity> choosable();

    void nameTheWork(UUID workNodeId, UUID activityId, UUID by, Instant at);

    long marksNaming(UUID activityId);

    Map<UUID, List<String>> departmentSpread();

    Map<UUID, List<String>> clientSpread();

    List<PriorWork> workOfThisKindIn(UUID jobId, String workType);

    void repointUsage(UUID from, UUID into);

    record PriorWork(String performerName, String activityName) {}
}
