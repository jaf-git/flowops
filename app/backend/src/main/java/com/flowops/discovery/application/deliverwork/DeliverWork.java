package com.flowops.discovery.application.deliverwork;

import com.flowops.discovery.application.closebracket.CloseBracket;
import com.flowops.discovery.application.closebracket.CloseOutcome;
import com.flowops.discovery.application.closejob.CloseJob;
import com.flowops.discovery.application.shared.exception.MessageNotMarkableException;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.MarkableMessagePort;
import com.flowops.discovery.application.shared.port.WorkStripPort;
import com.flowops.discovery.domain.enums.OutputKind;
import com.flowops.discovery.domain.model.BracketId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliverWork {
    private final IdentifyCallerPort caller;
    private final MarkableMessagePort messages;
    private final WorkStripPort strip;
    private final CloseBracket closing;
    private final CloseJob readiness;

    public DeliverWork(
            IdentifyCallerPort caller,
            MarkableMessagePort messages,
            WorkStripPort strip,
            CloseBracket closing,
            CloseJob readiness) {
        this.caller = caller;
        this.messages = messages;
        this.strip = strip;
        this.closing = closing;
        this.readiness = readiness;
    }

    @Transactional(readOnly = true)
    public Offer offerIn(UUID messageId) {
        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);
        return offer(conversationOf(messageId, me), me, messageId);
    }

    @Transactional
    public Delivered deliver(UUID messageId, UUID whichOne) {
        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);
        UUID conversation = conversationOf(messageId, me);

        Offer offer = offer(conversation, me, messageId);
        WorkStripPort.Closable chosen = theOneToClose(offer, whichOne);

        CloseOutcome outcome =
                closing.delivered(BracketId.of(chosen.bracketId()), OutputKind.MESSAGE_REF, messageId.toString(), me);

        readiness.reconsider(outcome.job());

        return new Delivered(
                chosen.bracketId(),
                chosen.destination(),
                messageId,
                outcome.released().size(),
                chosen.waitingHolderNames());
    }

    private UUID conversationOf(UUID messageId, UUID me) {
        return messages.read(messageId, me)
                .orElseThrow(MessageNotMarkableException::new)
                .conversationId();
    }

    private Offer offer(UUID conversation, UUID me, UUID messageId) {
        List<WorkStripPort.Closable> mine = new ArrayList<>();
        List<String> theirs = new ArrayList<>();
        java.util.Set<UUID> openedHere =
                messageId == null ? java.util.Set.of() : strip.liveBracketsStartedBy(messageId);

        for (WorkStripPort.Closable live : strip.liveWorkIn(conversation)) {
            if (openedHere.contains(live.bracketId())) {
                continue;
            }

            if (Objects.equals(live.closureRight(), me)) {
                mine.add(live);
            } else if (!theirs.contains(live.holderName())) {
                theirs.add(live.holderName());
            }
        }

        return new Offer(List.copyOf(mine), List.copyOf(theirs));
    }

    private WorkStripPort.Closable theOneToClose(Offer offer, UUID whichOne) {
        if (offer.mine().isEmpty()) {
            throw new NothingOfYoursIsOpenHereException(offer.heldByOthers());
        }

        if (whichOne == null) {
            if (offer.mine().size() > 1) {
                throw new SeveralPiecesOfWorkCouldBeDeliveredException(offer.mine().stream()
                        .map(WorkStripPort.Closable::bracketId)
                        .toList());
            }
            return offer.mine().getFirst();
        }

        return offer.mine().stream()
                .filter(candidate -> candidate.bracketId().equals(whichOne))
                .findFirst()
                .orElseThrow(() -> new NothingOfYoursIsOpenHereException(offer.heldByOthers()));
    }

    public record Offer(List<WorkStripPort.Closable> mine, List<String> heldByOthers) {}

    public record Delivered(UUID bracketId, String destination, UUID output, int released, List<String> unblocked) {}
}
