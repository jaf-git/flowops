package com.flowops.discovery.application.activities;

import com.flowops.discovery.application.shared.port.ActivityPort;
import com.flowops.discovery.application.shared.port.StaleLibraryPort;
import com.flowops.discovery.domain.enums.ActivityStatus;
import com.flowops.discovery.domain.model.Activity;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageActivities {
    private static final int USED_BY_ENOUGH_DEPARTMENTS_TO_BE_VAGUE = 3;

    private final ActivityPort activities;
    private final StaleLibraryPort library;
    private final Clock clock;

    public ManageActivities(ActivityPort activities, StaleLibraryPort library, Clock clock) {
        this.activities = activities;
        this.library = library;
        this.clock = clock;
    }

    @Transactional
    public Activity add(String name, UUID by) {
        String slug = Activity.slugOf(name);

        return activities.findBySlug(slug).orElseGet(() -> {
            Activity created = Activity.named(activities.nextId(), name, by, clock.instant());
            activities.save(created);
            return created;
        });
    }

    @Transactional(readOnly = true)
    public List<Activity> all() {
        return activities.all();
    }

    @Transactional(readOnly = true)
    public List<Activity> choosable() {
        return activities.choosable();
    }

    @Transactional(readOnly = true)
    public List<Listed> catalogue() {
        Map<UUID, List<String>> departments = activities.departmentSpread();
        Map<UUID, List<String>> clients = activities.clientSpread();

        return activities.choosable().stream()
                .map(activity -> {
                    List<String> spread = departments.getOrDefault(activity.id(), List.of());
                    return new Listed(
                            activity,
                            spread,
                            clients.getOrDefault(activity.id(), List.of()),
                            spread.size() >= USED_BY_ENOUGH_DEPARTMENTS_TO_BE_VAGUE);
                })
                .toList();
    }

    @Transactional
    public Activity merge(UUID losingId, UUID survivingId) {
        Activity losing = mustFind(losingId);
        Activity surviving = mustFind(survivingId);

        if (losingId.equals(survivingId)) {
            throw new ActivityMergeRefusedException("an activity cannot be merged into itself");
        }
        if (!surviving.status().isChoosable()) {
            throw new ActivityMergeRefusedException("\"" + surviving.name() + "\" is " + surviving.status()
                    + ", so merging into it would hide both names rather than joining them");
        }
        if (!losing.status().isChoosable()) {
            throw new ActivityMergeRefusedException(
                    "\"" + losing.name() + "\" is already " + losing.status() + " and has nothing left to merge");
        }

        activities.repointUsage(losingId, survivingId);

        surviving.absorbed(losing);
        losing.mergedInto(survivingId);

        activities.save(surviving);
        activities.save(losing);

        // The graph now agrees with the decision; the library does not, and it is not this method's
        // to correct. Approved templates drafted from the absorbed name are surfaced as a finding
        // with themselves as its evidence, and a person decides whether they still describe real
        // work — DECISION-NO-TEMPLATE-BINDING-01, and the reason nothing here retires anything.
        List<StaleLibraryPort.StaleTemplate> leftBehind = library.approvedTemplatesNaming(losing.name());
        if (!leftBehind.isEmpty()) {
            library.templatesLeftBehindByAMerge(losing.name(), surviving.name(), leftBehind, clock.instant());
        }

        return surviving;
    }

    @Transactional
    public Activity retire(UUID activityId) {
        Activity activity = mustFind(activityId);

        if (activity.status() == ActivityStatus.MERGED) {
            throw new ActivityMergeRefusedException("\"" + activity.name()
                    + "\" was merged into another activity, and retiring it would lose which one");
        }

        activity.retired();
        activities.save(activity);

        return activity;
    }

    @Transactional
    public Activity used(UUID activityId, UUID workNodeId, UUID by) {
        Activity activity = mustFind(activityId);

        activity.usedAt(clock.instant());
        activities.save(activity);
        activities.nameTheWork(workNodeId, activity.id(), by, clock.instant());

        return activity;
    }

    private Activity mustFind(UUID id) {
        return activities.find(id).orElseThrow(() -> new UnknownActivityException(id));
    }

    public record Listed(
            Activity activity, List<String> departments, List<String> clients, boolean tooGenericToBeOneThing) {}
}
