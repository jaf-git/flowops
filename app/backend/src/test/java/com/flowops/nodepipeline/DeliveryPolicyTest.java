package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.MatchTier;
import com.flowops.nodepipeline.domain.job.JobTier;
import com.flowops.nodepipeline.domain.notify.Audience;
import com.flowops.nodepipeline.domain.notify.DeliveryPolicy;
import com.flowops.nodepipeline.domain.notify.DigestDiff;
import com.flowops.nodepipeline.domain.notify.DiscoveryFinding;
import com.flowops.nodepipeline.domain.notify.JobFinding;
import com.flowops.nodepipeline.domain.notify.MessageKind;
import com.flowops.nodepipeline.domain.notify.NodeFinding;
import com.flowops.nodepipeline.domain.notify.NoticeBudget;
import com.flowops.nodepipeline.domain.notify.NudgeHistory;
import com.flowops.nodepipeline.domain.notify.PipelineMessage;
import com.flowops.nodepipeline.domain.notify.Recipient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryPolicyTest {
    private static final DeliveryPolicy POLICY = new DeliveryPolicy(NoticeBudget.reference());

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @Test
    void oneHundredAndEightyNodesProduceAtMostThreeOwnerItemsAndTwelveMessages() {
        Week week = aWeekOf180Nodes();

        List<PipelineMessage> proposed =
                POLICY.propose(week.nodes, week.jobs, week.discoveries, week.audience, week.history);
        List<PipelineMessage> sending = POLICY.capPerPerson(proposed);

        List<PipelineMessage> ownerItems =
                sending.stream().filter(m -> m.kind().forTheOwner()).toList();
        List<PipelineMessage> individual =
                sending.stream().filter(m -> !m.kind().forTheOwner()).toList();

        assertThat(ownerItems)
                .as("P10: at most three items reach the one person who can write a template")
                .hasSizeLessThanOrEqualTo(3);
        assertThat(individual)
                .as("P10: at most twelve messages reach everybody else, from 180 nodes")
                .hasSizeLessThanOrEqualTo(12);

        assertThat(sending)
                .as("silence is a valid output, but a week with this much in it is not silent")
                .isNotEmpty();

        Set<UUID> reachable = week.audience.everybodyAbleToAct();
        assertThat(sending).as("nobody is told anything they cannot act on").allSatisfy(m -> assertThat(reachable)
                .contains(m.to().person()));
    }

    @Test
    void anAbstentionNeverNotifiesAnybody() {
        NodeFinding abstained = node("n1", "j1", null, MatchTier.ABSTAIN, 0.0, "veto:text_floor");
        NodeFinding didItRight = node("n2", "j1", "T-CAPS", MatchTier.OK, 0.91, "ran_template");

        List<PipelineMessage> messages = POLICY.propose(
                List.of(abstained, didItRight),
                List.of(),
                List.of(),
                audienceFor(List.of("n1", "n2"), "j1"),
                NudgeHistory.none());

        assertThat(messages)
                .as("an abstention has nothing to say and doing the right thing is not news")
                .isEmpty();
    }

    @Test
    void aNudgeGoesToWhoeverMarkedTheWorkAndNeverToWhoeverSpoke() {
        UUID ines = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID sara = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

        Audience audience = new Audience(
                UUID.randomUUID(), OWNER, Map.of("j1", sara), Map.of("n1", ines), Set.of(OWNER, ines, sara));

        List<PipelineMessage> messages = POLICY.propose(
                List.of(node("n1", "j1", "T-CAPS", MatchTier.NUDGE, 0.88, "text 0.82")),
                List.of(),
                List.of(),
                audience,
                NudgeHistory.none());

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).to().person())
                .as("Sara writes the copy and Ines marks it; Ines gets the nudge")
                .isEqualTo(ines);
        assertThat(messages.get(0).to().standing()).isEqualTo(Recipient.Standing.MARKER);
    }

    @Test
    void aNodeAlreadyNudgedIsNeverNudgedAgain() {
        NudgeHistory nudgedOnFriday = new NudgeHistory(Set.of("n1"), List.of());

        List<PipelineMessage> messages = POLICY.propose(
                List.of(node("n1", "j1", "T-CAPS", MatchTier.NUDGE, 0.88, "text 0.82")),
                List.of(),
                List.of(),
                audienceFor(List.of("n1"), "j1"),
                nudgedOnFriday);

        assertThat(messages)
                .as("a second nudge teaches people to ignore the first")
                .isEmpty();
    }

    @Test
    void aStaleEngagementGetsAStallNoticeAndItsPeopleGetNothing() {
        UUID ines = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID marius = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

        Audience audience = new Audience(
                UUID.randomUUID(), OWNER, Map.of("j1", marius), Map.of("n1", ines), Set.of(OWNER, ines, marius));

        List<PipelineMessage> messages = POLICY.propose(
                List.of(node("n1", "j1", "T-CAPS", MatchTier.NUDGE, 0.88, "text 0.82")),
                List.of(job("j1", JobTier.STALE, null, 0.0, "stale: open, nothing for 31 days")),
                List.of(),
                audience,
                NudgeHistory.none());

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).kind()).isEqualTo(MessageKind.THE_ENGAGEMENT_HAS_STALLED);
        assertThat(messages.get(0).to().person())
                .as("the stall goes to whoever opened the engagement, who is the only owner there is")
                .isEqualTo(marius);
        assertThat(messages)
                .as("telling Ines to use a template for work that died a month ago is worse than silence")
                .noneSatisfy(m -> assertThat(m.to().person()).isEqualTo(ines));
    }

    @Test
    void aRoleMismatchReachesTheEngagementOwnerAsAStaffingNoteAndNeverThePerson() {
        UUID ines = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID marius = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

        Audience audience = new Audience(
                UUID.randomUUID(), OWNER, Map.of("j1", marius), Map.of("n1", ines), Set.of(OWNER, ines, marius));

        List<PipelineMessage> messages = POLICY.propose(
                List.of(node("n1", "j1", "T-SHOOT", MatchTier.ROLE_MISMATCH, 0.79, "text 0.77, role disagrees")),
                List.of(),
                List.of(),
                audience,
                NudgeHistory.none());

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).kind()).isEqualTo(MessageKind.STAFFING_NOTE);
        assertThat(messages.get(0).to().person()).isEqualTo(marius);
    }

    @Test
    void theThirdTimeSomebodyDoesTheSameWorkByHandItBecomesOneHabitSignal() {
        UUID ines = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        Audience audience = new Audience(
                UUID.randomUUID(),
                OWNER,
                Map.of("j1", OWNER),
                Map.of("n1", ines, "n2", ines, "n3", ines, "n4", ines),
                Set.of(OWNER, ines));

        List<NodeFinding> four = List.of(
                node("n1", "j1", "T-CAPS", MatchTier.NUDGE, 0.88, "text 0.82"),
                node("n2", "j1", "T-CAPS", MatchTier.NUDGE, 0.87, "text 0.81"),
                node("n3", "j1", "T-CAPS", MatchTier.NUDGE, 0.86, "text 0.80"),
                node("n4", "j1", "T-CAPS", MatchTier.NUDGE, 0.85, "text 0.79"));

        List<PipelineMessage> messages = POLICY.propose(four, List.of(), List.of(), audience, NudgeHistory.none());

        assertThat(messages)
                .as("four instances of one habit are one message, not four")
                .hasSize(1);
        assertThat(messages.get(0).kind()).isEqualTo(MessageKind.A_HABIT_WORTH_A_TEMPLATE);
        assertThat(messages.get(0).references())
                .as("and it carries every node it stands for, so the claim is checkable")
                .containsExactlyInAnyOrder("n1", "n2", "n3", "n4");
    }

    @Test
    void oncePastTheHabitThresholdThePairIsSilentForGood() {
        UUID ines = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        Audience audience =
                new Audience(UUID.randomUUID(), OWNER, Map.of("j1", OWNER), Map.of("n9", ines), Set.of(OWNER, ines));

        NudgeHistory spokenAboutThreeTimes = new NudgeHistory(
                Set.of(),
                List.of(
                        new NudgeHistory.PriorNudge(ines, "T-CAPS"),
                        new NudgeHistory.PriorNudge(ines, "T-CAPS"),
                        new NudgeHistory.PriorNudge(ines, "T-CAPS")));

        List<PipelineMessage> messages = POLICY.propose(
                List.of(node("n9", "j1", "T-CAPS", MatchTier.NUDGE, 0.88, "text 0.82")),
                List.of(),
                List.of(),
                audience,
                spokenAboutThreeTimes);

        assertThat(messages)
                .as("a fourth message about a pattern somebody has already declined to change is noise")
                .isEmpty();
    }

    @Test
    void nobodyGetsMoreThanThreeInOneDigestAndTheThreeAreTheStrongest() {
        UUID ines = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        List<PipelineMessage> five = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            five.add(new PipelineMessage(
                    new Recipient(ines, Recipient.Standing.MARKER),
                    MessageKind.WORK_COULD_HAVE_BEEN_FASTER,
                    "j" + i,
                    "text 0." + (50 + i * 5),
                    0.50 + i * 0.05,
                    List.of(new PipelineMessage.Evidence(UUID.randomUUID(), "NODE_MATCH|n" + i + "|T-CAPS", "n" + i))));
        }

        List<PipelineMessage> capped = POLICY.capPerPerson(five);

        assertThat(capped).hasSize(3);
        assertThat(capped)
                .extracting(PipelineMessage::score)
                .as("the strongest three, not the first three the loop happened to reach")
                .containsExactly(0.75, 0.70, 0.65);
    }

    @Test
    void aPersonWhoHasLeftTheWorkspaceIsNotAnAddress() {
        UUID departed = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
        Audience audience =
                new Audience(UUID.randomUUID(), OWNER, Map.of("j1", departed), Map.of("n1", departed), Set.of(OWNER));

        List<PipelineMessage> messages = POLICY.propose(
                List.of(node("n1", "j1", "T-CAPS", MatchTier.NUDGE, 0.88, "text 0.82")),
                List.of(job("j1", JobTier.STALE, null, 0.0, "stale: open, nothing for 31 days")),
                List.of(),
                audience,
                NudgeHistory.none());

        assertThat(messages).isEmpty();
    }

    @Test
    void aDecisionWhoseWorkNoLongerResolvesToAnEngagementSaysNothing() {
        UUID ines = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        Audience audience = new Audience(UUID.randomUUID(), OWNER, Map.of(), Map.of("n1", ines), Set.of(OWNER, ines));

        List<PipelineMessage> messages = POLICY.propose(
                List.of(node("n1", null, "T-CAPS", MatchTier.NUDGE, 0.88, "text 0.82")),
                List.of(),
                List.of(),
                audience,
                NudgeHistory.none());

        assertThat(messages)
                .as("dropped here rather than raising halfway through somebody else's digest")
                .isEmpty();
    }

    @Test
    void aDigestThatWouldRepeatLastWeeksMessageSendsNothing() {
        UUID ines = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        PipelineMessage sameAsLastWeek = new PipelineMessage(
                new Recipient(ines, Recipient.Standing.MARKER),
                MessageKind.WORK_COULD_HAVE_BEEN_FASTER,
                "j1",
                "text 0.82",
                0.88,
                List.of(new PipelineMessage.Evidence(UUID.randomUUID(), "NODE_MATCH|n1|T-CAPS", "n1")));

        DigestDiff.Diff diff = DigestDiff.since(Set.of("NODE_MATCH|n1|T-CAPS"), List.of(sameAsLastWeek));

        assertThat(diff.fresh())
                .as("a digest that says what the last one said is a digest nobody opens")
                .isEmpty();
        assertThat(diff.repeated()).hasSize(1);
    }

    @Test
    void aDigestSaysWhatChangedRatherThanTheAccumulatedList() {
        UUID ines = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        Recipient to = new Recipient(ines, Recipient.Standing.MARKER);

        PipelineMessage old = new PipelineMessage(
                to,
                MessageKind.WORK_COULD_HAVE_BEEN_FASTER,
                "j1",
                "text 0.82",
                0.88,
                List.of(new PipelineMessage.Evidence(UUID.randomUUID(), "NODE_MATCH|n1|T-CAPS", "n1")));
        PipelineMessage fresh = new PipelineMessage(
                to,
                MessageKind.WORK_COULD_HAVE_BEEN_FASTER,
                "j2",
                "text 0.79",
                0.84,
                List.of(new PipelineMessage.Evidence(UUID.randomUUID(), "NODE_MATCH|n7|T-CAPS", "n7")));

        DigestDiff.Diff diff =
                DigestDiff.since(Set.of("NODE_MATCH|n1|T-CAPS", "JOB_MATCH|j9|P-LAUNCH"), List.of(old, fresh));

        assertThat(diff.fresh()).containsExactly(fresh);
        assertThat(diff.settled())
                .as("what has gone since last week is part of what changed")
                .containsExactly("JOB_MATCH|j9|P-LAUNCH");
    }

    @Test
    void aMessageWithoutItsReasonCannotExist() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PipelineMessage(
                        new Recipient(UUID.randomUUID(), Recipient.Standing.MARKER),
                        MessageKind.WORK_COULD_HAVE_BEEN_FASTER,
                        "j1",
                        "  ",
                        0.88,
                        List.of(new PipelineMessage.Evidence(UUID.randomUUID(), "NODE_MATCH|n1|T-CAPS", "n1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never a bare score");
    }

    private record Week(
            List<NodeFinding> nodes,
            List<JobFinding> jobs,
            List<DiscoveryFinding> discoveries,
            Audience audience,
            NudgeHistory history) {}

    private static Week aWeekOf180Nodes() {
        List<String> people = List.of(
                "00000000-0000-0000-0000-0000000000a1",
                "00000000-0000-0000-0000-0000000000a2",
                "00000000-0000-0000-0000-0000000000a3",
                "00000000-0000-0000-0000-0000000000a4",
                "00000000-0000-0000-0000-0000000000a5",
                "00000000-0000-0000-0000-0000000000a6");
        List<String> templates = List.of("T-CAPS", "T-SHOOT", "T-BRIEF", "T-SCHED");

        List<NodeFinding> nodes = new ArrayList<>();
        Map<String, UUID> markers = new LinkedHashMap<>();
        Set<UUID> ableToAct = new LinkedHashSet<>();
        ableToAct.add(OWNER);
        people.forEach(p -> ableToAct.add(UUID.fromString(p)));

        for (int i = 0; i < 180; i++) {
            String nodeId = "n" + i;
            String jobId = "j" + (i % 12);
            UUID marker = UUID.fromString(people.get(i % people.size()));
            markers.put(nodeId, marker);

            MatchTier tier;
            if (i % 5 == 0 && i % 15 != 0) {
                tier = MatchTier.NUDGE;
            } else if (i % 45 == 0) {
                tier = MatchTier.ADJUST;
            } else if (i % 60 == 7) {
                tier = MatchTier.ROLE_MISMATCH;
            } else if (i % 13 == 0) {
                tier = MatchTier.OK;
            } else {
                tier = MatchTier.ABSTAIN;
            }

            String template = tier == MatchTier.ABSTAIN ? null : templates.get(i % templates.size());
            double score = tier == MatchTier.ABSTAIN ? 0.0 : 0.70 + (i % 25) * 0.01;
            nodes.add(
                    node(nodeId, jobId, template, tier, score, tier == MatchTier.ABSTAIN ? "below_floor" : "text 0.8"));
        }

        List<JobFinding> jobs = new ArrayList<>();
        Map<String, UUID> jobOwners = new LinkedHashMap<>();
        for (int j = 0; j < 12; j++) {
            String jobId = "j" + j;
            jobOwners.put(jobId, UUID.fromString(people.get(j % 2)));
            JobTier tier =
                    switch (j) {
                        case 0, 1 -> JobTier.STALE;
                        case 2 -> JobTier.PARTIAL_RUN;
                        case 3, 4, 5, 6 -> JobTier.UNKNOWN_PATTERN;
                        case 7 -> JobTier.PROCESS_RUN;
                        default -> JobTier.IN_PROGRESS;
                    };
            jobs.add(job(
                    jobId,
                    tier,
                    tier == JobTier.PROCESS_RUN ? "P-LAUNCH" : null,
                    0.6,
                    tier.name().toLowerCase(java.util.Locale.ROOT) + ": why"));
        }

        List<DiscoveryFinding> discoveries = new ArrayList<>();
        for (int k = 0; k < 9; k++) {
            discoveries.add(new DiscoveryFinding(
                    UUID.randomUUID(), DiscoveryFinding.Grain.STEP_KIND, "sk-" + k, 0.7, "6 marks across 4 jobs"));
        }
        for (int p = 0; p < 3; p++) {
            discoveries.add(new DiscoveryFinding(
                    UUID.randomUUID(),
                    DiscoveryFinding.Grain.DRAFT_PROCESS,
                    "proc-" + p,
                    0.8,
                    "4 runs, order reliable"));
        }

        Audience audience = new Audience(UUID.randomUUID(), OWNER, jobOwners, markers, ableToAct);
        return new Week(nodes, jobs, discoveries, audience, NudgeHistory.none());
    }

    private static NodeFinding node(
            String id, String jobId, String templateId, MatchTier tier, double score, String reason) {
        return new NodeFinding(UUID.randomUUID(), id, jobId, templateId, tier, score, reason);
    }

    private static JobFinding job(String id, JobTier tier, String processId, double score, String reason) {
        return new JobFinding(UUID.randomUUID(), id, tier, processId, score, reason);
    }

    private static Audience audienceFor(List<String> nodeIds, String jobId) {
        Map<String, UUID> markers = new LinkedHashMap<>();
        UUID somebody = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        nodeIds.forEach(n -> markers.put(n, somebody));
        return new Audience(UUID.randomUUID(), OWNER, Map.of(jobId, OWNER), markers, Set.of(OWNER, somebody));
    }
}
