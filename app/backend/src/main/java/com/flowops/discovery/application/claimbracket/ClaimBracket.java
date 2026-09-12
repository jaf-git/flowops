package com.flowops.discovery.application.claimbracket;

import com.flowops.discovery.application.closebracket.CloseBracket;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.BracketInvariants;
import com.flowops.discovery.domain.model.WorkBracket;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimBracket {
    private final WorkBracketPort brackets;
    private final CloseBracket closing;

    public ClaimBracket(WorkBracketPort brackets, CloseBracket closing) {
        this.brackets = brackets;
        this.closing = closing;
    }

    @Transactional
    public Claimed claim(BracketId id, UUID claimer) {
        Objects.requireNonNull(claimer, "R2.1 - a claim names who is taking the work on");

        WorkBracket unclaimed =
                brackets.find(id).orElseThrow(() -> new IllegalArgumentException("no bracket " + id.value()));

        if (!unclaimed.address().isUnclaimed()) {
            throw new WorkIsAlreadySomebodysException(id, unclaimed.address().performerId());
        }

        BracketAddress claimed = unclaimed.address().performedBy(claimer);
        Optional<WorkBracket> alreadyTheirs = brackets.findOpenAt(unclaimed.jobId(), claimed);

        if (alreadyTheirs.isPresent()) {
            return foldInto(unclaimed, alreadyTheirs.get());
        }

        unclaimed.claimedBy(claimer);
        brackets.save(unclaimed);

        BracketInvariants.assertAllOn(
                unclaimed, brackets.openWaitsHeldBy(unclaimed.id()).size());

        return new Claimed(unclaimed, null);
    }

    private Claimed foldInto(WorkBracket unclaimed, WorkBracket survivor) {
        brackets.mergeNodesInto(unclaimed.id(), survivor.id());

        closing.merged(unclaimed.id(), survivor);

        BracketInvariants.assertAllOn(
                survivor, brackets.openWaitsHeldBy(survivor.id()).size());

        return new Claimed(survivor, unclaimed.id());
    }

    public record Claimed(WorkBracket bracket, BracketId mergedFrom) {
        public boolean wasMerged() {
            return mergedFrom != null;
        }
    }
}
