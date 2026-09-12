package com.flowops.discovery.application.rhythm;

import com.flowops.discovery.application.closebracket.CloseBracket;
import com.flowops.discovery.application.shared.port.DiscoveryNoticePort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.model.BracketInvariants;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.RhythmWindows;
import com.flowops.discovery.domain.model.WorkBracket;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BracketRhythm {
    private static final Logger LOG = LoggerFactory.getLogger(BracketRhythm.class);

    private static final int ENGAGEMENTS_PER_CADENCE_PASS = 500;

    private final WorkBracketPort brackets;
    private final WorkGraphPort jobs;
    private final CloseBracket closing;
    private final DiscoveryNoticePort notices;
    private final Clock clock;

    public BracketRhythm(
            WorkBracketPort brackets,
            WorkGraphPort jobs,
            CloseBracket closing,
            DiscoveryNoticePort notices,
            Clock clock) {
        this.brackets = brackets;
        this.jobs = jobs;
        this.closing = closing;
        this.notices = notices;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${flowops.discovery.rhythm-ms:300000}")
    public void sweep() {
        RhythmWindows windows = RhythmWindows.defaults();

        nudgeTheIdle(windows);
        lapseTheSilent(windows);
        closeStandingCadences(windows);
    }

    @Transactional
    public void nudgeTheIdle(RhythmWindows windows) {
        Instant idleSince = clock.instant().minus(windows.idleBeforeNudge());

        for (WorkBracket bracket : brackets.awaitingTheirOneNudge(idleSince)) {
            try {
                BracketInvariants.oneNudgePerBracket(bracket, true);
                BracketInvariants.noNoticeAboutAClosedBracket(bracket);

                bracket.nudged(clock.instant());
                brackets.save(bracket);

                notices.stillGoing(bracket.closureRight(), bracket.id());
            } catch (RuntimeException failed) {
                LOG.warn("bracket {} could not be nudged: {}", bracket.id().value(), failed.getMessage());
            }
        }
    }

    @Transactional
    public void lapseTheSilent(RhythmWindows windows) {
        Instant nudgedBefore = clock.instant().minus(windows.lapseAfterNudge());

        for (WorkBracket bracket : brackets.readyToLapse(nudgedBefore)) {
            try {
                closing.lapsed(bracket.id());
            } catch (RuntimeException failed) {
                LOG.warn("bracket {} could not lapse: {}", bracket.id().value(), failed.getMessage());
            }
        }
    }

    public void closeStandingCadences(RhythmWindows windows) {
        for (Job job : jobs.recentlyTouchedOpenJobs(ENGAGEMENTS_PER_CADENCE_PASS)) {
            if (!job.standing() || job.isEnded()) {
                continue;
            }

            closeTheCadence(job.id(), windows);
        }
    }

    @Transactional
    public List<WorkBracket> closeTheCadence(JobId standingJob, RhythmWindows windows) {
        Instant openedBefore = clock.instant().minus(windows.standingCadence());

        List<WorkBracket> live = brackets.findLiveIn(standingJob);
        List<WorkBracket> closed = new ArrayList<>();

        for (WorkBracket bracket : live) {
            if (bracket.isBoundary() || bracket.openedAt().isAfter(openedBefore)) {
                continue;
            }

            try {
                closing.closedByCadence(bracket.id());
                closed.add(bracket);
            } catch (RuntimeException failed) {
                LOG.warn(
                        "bracket {} could not close on cadence: {}",
                        bracket.id().value(),
                        failed.getMessage());
            }
        }

        return closed;
    }
}
