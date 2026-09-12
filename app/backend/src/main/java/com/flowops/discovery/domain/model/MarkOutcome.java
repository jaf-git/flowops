package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.NodeRole;
import java.util.Objects;

public sealed interface MarkOutcome {
    WorkBracket bracket();

    NodeRole role();

    String destination();

    record Joined(WorkBracket bracket) implements MarkOutcome {
        public Joined {
            Objects.requireNonNull(bracket, "a join names what it joined");
        }

        @Override
        public NodeRole role() {
            return NodeRole.WORK;
        }

        @Override
        public String destination() {
            return "adding to · " + bracket.address().describe();
        }
    }

    record Opened(WorkBracket bracket, OpeningReason because) implements MarkOutcome {
        public Opened {
            Objects.requireNonNull(bracket, "an opening names what it opened");
            Objects.requireNonNull(because, "R1.2 - an opening says which of the four cases it was");
        }

        @Override
        public NodeRole role() {
            return NodeRole.START;
        }

        @Override
        public String destination() {
            return "starting · " + bracket.address().describe();
        }
    }

    enum OpeningReason {
        NEW_JOB,

        NEW_WORK_TYPE,

        NEW_PERFORMER,

        DIFFERENT_CHAT,

        UNEXPECTED
    }
}
