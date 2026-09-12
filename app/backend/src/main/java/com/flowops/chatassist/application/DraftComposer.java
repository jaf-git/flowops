package com.flowops.chatassist.application;

import com.flowops.chatassist.domain.ConversationExtract;
import com.flowops.chatassist.domain.FieldSource;
import com.flowops.chatassist.domain.ProposedWork;
import com.flowops.chatassist.domain.ProposedWork.Sourced;
import com.flowops.chatassist.domain.WorkGroundingValidator;
import com.flowops.chatassist.domain.WorkShape;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DraftComposer {
    private static final int DAYS_BETWEEN_STEPS = 3;

    private static final int LONGEST_TITLE = 120;

    private final Clock clock;

    public DraftComposer(Clock clock) {
        this.clock = clock;
    }

    public ProposedWork compose(
            WorkGroundingValidator.Grounded grounded,
            ConversationExtract conversation,
            Map<String, UUID> speakers,
            UUID requester) {
        Instant now = clock.instant();
        boolean process = grounded.shape() == WorkShape.PROCESS;

        List<ProposedWork.Step> steps = new ArrayList<>();
        for (int at = 0; at < grounded.parts().size(); at++) {
            WorkGroundingValidator.GroundedPart part = grounded.parts().get(at);

            Sourced<UUID> owner = process ? whoDoesIt(part.ownerLabel(), speakers, requester) : null;
            Sourced<Instant> by = process ? Sourced.suggested(now.plus(spacing(at))) : null;

            steps.add(new ProposedWork.Step(Sourced.quoted(part.span()), part.span(), owner, by));
        }

        Sourced<UUID> holder = process
                ? new Sourced<>(requester, FieldSource.SUGGESTED)
                : whoDoesIt(firstOwnerLabel(grounded), speakers, requester);

        Sourced<Instant> when =
                Sourced.suggested(now.plus(spacing(grounded.parts().size() - 1)));

        return new ProposedWork(grounded.shape(), nameFor(conversation), holder, when, steps);
    }

    private Sourced<UUID> whoDoesIt(String label, Map<String, UUID> speakers, UUID requester) {
        UUID volunteered = label == null ? null : speakers.get(label.trim().toUpperCase(java.util.Locale.ROOT));
        return volunteered == null ? Sourced.suggested(requester) : Sourced.quoted(volunteered);
    }

    private static String firstOwnerLabel(WorkGroundingValidator.Grounded grounded) {
        return grounded.parts().isEmpty() ? null : grounded.parts().getFirst().ownerLabel();
    }

    private static Duration spacing(int index) {
        return Duration.ofDays((long) (Math.max(index, 0) + 1) * DAYS_BETWEEN_STEPS);
    }

    private static Sourced<String> nameFor(ConversationExtract conversation) {
        String opening = conversation.lines().isEmpty()
                ? ""
                : conversation.lines().getFirst().said().trim();
        String trimmed = opening.length() <= LONGEST_TITLE
                ? opening
                : opening.substring(0, LONGEST_TITLE).trim();
        return Sourced.quoted(trimmed);
    }
}
