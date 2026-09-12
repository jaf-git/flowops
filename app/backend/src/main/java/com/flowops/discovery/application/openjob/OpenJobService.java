package com.flowops.discovery.application.openjob;

import com.flowops.discovery.application.markintobracket.ComposeAddress;
import com.flowops.discovery.application.shared.WorkCapture;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.enums.NodeRole;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.WorkBracket;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenJobService implements OpenJobUseCase {
    private static final java.time.Duration DUPLICATE_WINDOW = java.time.Duration.ofDays(14);

    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final WorkCapture capture;
    private final ComposeAddress addresses;
    private final WorkBracketPort brackets;
    private final Clock clock;

    public OpenJobService(
            IdentifyCallerPort caller,
            WorkGraphPort graph,
            WorkCapture capture,
            ComposeAddress addresses,
            WorkBracketPort brackets,
            Clock clock) {
        this.caller = caller;
        this.graph = graph;
        this.capture = capture;
        this.addresses = addresses;
        this.brackets = brackets;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Opened execute(OpenJob command) {
        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        Instant now = clock.instant();

        if (!command.evenThoughOneIsOpen()) {
            graph.openJobForCounterpartySince(command.counterpartyId(), now.minus(DUPLICATE_WINDOW))
                    .ifPresent(open -> {
                        throw new AnEngagementForThisClientIsAlreadyOpenException(open.id(), open.name());
                    });
        }

        Job job = Job.opened(graph.nextJobId(), command.jobName(), me, now);

        job.tracks(command.projectLabel());

        if (command.counterpartyId() != null) {
            job.belongsTo(command.counterpartyId());
        }

        if (command.reworkOfJobId() != null) {
            job.isReworkOf(JobId.of(command.reworkOfJobId()));
        }

        graph.save(job);

        WorkCapture.Captured captured =
                capture.capture(command.messageId(), me, job.id(), Direction.STANDALONE, NodeKind.JOB_START, null);

        openTheBoundary(job, captured, me, now);

        return new Opened(
                job.id(), captured.node().id(), captured.threadedInto().map(Track::id));
    }

    private void openTheBoundary(Job job, WorkCapture.Captured captured, UUID owner, Instant now) {
        BracketAddress address = addresses.composeFor(job.id(), captured.conversationId(), owner);

        WorkBracket boundary = WorkBracket.boundary(
                brackets.nextBracketId(), job.id(), address, captured.node().id(), owner, now);
        brackets.save(boundary);

        brackets.placeNode(captured.node().id(), boundary.id(), NodeRole.START, address, owner, null);
    }
}
