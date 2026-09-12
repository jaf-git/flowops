package com.flowops.aiexport.seed;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {
    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableClock(Instant start, ZoneId zone) {
        this.now = new AtomicReference<>(start);
        this.zone = zone;
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId other) {
        return new MutableClock(now.get(), other);
    }

    public void advanceTo(Instant later) {
        now.updateAndGet(current -> {
            if (later.isBefore(current)) {
                throw new IllegalArgumentException("the demo clock only moves forward: " + current + " -> " + later);
            }
            return later;
        });
    }

    public void set(Instant at) {
        now.set(at);
    }
}
