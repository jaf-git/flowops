package com.flowops.discovery.application.waitclock;

import com.flowops.discovery.application.shared.port.DiscoveryNoticePort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeWait;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaitClock {
    private static final Logger LOG = LoggerFactory.getLogger(WaitClock.class);

    private static final Duration LONG_ENOUGH_TO_TELL_THE_OWNER = Duration.ofDays(7);

    private static final long EVERY_HOUR = 3_600_000L;

    private final WorkBracketPort brackets;
    private final WorkGraphPort jobs;
    private final DiscoveryNoticePort notices;
    private final Clock clock;

    public WaitClock(WorkBracketPort brackets, WorkGraphPort jobs, DiscoveryNoticePort notices, Clock clock) {
        this.brackets = brackets;
        this.jobs = jobs;
        this.notices = notices;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = EVERY_HOUR)
    @Transactional(readOnly = true)
    public Rung sweep() {
        Instant now = clock.instant();

        int chased = 0;
        int escalated = 0;

        for (WorkNodeWait overdue : brackets.waitsPastTheirExpectedDate(now)) {
            Optional<WorkBracket> waiter = brackets.find(overdue.bracketId());

            if (waiter.isPresent()) {
                notices.theDateYouExpectedHasPassed(waiter.get().closureRight(), overdue.bracketId());
                chased++;
            }
        }

        for (WorkNodeWait running : brackets.externalWaitsOpenSince(now.minus(LONG_ENOUGH_TO_TELL_THE_OWNER))) {
            Optional<Job> engagement =
                    brackets.find(running.bracketId()).flatMap(waiter -> jobs.findJob(waiter.jobId()));

            if (engagement.isPresent()) {
                notices.thisHasBeenWaitingALongTime(
                        engagement.get().openedBy(), engagement.get().id());
                escalated++;
            }
        }

        if (chased > 0 || escalated > 0) {
            LOG.info("wait clock: {} expected dates passed, {} external waits ran long", chased, escalated);
        }

        return new Rung(chased, escalated);
    }

    public record Rung(int datesPassed, int externalWaitsRunningLong) {}
}
