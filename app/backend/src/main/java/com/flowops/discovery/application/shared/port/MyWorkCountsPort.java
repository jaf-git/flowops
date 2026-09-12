package com.flowops.discovery.application.shared.port;

import java.time.Instant;
import java.util.UUID;

public interface MyWorkCountsPort {
    MyWorkCounts countsFor(UUID viewer, Instant weekStarted);

    record MyWorkCounts(int waitingOnYou, int openWork, int deliveredThisWeek) {}
}
