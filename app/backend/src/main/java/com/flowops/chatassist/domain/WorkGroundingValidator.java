package com.flowops.chatassist.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class WorkGroundingValidator {
    private static final double MOST_THAT_MAY_DROP = 0.5;

    private static final int FEWEST_STEPS = 3;

    private static final int FEWEST_CHECKLIST_ITEMS = 2;

    private WorkGroundingValidator() {}

    public record Grounded(WorkShape shape, List<GroundedPart> parts) {
        public boolean isEmpty() {
            return parts.isEmpty();
        }
    }

    public record GroundedPart(String span, String ownerLabel) {}

    public static Optional<Grounded> check(WorkOpinion opinion, ConversationExtract conversation) {
        if (opinion == null || opinion.shape() == WorkShape.NOTHING || conversation == null || conversation.isEmpty()) {
            return Optional.empty();
        }

        List<GroundedPart> grounded = new ArrayList<>();
        Set<String> alreadyUsed = new LinkedHashSet<>();
        int proposed = opinion.parts().size();

        for (WorkOpinion.Part part : opinion.parts()) {
            Optional<String> span = conversation.spanFor(part.phrase());
            if (span.isEmpty()) {
                continue;
            }

            if (!alreadyUsed.add(span.get().toLowerCase(Locale.ROOT))) {
                continue;
            }
            grounded.add(new GroundedPart(span.get(), part.ownerLabel()));
        }

        int floor = opinion.shape() == WorkShape.PROCESS ? FEWEST_STEPS : FEWEST_CHECKLIST_ITEMS;
        if (proposed == 0 || grounded.size() < floor) {
            return Optional.empty();
        }
        double dropped = (double) (proposed - grounded.size()) / proposed;
        if (dropped > MOST_THAT_MAY_DROP) {
            return Optional.empty();
        }
        return Optional.of(new Grounded(opinion.shape(), List.copyOf(grounded)));
    }
}
