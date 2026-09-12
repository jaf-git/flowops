package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.application.RunNodePipeline;
import com.flowops.nodepipeline.application.port.PipelineGraphPort;
import com.flowops.nodepipeline.application.port.PipelineJournalPort;
import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.NodeVerdict;
import com.flowops.nodepipeline.domain.PipelineNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunNodePipelineTest {
    private static final Instant NOW = Instant.parse("2026-08-30T09:00:00Z");
    private static final Instant FROM = NOW.minusSeconds(86_400L * 30);

    @Test
    void everyVerdictIsJournalledIncludingTheAbstentions() {
        RecordingJournal journal = new RecordingJournal();
        RunNodePipeline pipeline = new RunNodePipeline(
                new StubGraph(),
                journal,
                new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                fixedClock());

        RunNodePipeline.RunSummary summary = pipeline.run(FROM, NOW);

        assertThat(summary.nodesRead()).isEqualTo(3);
        assertThat(journal.decisions)
                .as("one row per node, abstentions included -- recall is only measurable from what the "
                        + "pipeline did not say")
                .hasSize(3);
        assertThat(journal.decisions).extracting(v -> v.tier().name()).contains("ABSTAIN");
    }

    @Test
    void whereAnItemStoppedIsRecordedAndDistinguishesAGateFromAWeakMatch() {
        RecordingJournal journal = new RecordingJournal();
        new RunNodePipeline(
                        new StubGraph(),
                        journal,
                        new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                        fixedClock())
                .run(FROM, NOW);

        assertThat(journal.stuck)
                .extracting(PipelineJournalPort.StuckItem::itemId)
                .contains("boundary");
        assertThat(journal.stuck)
                .filteredOn(s -> s.itemId().equals("boundary"))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.lastStage()).isEqualTo("OBSERVE");
                    assertThat(s.reason()).isEqualTo("gate:job_boundary");
                    assertThat(s.itemKind()).isEqualTo("NODE");
                });
    }

    @Test
    void theRunIsOpenedBeforeItReadsAndSealedAfterItDecides() {
        RecordingJournal journal = new RecordingJournal();
        new RunNodePipeline(
                        new StubGraph(),
                        journal,
                        new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                        fixedClock())
                .run(FROM, NOW);

        assertThat(journal.order)
                .containsExactly(
                        "openRun",
                        "recordVolumes",
                        "recordDecisions",
                        "recordJobDecisions",
                        "recordDiscoveries",
                        "recordStuck",
                        "closeRun");
        assertThat(journal.signature).hasSize(64);
        assertThat(journal.aiMode)
                .as("the shippable product is deterministic; AI is additive at three plug points")
                .isEqualTo(PipelineJournalPort.AiMode.OFF);
    }

    @Test
    void theJobLayerRunsInTheSamePassAndAnUnmatchedEngagementBecomesADiscoveryCandidate() {
        RecordingJournal journal = new RecordingJournal();
        RunNodePipeline.RunSummary summary = new RunNodePipeline(
                        new StubGraph(),
                        journal,
                        new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                        fixedClock())
                .run(FROM, NOW);

        assertThat(summary.jobsRead()).isEqualTo(1);
        assertThat(summary.processesConsidered()).isZero();
        assertThat(summary.discoveryCandidates()).isEqualTo(1);

        assertThat(journal.jobDecisions).singleElement().satisfies(v -> assertThat(v.isDiscoveryCandidate())
                .isTrue());

        assertThat(journal.stuck)
                .filteredOn(s -> s.itemKind().equals("JOB"))
                .singleElement()
                .satisfies(s -> assertThat(s.lastStage()).isEqualTo("DETECT"));
    }

    @Test
    void anEmptyLibraryAbstainsOnEveryNodeRatherThanFailing() {
        RecordingJournal journal = new RecordingJournal();
        RunNodePipeline pipeline = new RunNodePipeline(
                new StubGraph(List.of()),
                journal,
                new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                fixedClock());

        RunNodePipeline.RunSummary summary = pipeline.run(FROM, NOW);

        assertThat(summary.tiers()).containsOnlyKeys("ABSTAIN");
        assertThat(journal.stuck)
                .extracting(PipelineJournalPort.StuckItem::reason)
                .contains("no_eligible_template");
    }

    @Test
    void theModeReachesBothTheRunRowAndTheSignature() {
        RecordingJournal deterministic = new RecordingJournal();
        RecordingJournal compared = new RecordingJournal();

        new RunNodePipeline(new StubGraph(), deterministic, new StubJudge("m-1", "v3"), fixedClock())
                .run(FROM, NOW, PipelineJournalPort.AiMode.OFF);
        new RunNodePipeline(new StubGraph(), compared, new StubJudge("m-1", "v3"), fixedClock())
                .run(FROM, NOW, PipelineJournalPort.AiMode.COMPARE);

        assertThat(compared.aiMode).isEqualTo(PipelineJournalPort.AiMode.COMPARE);
        assertThat(compared.signature)
                .as("two configurations that share a fingerprint cannot be told apart afterwards")
                .isNotEqualTo(deterministic.signature);
    }

    @Test
    void compareKeepsBothAnswersAndTheDeterministicOneIsUntouched() {
        RecordingJournal journal = new RecordingJournal();

        new RunNodePipeline(new StubGraph(), journal, new StubJudge("m-1", "v3"), fixedClock())
                .run(FROM, NOW, PipelineJournalPort.AiMode.COMPARE);

        assertThat(journal.order)
                .as("COMPARE writes the compared rows, not the plain ones")
                .contains("recordComparedDecisions")
                .doesNotContain("recordDecisions");
        assertThat(journal.comparisons).hasSize(3);
        assertThat(journal.comparisons)
                .allSatisfy(c -> assertThat(c.deterministic()).isNotNull());
        assertThat(journal.comparisons)
                .as("this judge answers nothing, so nothing may be attributed to it")
                .allSatisfy(c -> assertThat(c.withAi()).isNull());
        assertThat(journal.comparisons)
                .as("every row stays attributable to the model that could have spoken")
                .allSatisfy(c -> assertThat(c.modelId()).isEqualTo("m-1"));
    }

    @Test
    void anOffRunWritesPlainDecisionsAndNoComparison() {
        RecordingJournal journal = new RecordingJournal();

        new RunNodePipeline(new StubGraph(), journal, new StubJudge("m-1", "v3"), fixedClock())
                .run(FROM, NOW, PipelineJournalPort.AiMode.OFF);

        assertThat(journal.order).contains("recordDecisions").doesNotContain("recordComparedDecisions");
        assertThat(journal.comparisons).isEmpty();
    }

    private record StubJudge(String model, String prompt)
            implements com.flowops.nodepipeline.application.port.WorkJudgePort {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean isEnabled(com.flowops.nodepipeline.domain.ai.Judgement.PlugPoint plugPoint) {
            return false;
        }

        @Override
        public java.util.Optional<com.flowops.nodepipeline.domain.ai.Judgement.Verdict> judge(
                com.flowops.nodepipeline.domain.ai.Judgement.Question question) {
            return java.util.Optional.empty();
        }

        @Override
        public int callsRemaining() {
            return 0;
        }

        @Override
        public String modelId() {
            return model;
        }

        @Override
        public String promptVersion() {
            return prompt;
        }
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Test
    void aWindowThatFilledTheVolumeGuardSaysSoRatherThanGoingQuiet() {
        RecordingJournal journal = new RecordingJournal();

        new RunNodePipeline(
                        new StubGraph(RunNodePipeline.MAX_NODES_PER_RUN, 348),
                        journal,
                        new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                        fixedClock())
                .run(FROM, NOW);

        assertThat(journal.stuck)
                .as("a truncated run states how much it read, the range it did not, and what to do")
                .anySatisfy(item -> {
                    assertThat(item.itemKind()).isEqualTo("RUN");
                    assertThat(item.itemId())
                            .as("the window, not the run — keyed on the run it could never be diffed")
                            .isEqualTo("window");
                    assertThat(item.lastStage()).isEqualTo("OBSERVE");
                    assertThat(item.reason())
                            .as("the count read against the count that existed")
                            .contains("read 200 of 348")
                            .as("the unread range, and its direction — newest-first means the rest are older")
                            .contains("148 not read are older than")
                            .as("what the reader can do about it, which is the half a bare count omits")
                            .contains("narrow the window")
                            .contains("raise the cap");
                });
    }

    @Test
    void aRunSaysHowMuchTheWindowHeldAndNotOnlyHowMuchItRead() {
        RecordingJournal journal = new RecordingJournal();

        RunNodePipeline.RunSummary truncated = new RunNodePipeline(
                        new StubGraph(RunNodePipeline.MAX_NODES_PER_RUN, 348),
                        journal,
                        new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                        fixedClock())
                .run(FROM, NOW);

        assertThat(truncated.nodesRead()).isEqualTo(RunNodePipeline.MAX_NODES_PER_RUN);
        assertThat(truncated.nodesInWindow())
                .as("the denominator, on the summary rather than only in the ledger")
                .isEqualTo(348);
        assertThat(journal.nodesInWindow)
                .as("and stored, so a reload does not lose it")
                .isEqualTo(348);

        RecordingJournal whole = new RecordingJournal();
        RunNodePipeline.RunSummary complete = new RunNodePipeline(
                        new StubGraph(),
                        whole,
                        new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                        fixedClock())
                .run(FROM, NOW);

        assertThat(complete.nodesInWindow())
                .as("an ordinary run read everything the window held, and the pair says so by agreeing")
                .isEqualTo(complete.nodesRead());
    }

    @Test
    void aWindowSaysHowMuchItHoldsBeforeAnythingIsRun() {
        RunNodePipeline.WindowPreview preview = new RunNodePipeline(
                        new StubGraph(RunNodePipeline.MAX_NODES_PER_RUN, 348),
                        new RecordingJournal(),
                        new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                        fixedClock())
                .preview(FROM, NOW);

        assertThat(preview.nodesInWindow())
                .as("everything the window holds, ignoring the cap — not what a run would return")
                .isEqualTo(348);
        assertThat(preview.cap()).isEqualTo(200);
        assertThat(preview.wouldTruncate())
                .as("348 exceeds the cap, so a run would drop the oldest and must say so up front")
                .isTrue();
    }

    @Test
    void aWindowThatFitsPromisesNoTruncation() {
        RunNodePipeline.WindowPreview preview = new RunNodePipeline(
                        new StubGraph(),
                        new RecordingJournal(),
                        new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                        fixedClock())
                .preview(FROM, NOW);

        assertThat(preview.nodesInWindow()).isEqualTo(3);
        assertThat(preview.wouldTruncate()).isFalse();
    }

    @Test
    void anEngagementReadOnlyInPartIsNamedRatherThanLookingComplete() {
        RecordingJournal journal = new RecordingJournal();

        new RunNodePipeline(
                        new StubGraph(RunNodePipeline.MAX_NODES_PER_RUN, 348),
                        journal,
                        new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                        fixedClock())
                .run(FROM, NOW);

        assertThat(journal.stuck)
                .as("job-1 holds 348 nodes in the window and only 200 were read, so it is a partial reading")
                .anySatisfy(item -> {
                    assertThat(item.itemKind()).isEqualTo("JOB");
                    assertThat(item.lastStage()).isEqualTo("OBSERVE");
                    assertThat(item.reason()).contains("partially read").contains("200 of 348");
                });
    }

    @Test
    void aWindowThatFitsClaimsNoTruncation() {
        RecordingJournal journal = new RecordingJournal();

        new RunNodePipeline(
                        new StubGraph(),
                        journal,
                        new com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter(),
                        fixedClock())
                .run(FROM, NOW);

        assertThat(journal.stuck)
                .noneSatisfy(item -> assertThat(item.itemKind()).isEqualTo("RUN"));
    }

    private static final class StubGraph implements PipelineGraphPort {
        @Override
        public java.util.Optional<String> commonestWorkTypeOf(java.util.UUID performer) {
            return java.util.Optional.empty();
        }

        private final List<CandidateTemplate> library;

        private int windowSize = -1;

        private int windowTotal = -1;

        StubGraph(int windowSize, int windowTotal) {
            this();
            this.windowSize = windowSize;
            this.windowTotal = windowTotal;
        }

        StubGraph() {
            this(List.of(new CandidateTemplate(
                    "T-CROP",
                    "Crop the header pictures",
                    "Resize and brighten the banner images for a client page",
                    "DESIGN",
                    "Designer",
                    "DESIGN",
                    "A set of cropped images",
                    null,
                    null,
                    List.of(),
                    "APPROVED",
                    LocalDate.of(2026, 1, 1),
                    List.of("brighten them", "banner image"),
                    null,
                    null,
                    null)));
        }

        StubGraph(List<CandidateTemplate> library) {
            this.library = library;
        }

        @Override
        public List<PipelineNode> nodesMarkedBetween(Instant from, Instant to, int limit) {
            if (windowSize >= 0) {
                List<PipelineNode> filled = new java.util.ArrayList<>();
                for (int i = 0; i < windowSize; i++) {
                    filled.add(node("n-" + i, "I will brighten them and send a new version", "WORK"));
                }
                return filled;
            }
            return List.of(
                    node("boundary", "Aurora Coffee summer menu", "JOB_START"),
                    node("crop", "I will brighten them and send a new version", "WORK"),
                    node("chatter", "Same here. Quiet week for once.", "WORK"));
        }

        @Override
        public java.util.Map<String, Integer> nodeCountsByJobBetween(Instant from, Instant to) {
            return java.util.Map.of("job-1", windowTotal >= 0 ? windowTotal : 3);
        }

        @Override
        public java.util.Map<String, String> templateTitles() {
            return java.util.Map.of("t-a", "Brief", "t-b", "Design", "t-c", "Ship");
        }

        @Override
        public List<CandidateTemplate> discoveredTemplates() {
            return List.of();
        }

        @Override
        public List<CandidateTemplate> library() {
            return library;
        }

        @Override
        public Adoption activityAdoption() {
            return new Adoption(0, 0);
        }

        @Override
        public java.util.Set<String> directConversations() {
            return java.util.Set.of();
        }

        @Override
        public List<com.flowops.nodepipeline.domain.job.PipelineJob> jobsFor(java.util.Collection<String> jobIds) {
            return List.of(new com.flowops.nodepipeline.domain.job.PipelineJob(
                    "job-1",
                    "Aurora Coffee summer menu",
                    "CLOSED",
                    false,
                    true,
                    false,
                    null,
                    "CLIENT",
                    LocalDate.of(2026, 8, 25)));
        }

        @Override
        public List<com.flowops.nodepipeline.domain.job.ProcessShape> processShapes() {
            return List.of();
        }

        private static PipelineNode node(String id, String text, String kind) {
            return new PipelineNode(
                    id,
                    "job-1",
                    text,
                    null,
                    UUID.nameUUIDFromBytes("sara".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    UUID.nameUUIDFromBytes("ines".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    kind,
                    "Designer",
                    "Designer",
                    LocalDate.of(2026, 8, 20),
                    PipelineNode.Closure.MARKED,
                    "STANDALONE",
                    false,
                    "DESIGN",
                    "DESIGN",
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
    }

    private static final class RecordingJournal implements PipelineJournalPort {
        private final List<String> order = new ArrayList<>();
        private final List<NodeVerdict> decisions = new ArrayList<>();
        private final List<com.flowops.nodepipeline.domain.job.JobVerdict> jobDecisions = new ArrayList<>();
        private final List<StuckItem> stuck = new ArrayList<>();
        private String signature;
        private AiMode aiMode;
        private String modelId;
        private String promptVersion;

        private int nodesInWindow;

        @Override
        public UUID openRun(Instant windowFrom, Instant windowTo, String signature, AiMode aiMode, Instant startedAt) {
            order.add("openRun");
            this.signature = signature;
            this.aiMode = aiMode;
            return UUID.randomUUID();
        }

        @Override
        public void recordVolumes(UUID runId, int nodesRead, int nodesInWindow, int jobsRead) {
            order.add("recordVolumes");
            this.nodesInWindow = nodesInWindow;
        }

        @Override
        public void recordDecisions(
                UUID runId, List<NodeVerdict> verdicts, Instant at, String modelId, String promptVersion) {
            order.add("recordDecisions");
            decisions.addAll(verdicts);
            this.modelId = modelId;
            this.promptVersion = promptVersion;
        }

        @Override
        public void recordJobDecisions(
                UUID runId, List<com.flowops.nodepipeline.domain.job.JobVerdict> verdicts, Instant at) {
            order.add("recordJobDecisions");
            jobDecisions.addAll(verdicts);
        }

        @Override
        public void recordDiscoveries(
                UUID runId,
                List<com.flowops.nodepipeline.domain.discovery.StepKind> kinds,
                List<com.flowops.nodepipeline.domain.discovery.DiscoveredProcess> processes,
                Instant at) {
            order.add("recordDiscoveries");
        }

        private final List<Comparison> comparisons = new ArrayList<>();

        @Override
        public void recordComparedDecisions(UUID runId, List<Comparison> items, Instant at) {
            order.add("recordComparedDecisions");
            comparisons.addAll(items);
        }

        @Override
        public void recordStuck(UUID runId, List<StuckItem> items) {
            order.add("recordStuck");
            stuck.addAll(items);
        }

        @Override
        public void closeRun(UUID runId, String reachedStage, String failure, Instant finishedAt) {
            order.add("closeRun");
        }
    }
}
