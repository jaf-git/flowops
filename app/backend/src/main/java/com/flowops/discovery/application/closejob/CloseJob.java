package com.flowops.discovery.application.closejob;

import com.flowops.discovery.application.closebracket.CloseBracket;
import com.flowops.discovery.application.shared.port.DiscoveryNoticePort;
import com.flowops.discovery.application.shared.port.PersonRolePort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.WorkBracket;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloseJob {
    private final WorkBracketPort brackets;
    private final CloseBracket closingBrackets;
    private final WorkGraphPort jobs;
    private final DiscoveryNoticePort notices;
    private final PersonRolePort people;
    private final Clock clock;

    public CloseJob(
            WorkBracketPort brackets,
            CloseBracket closingBrackets,
            WorkGraphPort jobs,
            DiscoveryNoticePort notices,
            PersonRolePort people,
            Clock clock) {
        this.brackets = brackets;
        this.closingBrackets = closingBrackets;
        this.jobs = jobs;
        this.notices = notices;
        this.people = people;
        this.clock = clock;
    }

    @Transactional
    public Readiness reconsider(JobId id) {
        Job job = mustFind(id);
        List<WorkBracket> live = brackets.liveWorkIn(id);

        if (job.isEnded()) {
            return new Readiness(false, live.size(), job.status());
        }

        if (live.isEmpty() && job.status() != Job.Status.READY_TO_CLOSE) {
            job.readyToClose();
            jobs.save(job);
        } else if (!live.isEmpty() && job.status() == Job.Status.READY_TO_CLOSE) {
            job.reopened(clock.instant());
            jobs.save(job);
        }

        return new Readiness(live.isEmpty(), live.size(), job.status());
    }

    @Transactional
    public Ended close(JobId id, UUID by) {
        Job job = mustFind(id);
        refuseIfEnded(job);

        List<WorkBracket> live = brackets.liveWorkIn(id);
        if (!live.isEmpty()) {
            throw new JobStillHasLiveWorkException(id, live.size());
        }

        Instant now = clock.instant();
        endTheBoundary(job, by, now);

        job.closed(now);
        jobs.save(job);

        return new Ended(id, job.status(), job.shapeEligible(), List.of(), null);
    }

    @Transactional
    public Ended autoClose(JobId id) {
        Job job = mustFind(id);
        refuseIfEnded(job);

        Instant now = clock.instant();
        endTheBoundary(job, job.openedBy(), now);

        job.autoClosed(now);
        jobs.save(job);

        return new Ended(id, job.status(), job.shapeEligible(), List.of(), null);
    }

    @Transactional
    public Ended forceClose(JobId id, UUID owner, String reason) {
        Job job = mustFind(id);
        refuseIfEnded(job);

        if (!job.openedBy().equals(owner) && !people.ownsTheWorkspace(owner)) {
            throw new OnlyTheOwnerMayForceCloseException(id, owner);
        }

        Instant now = clock.instant();

        Set<UUID> toTell = new LinkedHashSet<>();
        List<UUID> terminalised = new ArrayList<>();

        for (WorkBracket live : brackets.liveWorkIn(id)) {
            toTell.add(live.closureRight());
            closingBrackets.terminalisedByJobEnding(live.id());
            terminalised.add(live.id().value());
        }

        toTell.remove(owner);

        endTheBoundary(job, owner, now);

        job.forceClosed(now, reason);
        jobs.save(job);

        for (UUID holder : toTell) {
            notices.jobForceClosed(holder, id);
        }

        return new Ended(id, job.status(), job.shapeEligible(), List.copyOf(toTell), terminalised);
    }

    private void endTheBoundary(Job job, UUID by, Instant now) {
        brackets.boundaryOf(job.id()).ifPresent(boundary -> {
            boundary.jobEnded(brackets.appendEndNode(boundary, by, now), now);
            brackets.save(boundary);
        });
    }

    private void refuseIfEnded(Job job) {
        if (job.isEnded()) {
            throw new JobAlreadyEndedException(job.id(), job.status());
        }
    }

    private Job mustFind(JobId id) {
        return jobs.findJob(id).orElseThrow(() -> new IllegalArgumentException("no job " + id.value()));
    }

    public record Readiness(boolean ready, int liveWork, Job.Status status) {}

    public record Ended(
            JobId job, Job.Status status, boolean shapeEligible, List<UUID> tellThem, List<UUID> terminalised) {
        public Ended {
            Objects.requireNonNull(job, "an ending belongs to an engagement");
        }
    }
}
