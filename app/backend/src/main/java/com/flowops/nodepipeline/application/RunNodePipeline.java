package com.flowops.nodepipeline.application;

import com.flowops.nodepipeline.application.port.PipelineGraphPort;
import com.flowops.nodepipeline.application.port.PipelineJournalPort;
import com.flowops.nodepipeline.application.port.WorkJudgePort;
import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.NodeVerdict;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.RunSignature;
import com.flowops.nodepipeline.domain.discovery.Churn;
import com.flowops.nodepipeline.domain.discovery.DiscoveredProcess;
import com.flowops.nodepipeline.domain.discovery.DiscoveryWeights;
import com.flowops.nodepipeline.domain.discovery.ProcessDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import com.flowops.nodepipeline.domain.job.Cadence;
import com.flowops.nodepipeline.domain.job.JobMatcher;
import com.flowops.nodepipeline.domain.job.JobVerdict;
import com.flowops.nodepipeline.domain.job.JobWeights;
import com.flowops.nodepipeline.domain.job.PipelineJob;
import com.flowops.nodepipeline.domain.job.ProcessShape;
import com.flowops.nodepipeline.domain.match.Lexicons;
import com.flowops.nodepipeline.domain.match.MatchWeights;
import com.flowops.nodepipeline.domain.match.NodeMatcher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunNodePipeline {
    public static final int MAX_NODES_PER_RUN = 200;

    private final int maxNodesPerRun;

    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);

    private final PipelineGraphPort graph;
    private final PipelineJournalPort journal;
    private final WorkJudgePort judge;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public RunNodePipeline(
            PipelineGraphPort graph,
            PipelineJournalPort journal,
            WorkJudgePort judge,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${flowops.pipeline.max-nodes:200}")
                    int maxNodesPerRun) {
        this.judge = judge;
        this.graph = graph;
        this.journal = journal;
        this.clock = clock;
        this.maxNodesPerRun = maxNodesPerRun;
    }

    public RunNodePipeline(PipelineGraphPort graph, PipelineJournalPort journal, WorkJudgePort judge, Clock clock) {
        this(graph, journal, judge, clock, MAX_NODES_PER_RUN);
    }

    @Transactional
    public RunSummary run() {
        return run(PipelineJournalPort.AiMode.OFF);
    }

    @Transactional
    public RunSummary run(PipelineJournalPort.AiMode aiMode) {
        Instant now = clock.instant();
        return run(now.minus(DEFAULT_WINDOW), now, aiMode);
    }

    public WindowPreview preview() {
        Instant now = clock.instant();
        return preview(now.minus(DEFAULT_WINDOW), now);
    }

    /**
     * Both endpoints take the window from the caller, so both refuse the same way. A backwards
     * window used to reach the database, where it broke the run's first insert; a backwards
     * preview never reached anything and answered a confident zero.
     */
    private static void windowMustRunForwards(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new WindowRunsBackwardsException(from, to);
        }
    }

    public WindowPreview preview(Instant from, Instant to) {
        windowMustRunForwards(from, to);

        int held = graph.nodeCountsByJobBetween(from, to).values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        return new WindowPreview(from, to, held, maxNodesPerRun, held > maxNodesPerRun);
    }

    public record WindowPreview(Instant from, Instant to, int nodesInWindow, int cap, boolean wouldTruncate) {}

    @Transactional
    public RunSummary run(Instant from, Instant to) {
        return run(from, to, PipelineJournalPort.AiMode.OFF);
    }

    @Transactional
    public RunSummary run(Instant from, Instant to, PipelineJournalPort.AiMode aiMode) {
        windowMustRunForwards(from, to);

        // The budget is specified per run, so the judge is told a run is starting.
        judge.beginRun();

        MatchWeights weights = MatchWeights.reference();

        Lexicons lexicons = Lexicons.empty();

        String signature = RunSignature.of(weights, lexicons, aiMode.name(), judge.modelId(), judge.promptVersion());

        Instant startedAt = clock.instant();
        UUID runId = journal.openRun(from, to, signature, aiMode, startedAt);

        Discovery discovery = discover(from, to);
        List<PipelineNode> nodes = discovery.nodes();
        List<CandidateTemplate> library = discovery.library();
        Map<String, List<PipelineNode>> byJob = discovery.nodesByJob();
        List<PipelineJob> jobs = discovery.jobs();

        List<ProcessShape> processes = graph.processShapes();

        Map<String, Integer> perJob =
                nodes.size() == maxNodesPerRun ? graph.nodeCountsByJobBetween(from, to) : Map.of();
        int nodesInWindow = perJob.isEmpty()
                ? nodes.size()
                : perJob.values().stream().mapToInt(Integer::intValue).sum();

        journal.recordVolumes(runId, nodes.size(), nodesInWindow, jobs.size());

        NodeMatcher.WorkJudge asked = aiMode == PipelineJournalPort.AiMode.OFF
                ? null
                : new NodeMatcher.WorkJudge() {
                    @Override
                    public boolean isEnabled(com.flowops.nodepipeline.domain.ai.Judgement.PlugPoint plugPoint) {
                        return judge.isEnabled(plugPoint);
                    }

                    @Override
                    public java.util.Optional<com.flowops.nodepipeline.domain.ai.Judgement.Verdict> judge(
                            com.flowops.nodepipeline.domain.ai.Judgement.Question question) {
                        return judge.judge(question);
                    }
                };

        NodeMatcher matcher =
                asked == null ? new NodeMatcher(weights, lexicons) : new NodeMatcher(weights, lexicons, asked);

        List<NodeVerdict> verdicts = new ArrayList<>(nodes.size());
        List<PipelineJournalPort.StuckItem> stuck = new ArrayList<>();
        Map<String, Integer> tiers = new LinkedHashMap<>();

        if (nodes.size() == maxNodesPerRun) {
            int held = nodesInWindow;
            int unread = Math.max(held - nodes.size(), 0);

            stuck.add(new PipelineJournalPort.StuckItem(
                    "window",
                    "RUN",
                    "OBSERVE",
                    "read " + nodes.size() + " of " + held + "; the " + unread + " not read are older than "
                            + nodes.getLast().createdAt()
                            + " - narrow the window to the last N days, or raise the cap"));

            Map<String, Integer> readPerJob = new LinkedHashMap<>();
            for (PipelineNode node : nodes) {
                readPerJob.merge(node.jobId(), 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> job : readPerJob.entrySet()) {
                int inWindow = perJob.getOrDefault(job.getKey(), job.getValue());
                if (inWindow > job.getValue()) {
                    stuck.add(new PipelineJournalPort.StuckItem(
                            job.getKey(),
                            "JOB",
                            "OBSERVE",
                            "partially read at the cap: " + job.getValue() + " of " + inWindow + " nodes"));
                }
            }
        }

        NodeMatcher plainMatcher =
                aiMode == PipelineJournalPort.AiMode.COMPARE ? new NodeMatcher(weights, lexicons) : null;
        List<PipelineJournalPort.Comparison> comparisons = new ArrayList<>();

        for (PipelineNode node : nodes) {
            NodeVerdict judged = matcher.match(node, library);

            NodeVerdict verdict = judged;
            if (plainMatcher != null) {
                NodeVerdict plain = plainMatcher.match(node, library);
                boolean modelSpoke = !plain.equals(judged);
                comparisons.add(new PipelineJournalPort.Comparison(
                        plain, modelSpoke ? judged : null, false, judge.modelId(), judge.promptVersion()));
                verdict = plain;
            }

            verdicts.add(verdict);
            tiers.merge(verdict.tier().name(), 1, Integer::sum);

            if (!verdict.acts()) {
                String stage = verdict.why().startsWith("gate:") ? "OBSERVE" : "MEASURE";
                stuck.add(new PipelineJournalPort.StuckItem(node.id(), "NODE", stage, verdict.why()));
            }
        }

        JobMatcher jobMatcher = new JobMatcher(
                matcher,
                JobWeights.reference(),
                clock.instant().atZone(java.time.ZoneOffset.UTC).toLocalDate());

        List<JobVerdict> jobVerdicts = new ArrayList<>();
        Map<String, Integer> jobTiers = new LinkedHashMap<>();

        for (PipelineJob job : jobs) {
            List<PipelineNode> jobNodes = byJob.getOrDefault(job.id(), List.of());

            if (job.standing()) {
                for (List<PipelineNode> cycle : Cadence.monthlyCycles(jobNodes).values()) {
                    record(jobMatcher.match(job, cycle, library, processes, true), jobVerdicts, jobTiers, stuck);
                }
            } else {
                record(jobMatcher.match(job, jobNodes, library, processes, false), jobVerdicts, jobTiers, stuck);
            }
        }

        Set<String> excluded = discovery.excluded();
        List<StepKind> stepKinds = discovery.stepKinds();

        for (String ungoverned :
                new StepDiscovery(DiscoveryWeights.reference()).ungoverned(discovery.nodes(), discovery.excluded())) {
            stuck.add(new PipelineJournalPort.StuckItem(
                    ungoverned, "NODE", "CORRELATE", StepDiscovery.NO_GOVERNED_WORK_TYPE));
        }
        List<DiscoveredProcess> discovered = discovery.processes();

        // Exhausting the budget mid-run means the later nodes were scored without the model that
        // scored the earlier ones. That is a fact about the run, so it is written down rather than
        // logged at debug and lost.
        if (aiMode != PipelineJournalPort.AiMode.OFF
                && judge.isEnabled(com.flowops.nodepipeline.domain.ai.Judgement.PlugPoint.SAME_WORK)
                && judge.callsRemaining() == 0) {
            stuck.add(new PipelineJournalPort.StuckItem(
                    "run",
                    "RUN",
                    "MEASURE",
                    "ai_budget_exhausted: the model stopped being asked partway through this run"));
        }

        if (comparisons.isEmpty()) {
            journal.recordDecisions(runId, verdicts, startedAt, judge.modelId(), judge.promptVersion());
        } else {
            journal.recordComparedDecisions(runId, comparisons, startedAt);
        }
        journal.recordJobDecisions(runId, jobVerdicts, startedAt);
        journal.recordDiscoveries(runId, stepKinds, discovered, startedAt);
        journal.recordStuck(runId, stuck);
        journal.closeRun(runId, "CORRELATE", null, clock.instant());

        return new RunSummary(
                runId,
                signature,
                from,
                to,
                nodes.size(),
                library.size(),
                Map.copyOf(tiers),
                jobs.size(),
                processes.size(),
                Map.copyOf(jobTiers),
                stepKinds.size(),
                discovered.size(),
                excluded.size(),
                nodesInWindow);
    }

    private static void record(
            JobVerdict verdict,
            List<JobVerdict> into,
            Map<String, Integer> tiers,
            List<PipelineJournalPort.StuckItem> stuck) {
        into.add(verdict);
        tiers.merge(verdict.tier().name(), 1, Integer::sum);

        if (verdict.processId() == null) {
            stuck.add(new PipelineJournalPort.StuckItem(verdict.jobId(), "JOB", "DETECT", verdict.why()));
        }
    }

    public record Discovery(
            List<PipelineNode> nodes,
            Map<String, List<PipelineNode>> nodesByJob,
            List<PipelineJob> jobs,
            List<CandidateTemplate> library,
            List<StepKind> stepKinds,
            List<DiscoveredProcess> processes,
            Set<String> excluded,
            Set<String> directConversations) {}

    public Discovery discover(Instant from, Instant to) {
        List<PipelineNode> nodes = graph.nodesMarkedBetween(from, to, maxNodesPerRun);
        List<CandidateTemplate> library = graph.library();

        Map<String, List<PipelineNode>> byJob = new LinkedHashMap<>();
        for (PipelineNode node : nodes) {
            byJob.computeIfAbsent(node.jobId(), key -> new ArrayList<>()).add(node);
        }

        List<PipelineJob> jobs = graph.jobsFor(byJob.keySet());

        Set<String> excluded = Churn.excludedFrom(nodes, jobs);
        DiscoveryWeights weights = DiscoveryWeights.reference();

        Set<String> direct = graph.directConversations();
        List<StepKind> stepKinds = new StepDiscovery(weights, direct).discover(nodes, excluded);
        List<DiscoveredProcess> processes = new ProcessDiscovery(weights).discover(jobs, nodes, stepKinds);

        return new Discovery(nodes, byJob, jobs, library, stepKinds, processes, excluded, direct);
    }

    public record RunSummary(
            UUID runId,
            String signature,
            Instant windowFrom,
            Instant windowTo,
            int nodesRead,
            int templatesConsidered,
            Map<String, Integer> tiers,
            int jobsRead,
            int processesConsidered,
            Map<String, Integer> jobTiers,
            int stepKindsFound,
            int processesDiscovered,
            int excludedFromDiscovery,
            int nodesInWindow) {
        public int discoveryCandidates() {
            return jobTiers.getOrDefault("UNKNOWN_PATTERN", 0);
        }
    }
}
