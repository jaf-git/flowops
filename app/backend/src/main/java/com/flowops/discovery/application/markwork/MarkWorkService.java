package com.flowops.discovery.application.markwork;

import com.flowops.discovery.application.activities.ManageActivities;
import com.flowops.discovery.application.markintobracket.ComposeAddress;
import com.flowops.discovery.application.markintobracket.PlaceMarkInBracket;
import com.flowops.discovery.application.markmessage.MarkMessageUseCase;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.JoinIntentPort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.MarkVerb;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.MarkOutcome;
import com.flowops.discovery.domain.model.WorkBracket;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkWorkService implements MarkWorkUseCase {
    private final IdentifyCallerPort caller;
    private final MarkMessageUseCase marking;
    private final ComposeAddress addresses;
    private final PlaceMarkInBracket placing;
    private final WorkBracketPort brackets;
    private final JoinIntentPort joins;
    private final ManageActivities activities;
    private final Clock clock;

    public MarkWorkService(
            IdentifyCallerPort caller,
            MarkMessageUseCase marking,
            ComposeAddress addresses,
            PlaceMarkInBracket placing,
            WorkBracketPort brackets,
            JoinIntentPort joins,
            ManageActivities activities,
            Clock clock) {
        this.caller = caller;
        this.marking = marking;
        this.addresses = addresses;
        this.placing = placing;
        this.brackets = brackets;
        this.joins = joins;
        this.activities = activities;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Placed execute(MarkWork command) {
        Objects.requireNonNull(command.verb(), "R21.1 - the verb is the button, so there is always one");

        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);
        JobId job = JobId.of(command.jobId());

        WorkBracket joining = command.verb() == MarkVerb.JOIN ? theWorkBeingJoined(command, job) : null;

        UUID performer = joining == null ? command.performerId() : me;
        String workType =
                joining == null ? command.workType() : joining.address().workType();

        Direction direction = performer != null && !performer.equals(me) ? Direction.REQUEST : Direction.STANDALONE;

        MarkMessageUseCase.Marked marked = marking.execute(
                new MarkMessageUseCase.MarkMessage(command.messageId(), command.jobId(), direction, performer));

        BracketAddress address = addresses.composeFor(job, marked.conversationId(), performer, workType);

        refuseACreateThatWouldCollide(command.verb(), job, address);

        boolean corrected = workType != null && !workType.isBlank();
        MarkOutcome outcome = placing.place(job, address, marked.node(), me, corrected);

        UUID joinedWith = recordTheJoin(joining, job, outcome, me);

        String activity = nameTheWork(command.activityId(), marked.node().value(), me);

        return new Placed(
                marked.node(),
                outcome.bracket().id(),
                outcome instanceof MarkOutcome.Joined,
                outcome.destination(),
                address.workType(),
                activity,
                joinedWith);
    }

    private String nameTheWork(UUID activityId, UUID node, UUID me) {
        if (activityId == null) {
            return null;
        }
        return activities.used(activityId, node, me).name();
    }

    private void refuseACreateThatWouldCollide(MarkVerb verb, JobId job, BracketAddress address) {
        if (verb != MarkVerb.CREATE) {
            return;
        }

        Optional<WorkBracket> open = brackets.findOpenAt(job, address);
        if (open.isPresent()) {
            throw new ThatWorkIsAlreadyOpenException(open.get().id().value(), address.describe());
        }
    }

    private WorkBracket theWorkBeingJoined(MarkWork command, JobId job) {
        UUID named = Objects.requireNonNull(command.joining(), "R21.7 - a join names whose work it is joining");

        WorkBracket target = brackets.find(BracketId.of(named))
                .orElseThrow(() -> new IllegalArgumentException("there is no such work to join"));

        if (!target.jobId().equals(job)) {
            throw new IllegalArgumentException("R3.2 - that work belongs to a different engagement");
        }
        if (target.isBoundary()) {
            throw new IllegalArgumentException("that is the engagement itself rather than a piece of work");
        }
        if (target.state().isTerminal()) {
            throw new IllegalStateException("that work has already ended, so there is nothing to join");
        }

        return target;
    }

    private UUID recordTheJoin(WorkBracket joining, JobId job, MarkOutcome outcome, UUID by) {
        if (joining == null || joining.id().equals(outcome.bracket().id())) {
            return null;
        }

        joins.declared(job, outcome.bracket().id(), joining.id(), by, clock.instant());
        return joining.id().value();
    }
}
