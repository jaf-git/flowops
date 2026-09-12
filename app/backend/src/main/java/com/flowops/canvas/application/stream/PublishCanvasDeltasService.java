package com.flowops.canvas.application.stream;

import com.flowops.canvas.application.shared.port.InstanceScopePort;
import com.flowops.canvas.application.shared.port.SubscriberPermissionsPort;
import com.flowops.canvas.application.shared.port.TaskEventFeedPort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PublishCanvasDeltasService implements PublishCanvasDeltasUseCase {
    static final int BATCH = 200;

    private static final Logger LOG = LoggerFactory.getLogger(PublishCanvasDeltasService.class);

    private final TaskEventFeedPort feed;
    private final InstanceScopePort scope;
    private final SubscriberPermissionsPort permissions;
    private final CanvasStreamRegistry registry;

    public PublishCanvasDeltasService(
            TaskEventFeedPort feed,
            InstanceScopePort scope,
            SubscriberPermissionsPort permissions,
            CanvasStreamRegistry registry) {
        this.feed = feed;
        this.scope = scope;
        this.permissions = permissions;
        this.registry = registry;
    }

    @Override
    public synchronized void pump() {
        long live = feed.currentCursor();

        if (registry.advanceWhileUnwatched(live)) {
            return;
        }

        long from = registry.watermark(live);
        List<TaskEventFeedPort.Event> fresh = feed.after(from, BATCH);
        if (fresh.isEmpty()) {
            return;
        }

        Map<UUID, Optional<UUID>> runOfTask = new HashMap<>();

        Map<UUID, Set<String>> heldBy = new HashMap<>();

        for (TaskEventFeedPort.Event event : fresh) {
            Optional<UUID> run = runOfTask.computeIfAbsent(event.task(), scope::instanceOf);
            if (run.isEmpty()) {
                continue;
            }
            deliver(event, run.get(), heldBy);
        }

        registry.pumpedTo(fresh.get(fresh.size() - 1).sequence());
    }

    private void deliver(TaskEventFeedPort.Event event, UUID run, Map<UUID, Set<String>> heldBy) {
        for (CanvasSubscription subscription : registry.openStreams()) {
            if (!subscription.instance().equals(run)) {
                continue;
            }
            Set<String> held = heldBy.computeIfAbsent(subscription.person(), permissions::heldBy);
            if (!scope.mayView(subscription.person(), held, run)) {
                continue;
            }
            try {
                subscription.sink().send(new CanvasDelta(event.sequence(), event.task(), event.kind()));
            } catch (Exception sending) {
                LOG.debug("dropping a stream that could not be written to", sending);
                registry.remove(subscription.id());
                subscription.sink().close();
            }
        }
    }
}
