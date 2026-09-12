package com.flowops.nodepipeline.domain.ai;

import java.util.List;

public final class Judgement {
    private Judgement() {}

    public enum PlugPoint {
        SAME_WORK,

        SAME_KIND,

        NAMING,

        /**
         * Writes guidance for a discovered template or process, for somebody who has just joined.
         *
         * <p>The other three select or order words that are already present. This one <b>composes</b>
         * — it is the first plug point that does, and it is therefore the first whose output is a
         * claim the product cannot substantiate from its own evidence. It exists because the owner
         * asked for it, and everything it produces is labelled as written by a model so that no
         * reader mistakes it for something the business said about itself.
         */
        EXPLAIN,

        /**
         * Judges whether an activity somebody is typing names the same work as one already in the
         * vocabulary, where the two share no characters — "post copy" against "caption set".
         *
         * <p>Character matching in the browser catches the typos and the plurals and costs nothing;
         * this catches only what it cannot. The answer is shown to the person as a question and is
         * never applied: a wrong merge makes two different activities one and the loss is silent
         * and permanent, where a missed suggestion costs one duplicate that anybody can merge later.
         */
        SAME_ACTIVITY
    }

    public enum SameWork {
        SAME,

        DIFFERENT,

        UNSURE
    }

    public record Question(PlugPoint plugPoint, String text, List<String> candidateIds, String context) {
        public Question {
            candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
        }
    }

    public record Verdict(String value, double confidence, String reason) {
        public java.util.Optional<SameWork> asSameWork() {
            try {
                return java.util.Optional.of(SameWork.valueOf(value));
            } catch (IllegalArgumentException unrecognised) {
                return java.util.Optional.empty();
            }
        }
    }
}
