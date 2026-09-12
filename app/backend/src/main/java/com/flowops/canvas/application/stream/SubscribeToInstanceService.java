package com.flowops.canvas.application.stream;

import com.flowops.canvas.application.shared.port.InstanceScopePort;
import com.flowops.canvas.application.shared.port.TaskEventFeedPort;
import com.flowops.canvas.application.stream.exception.CursorTooOldException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SubscribeToInstanceService implements SubscribeToInstanceUseCase {
    static final int REPLAY_LIMIT = 500;

    private static final Logger LOG = LoggerFactory.getLogger(SubscribeToInstanceService.class);

    private final TaskEventFeedPort feed;
    private final InstanceScopePort scope;
    private final CanvasStreamRegistry registry;

    public SubscribeToInstanceService(TaskEventFeedPort feed, InstanceScopePort scope, CanvasStreamRegistry registry) {
        this.feed = feed;
        this.scope = scope;
        this.registry = registry;
    }

    @Override
    public UUID subscribe(UUID person, Set<String> permissions, UUID instance, Long cursor, CanvasSink sink) {
        if (!scope.mayView(person, permissions, instance)) {
            throw new StreamNotAvailableException();
        }

        long live = feed.currentCursor();

        if (cursor != null && live - cursor > REPLAY_LIMIT) {
            throw new CursorTooOldException(cursor, live);
        }

        registry.watermark(live);

        CanvasSubscription subscription = new CanvasSubscription(UUID.randomUUID(), person, instance, sink);
        registry.add(subscription);

        if (cursor != null) {
            try {
                replay(person, permissions, instance, cursor, sink);
            } catch (RuntimeException failed) {
                registry.remove(subscription.id());
                throw failed;
            }
        }

        return subscription.id();
    }

    @Override
    public void unsubscribe(UUID subscription) {
        registry.remove(subscription);
    }

    @Override
    public long currentCursor() {
        return feed.currentCursor();
    }

    private void replay(UUID person, Set<String> permissions, UUID instance, long cursor, CanvasSink sink) {
        List<TaskEventFeedPort.Event> missed = feed.after(cursor, REPLAY_LIMIT);
        for (TaskEventFeedPort.Event event : missed) {
            if (!belongsToTheWatchedRun(event, instance) || !scope.mayView(person, permissions, instance)) {
                continue;
            }
            try {
                sink.send(new CanvasDelta(event.sequence(), event.task(), event.kind()));
            } catch (Exception sending) {
                LOG.debug("a stream closed while its gap was being replayed", sending);
                return;
            }
        }
    }

    private boolean belongsToTheWatchedRun(TaskEventFeedPort.Event event, UUID instance) {
        return scope.instanceOf(event.task()).filter(instance::equals).isPresent();
    }
}
