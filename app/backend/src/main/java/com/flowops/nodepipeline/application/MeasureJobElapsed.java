package com.flowops.nodepipeline.application;

import com.flowops.nodepipeline.application.port.WaitReadPort;
import com.flowops.nodepipeline.domain.wait.JobElapsed;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeasureJobElapsed {
    private final WaitReadPort waits;
    private final Clock clock;

    public MeasureJobElapsed(WaitReadPort waits, Clock clock) {
        this.waits = waits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<JobElapsed> of(UUID jobId) {
        return waits.windowOf(jobId).map(window -> {
            Instant endOfTheClock = window.closedAt() == null ? clock.instant() : window.closedAt();
            return JobElapsed.over(window.jobId(), window.openedAt(), endOfTheClock, waits.waitsIn(jobId));
        });
    }
}
