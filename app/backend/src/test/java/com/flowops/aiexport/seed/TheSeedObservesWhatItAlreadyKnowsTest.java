package com.flowops.aiexport.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.support.ApplicationTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("AI-EXPORT-SEED-DEMO-HISTORY-01")
class TheSeedObservesWhatItAlreadyKnowsTest extends ApplicationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the observing seed marks work, and some of it becomes a thread that ended")
    void theSeedMarksWorkAndClosesEnoughOfIt() {
        Walk walk = theWalkTheSeedWillTake();

        assertThat(walk.requestsMarked())
                .describedAs("Nothing is marked, so the graph is empty and every screen in the zone reads as"
                        + " broken rather than as new.")
                .isGreaterThan(0);
        assertThat(walk.threadsCarriedToAnEnding())
                .describedAs(
                        """
                        Nothing is carried to a close. Three of the fingerprint's six components need a closed \
                        thread, so the graph would accumulate, the clustering would have nothing to cluster and \
                        the types screen would have no rows — which is indistinguishable, to somebody watching, \
                        from the feature not working.""")
                .isGreaterThan(0);
        assertThat(walk.threadsEndedUnanswered())
                .describedAs(
                        """
                        No thread closes START_ONLY, so the exclusion that stops silence being read as speed \
                        has nothing to exclude and cannot be demonstrated.""")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("work is left running, because a canvas of nothing but closed lanes is a filing cabinet")
    void someWorkIsStillGoing() {
        Walk walk = theWalkTheSeedWillTake();

        assertThat(walk.requestsLeftRunning())
                .describedAs("Every marked request is closed one way or another. Work in flight is what the"
                        + " canvas is for, and a workspace with none of it reads as an archive.")
                .isGreaterThan(0);
        assertThat(walk.requestsLeftRunning())
                .describedAs("Almost everything is left running, so the closed half is a rounding error and"
                        + " nothing downstream has enough evidence to discover anything from.")
                .isLessThan(walk.requestsMarked());
    }

    @Test
    @DisplayName("both kinds of waiting happen, so invariant I4 has something to be computable over")
    void aClientsSilenceAndAColleaguesBothAppear() {
        Walk walk = theWalkTheSeedWillTake();

        assertThat(walk.waitsByWhoWasWaitedOn().keySet())
                .describedAs(
                        """
                        A client's silence and a colleague's are the same stretch of time and mean entirely \
                        different things — CLIENT and SUPPLIER open an EXTERNAL_WAIT row, COLLEAGUE and \
                        APPROVAL an INTERNAL_WAIT one. A workspace holding only one of the two cannot show \
                        that the product tells them apart, which is all invariant I4 is.""")
                .containsExactlyInAnyOrder("CLIENT", "COLLEAGUE");

        int waited = walk.waitsByWhoWasWaitedOn().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        assertThat(waited)
                .describedAs("Waiting is supposed to be a minority of the work. A workspace where most work"
                        + " waits on somebody describes an agency in trouble rather than one at work.")
                .isLessThan(walk.threadsCarriedToAnEnding() / 2);
    }

    @Test
    @DisplayName("at least two shapes clear the proposal floor and at least one deliberately sits below it")
    void theFloorHasSomethingAboveItAndSomethingBelowIt() {
        int toPropose = thresholdColumn("discovery_tracks_to_propose_type");
        int toBeACandidate = thresholdColumn("discovery_tracks_to_form_candidate");

        Map<String, Integer> threadsByShape = theWalkTheSeedWillTake().threadsByShape();

        List<String> proposing = new ArrayList<>();
        List<String> candidates = new ArrayList<>();
        List<String> silent = new ArrayList<>();
        threadsByShape.forEach((shape, threads) -> {
            if (threads >= toPropose) {
                proposing.add(shape + " × " + threads);
            } else if (threads >= toBeACandidate) {
                candidates.add(shape + " × " + threads);
            } else {
                silent.add(shape + " × " + threads);
            }
        });

        assertThat(proposing)
                .describedAs(
                        """
                        Fewer than two shapes reach %d completed threads, so the demonstration has nothing to \
                        show a proposal against and the whole of DISCOVERY-PROPOSE-TYPE-01 renders empty. \
                        Shapes as planned: %s""",
                        toPropose, threadsByShape)
                .hasSizeGreaterThanOrEqualTo(2);

        assertThat(candidates)
                .describedAs(
                        """
                        No shape sits between %d and %d, so nothing in the workspace demonstrates the product \
                        noticing a pattern and declining to discuss it. A workspace where every shape proposes \
                        teaches nothing about the floor. Shapes as planned: %s""",
                        toBeACandidate, toPropose, threadsByShape)
                .isNotEmpty();

        assertThat(silent)
                .describedAs(
                        """
                        Every shape reaches %d, so the lower floor is invisible too and there is no work the \
                        product holds silently. Shapes as planned: %s""",
                        toBeACandidate, threadsByShape)
                .isNotEmpty();
    }

    @Test
    @DisplayName("the calibration is spent inside the history rather than past the end of it")
    void everyBudgetedThreadHasARequestToBeMadeOf() {
        Walk walk = theWalkTheSeedWillTake();
        int rounds = SeedConversations.howManyRequestsAreMade() / SeedObservationPolicy.conversationsThatCarryWork();

        List<String> unmet = new ArrayList<>();
        for (int conversation = 0; conversation < SeedObservationPolicy.conversationsThatCarryWork(); conversation++) {
            int wanted = SeedObservationPolicy.threadsCarriedToAnEnding(conversation);
            if (wanted > rounds - 1) {
                unmet.add("conversation %d wants %d threads and the history gives it %d requests"
                        .formatted(conversation, wanted, rounds - 1));
            }
        }

        assertThat(unmet)
                .describedAs("A budget larger than the conversation is a budget silently unmet: the shape comes"
                        + " out short of its floor and nothing anywhere says why.")
                .isEmpty();
        assertThat(walk.threadsCarriedToAnEnding())
                .isEqualTo(walk.threadsByShape().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum());
    }

    @Test
    @DisplayName("every job the seed says produces something is a job this workspace's vocabulary holds")
    void theOutputVocabularyAndTheJobVocabularyAgree() {
        List<String> statedJobs = jdbc.queryForList("select name from functional_role", String.class);

        List<String> unknown = new ArrayList<>();
        for (String pair : SeedConversations.theJobsEachWorkingConversationRunsBetween()) {
            for (String job : pair.split(" -> ")) {
                if (!statedJobs.contains(job)) {
                    unknown.add(job);
                }
            }
        }

        assertThat(unknown)
                .describedAs(
                        """
                        The seed hands work between jobs this workspace does not have, so SeedOrganisation \
                        would refuse before anything was marked. The vocabulary V64 seeds is %s.""",
                        statedJobs)
                .isEmpty();

        for (String pair : SeedConversations.theJobsEachWorkingConversationRunsBetween()) {
            String doer = pair.split(" -> ")[1];
            assertThat(SeedObservationPolicy.whatIsProducedBy(doer))
                    .describedAs(
                            "Nothing says what a %s produces, so their finished work would need an output"
                                    + " type nobody chose — and the output type is the strongest of the six"
                                    + " fingerprint components.",
                            doer)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("the corpus both zones observe is still the 656 messages the .http files name")
    void theTwoZonesStillSeeTheSameMessages() {
        assertThat(SeedConversations.howManyMessagesAreSaid())
                .describedAs(
                        """
                        http/discovery.http tells a reader this zone is demonstrated against 656 messages, and \
                        the comparison the feature rests on is that the application and Discovery saw the same \
                        ones. A sentence added or removed here leaves that claim false with nothing saying so.""")
                .isEqualTo(656);
    }

    private Walk theWalkTheSeedWillTake() {
        List<String> jobPairs = SeedConversations.theJobsEachWorkingConversationRunsBetween();
        int conversations = SeedObservationPolicy.conversationsThatCarryWork();

        int marked = 0;
        int carried = 0;
        int unanswered = 0;
        int running = 0;
        Map<String, Integer> byShape = new LinkedHashMap<>();
        Map<String, Integer> waits = new TreeMap<>();

        for (int request = 0; request < SeedConversations.howManyRequestsAreMade(); request++) {
            int conversation = request % conversations;
            int round = request / conversations;
            if (round == 0) {
                continue;
            }
            marked++;

            switch (SeedObservationPolicy.whatBecomesOf(conversation, round)) {
                case CARRY_TO_AN_ENDING -> {
                    carried++;
                    byShape.merge(shapeOf(jobPairs.get(conversation)), 1, Integer::sum);
                    String waitingOn = SeedObservationPolicy.whoKeptTheWorkWaiting(conversation, round);
                    if (waitingOn != null) {
                        waits.merge(waitingOn, 1, Integer::sum);
                    }
                }
                case END_IT_UNANSWERED -> unanswered++;
                case LEAVE_IT_RUNNING -> running++;
                default -> throw new IllegalStateException("an observation this test does not know about");
            }
        }
        return new Walk(marked, carried, unanswered, running, byShape, waits);
    }

    private static String shapeOf(String jobPair) {
        String doer = jobPair.split(" -> ")[1];
        return jobPair + " producing " + SeedObservationPolicy.whatIsProducedBy(doer);
    }

    private record Walk(
            int requestsMarked,
            int threadsCarriedToAnEnding,
            int threadsEndedUnanswered,
            int requestsLeftRunning,
            Map<String, Integer> threadsByShape,
            Map<String, Integer> waitsByWhoWasWaitedOn) {}

    private int thresholdColumn(String column) {
        String stated = jdbc.queryForObject(
                """
                select column_default
                from information_schema.columns
                where table_name = 'workspace_settings' and column_name = ?
                """,
                String.class,
                column);
        assertThat(stated)
                .describedAs(
                        "workspace_settings.%s has no default, so a fresh workspace has no floor and this"
                                + " test has nothing to calibrate the seed against.",
                        column)
                .isNotNull();
        return Integer.parseInt(stated.replaceAll("[^0-9]", ""));
    }
}
