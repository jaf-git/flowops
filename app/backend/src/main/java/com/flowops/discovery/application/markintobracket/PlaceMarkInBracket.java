package com.flowops.discovery.application.markintobracket;

import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.enums.NodeRole;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.BracketInvariants;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.MarkOutcome;
import com.flowops.discovery.domain.model.ParentRule;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceMarkInBracket {
    private final WorkBracketPort brackets;
    private final Clock clock;

    public PlaceMarkInBracket(WorkBracketPort brackets, Clock clock) {
        this.brackets = brackets;
        this.clock = clock;
    }

    @Transactional
    public MarkOutcome place(JobId job, BracketAddress address, WorkNodeId node, UUID marker) {
        return place(job, address, node, marker, false);
    }

    @Transactional
    public MarkOutcome place(
            JobId job, BracketAddress address, WorkNodeId node, UUID marker, boolean workTypeWasCorrected) {
        Objects.requireNonNull(job, "a mark belongs to an engagement");
        Objects.requireNonNull(address, "R1 - the address decides, so there is always one");
        Objects.requireNonNull(node, "a mark places a node");
        Objects.requireNonNull(marker, "R5.2 - somebody clicked, and they hold the obligation");

        Instant now = clock.instant();

        Optional<WorkBracket> existing = brackets.findOpenAt(job, address);

        List<WorkNodeId> theirPreviousWork = address.performer()
                .map(p -> brackets.chainOf(job, p).stream()
                        .filter(earlier -> !earlier.equals(node))
                        .toList())
                .orElse(List.of());

        WorkNodeId candidate = ParentRule.parentOf(
                null, theirPreviousWork, brackets.rootNodeOf(job).orElse(node));

        WorkNodeId parent = candidate.equals(node) ? null : candidate;

        if (existing.isPresent()) {
            WorkBracket bracket = existing.get();

            bracket.touched(now);
            brackets.save(bracket);

            brackets.placeNode(node, bracket.id(), NodeRole.WORK, address, marker, parent);

            BracketInvariants.assertAllOn(
                    bracket, brackets.openWaitsHeldBy(bracket.id()).size());

            return new MarkOutcome.Joined(bracket);
        }

        UUID holder = address.performer().orElse(marker);

        BracketId id = brackets.nextBracketId();
        WorkBracket opened = WorkBracket.opened(id, job, address, node, holder, now);

        if (workTypeWasCorrected) {
            opened.workTypeWasCorrected();
        }

        brackets.save(opened);

        brackets.placeNode(node, opened.id(), NodeRole.START, address, marker, parent);

        BracketInvariants.assertAllOn(opened, 0);

        return new MarkOutcome.Opened(opened, whyItOpened(job, address));
    }

    private MarkOutcome.OpeningReason whyItOpened(JobId job, BracketAddress address) {
        var live = brackets.findLiveIn(job);

        if (live.isEmpty()) {
            return MarkOutcome.OpeningReason.NEW_JOB;
        }

        boolean sameWorkTypeSeen =
                live.stream().anyMatch(b -> b.address().workType().equals(address.workType()));

        if (!sameWorkTypeSeen) {
            return MarkOutcome.OpeningReason.NEW_WORK_TYPE;
        }

        boolean sameConversationSeen = live.stream()
                .anyMatch(b -> b.address().workType().equals(address.workType())
                        && b.address().conversationId().equals(address.conversationId()));

        if (!sameConversationSeen) {
            return MarkOutcome.OpeningReason.DIFFERENT_CHAT;
        }

        boolean samePerformerSeen = live.stream()
                .anyMatch(b -> b.address().workType().equals(address.workType())
                        && b.address().conversationId().equals(address.conversationId())
                        && Objects.equals(b.address().performerId(), address.performerId()));

        if (!samePerformerSeen) {
            return MarkOutcome.OpeningReason.NEW_PERFORMER;
        }

        return MarkOutcome.OpeningReason.UNEXPECTED;
    }
}
