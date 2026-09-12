package com.flowops.canvas.application.stream;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class CanvasStreamRegistry {
    private final Map<UUID, CanvasSubscription> open = new ConcurrentHashMap<>();
    private final AtomicLong pumpedTo = new AtomicLong(-1);

    private final Object admission = new Object();

    public void add(CanvasSubscription subscription) {
        synchronized (admission) {
            open.put(subscription.id(), subscription);
        }
    }

    public boolean advanceWhileUnwatched(long liveCursor) {
        synchronized (admission) {
            if (!open.isEmpty()) {
                return false;
            }
            pumpedTo(liveCursor);
            return true;
        }
    }

    public void remove(UUID subscription) {
        open.remove(subscription);
    }

    public Collection<CanvasSubscription> openStreams() {
        return open.values();
    }

    public int size() {
        return open.size();
    }

    public long watermark(long liveCursorIfUnset) {
        return pumpedTo.updateAndGet(current -> current < 0 ? liveCursorIfUnset : current);
    }

    public void pumpedTo(long cursor) {
        pumpedTo.updateAndGet(current -> Math.max(current, cursor));
    }
}
