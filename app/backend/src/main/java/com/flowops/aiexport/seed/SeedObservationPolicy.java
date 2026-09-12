package com.flowops.aiexport.seed;

import java.util.List;
import java.util.Map;

final class SeedObservationPolicy {
    private SeedObservationPolicy() {}

    private static final List<Integer> THREADS_CARRIED_TO_AN_ENDING = List.of(8, 3, 9, 7, 5, 4, 2, 2, 6, 2);

    private static final int THREADS_ENDED_UNANSWERED = 2;

    private static final Map<String, String> WHAT_THE_JOB_PRODUCES = Map.of(
            "Content writer",
            "TEXT",
            "Designer",
            "DESIGN",
            "Ads specialist",
            "REPORT",
            "Account manager",
            "SCHEDULING",
            SeedOrganisation.AGENCY_OWNER,
            "REPORT");

    enum Observation {
        CARRY_TO_AN_ENDING,

        END_IT_UNANSWERED,

        LEAVE_IT_RUNNING
    }

    static Observation whatBecomesOf(int conversationIndex, int round) {
        int carried = THREADS_CARRIED_TO_AN_ENDING.get(conversationIndex);
        if (round >= 1 && round <= carried) {
            return Observation.CARRY_TO_AN_ENDING;
        }
        if (round > carried && round <= carried + THREADS_ENDED_UNANSWERED) {
            return Observation.END_IT_UNANSWERED;
        }
        return Observation.LEAVE_IT_RUNNING;
    }

    static int threadsCarriedToAnEnding(int conversationIndex) {
        return THREADS_CARRIED_TO_AN_ENDING.get(conversationIndex);
    }

    static int conversationsThatCarryWork() {
        return THREADS_CARRIED_TO_AN_ENDING.size();
    }

    static String whatIsProducedBy(String statedJob) {
        String produced = WHAT_THE_JOB_PRODUCES.get(statedJob);
        if (produced == null) {
            throw new SeedFailedException("nothing says what a \"" + statedJob + "\" produces, so the work they"
                    + " finish would have to be given an output type nobody chose");
        }
        return produced;
    }

    static String whoKeptTheWorkWaiting(int conversationIndex, int round) {
        int turn = Math.floorMod(conversationIndex + round, 6);
        if (turn == 0) {
            return "CLIENT";
        }
        return turn == 3 ? "COLLEAGUE" : null;
    }
}
