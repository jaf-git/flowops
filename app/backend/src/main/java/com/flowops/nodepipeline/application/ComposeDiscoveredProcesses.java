package com.flowops.nodepipeline.application;

import com.flowops.nodepipeline.application.port.PipelineCallerPort;
import com.flowops.nodepipeline.application.port.ProcessDraftPort;
import com.flowops.nodepipeline.application.port.TaskTemplateDraftPort;
import com.flowops.nodepipeline.application.port.WorkJudgePort;
import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.ConceptSplit;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.compose.Conversion;
import com.flowops.nodepipeline.domain.compose.DraftProcess;
import com.flowops.nodepipeline.domain.compose.ProcessValidator;
import com.flowops.nodepipeline.domain.discovery.DiscoveredProcess;
import com.flowops.nodepipeline.domain.discovery.DiscoveryWeights;
import com.flowops.nodepipeline.domain.discovery.StepDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import com.flowops.shared.published.PublishedRefusal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComposeDiscoveredProcesses {
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);

    private final RunNodePipeline pipeline;
    private final TaskTemplateDraftPort drafts;
    private final ProcessDraftPort processes;
    private final PipelineCallerPort caller;
    private final WorkJudgePort judge;
    private final Clock clock;

    public ComposeDiscoveredProcesses(
            RunNodePipeline pipeline,
            TaskTemplateDraftPort drafts,
            ProcessDraftPort processes,
            PipelineCallerPort caller,
            WorkJudgePort judge,
            Clock clock) {
        this.pipeline = pipeline;
        this.drafts = drafts;
        this.processes = processes;
        this.caller = caller;
        this.judge = judge;
        this.clock = clock;
    }

    public Composed compose() {
        Instant now = clock.instant();
        return compose(now.minus(DEFAULT_WINDOW), now);
    }

    @Transactional
    public Composed compose(Instant from, Instant to) {
        UUID author = caller.currentCaller()
                .orElseThrow(() -> new IllegalStateException("there is no session behind this call"));

        RunNodePipeline.Discovery discovery = pipeline.discover(from, to);
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);

        Map<String, StepKind> kindsById = new LinkedHashMap<>();
        for (StepKind kind : discovery.stepKinds()) {
            kindsById.put(kind.id(), kind);
        }

        Map<String, Conversion.Resolved> resolutions = new LinkedHashMap<>();
        Map<String, String> statuses = new LinkedHashMap<>();
        for (CandidateTemplate known : discovery.library()) {
            statuses.put(known.id(), known.status());
        }

        int drafted = 0;
        for (StepKind kind : discovery.stepKinds()) {
            List<PipelineNode> marks = nodesOf(kind, discovery.nodes());
            Conversion.Resolved resolved = Conversion.resolve(kind, marks, discovery.library());

            if (resolved.outcome() == Conversion.Resolution.MINTED) {
                CandidateTemplate wanted = Conversion.mint(kind, marks, null, today, this::nameFromModel);
                TaskTemplateDraftPort.Draft entry = drafts.draftFor(wanted, author);

                resolved = new Conversion.Resolved(
                        kind,
                        Conversion.Resolution.MINTED,
                        entry.id().toString(),
                        wanted.title(),
                        resolved.score(),
                        resolved.margin(),
                        resolved.alternatives());
                statuses.put(entry.id().toString(), "DRAFT");

                if (entry.created()) {
                    drafted++;
                }
            }
            resolutions.put(kind.id(), resolved);
        }

        List<Written> written = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int index = 0;

        for (DiscoveredProcess found : discovery.processes()) {
            index++;
            DraftProcess draft =
                    Conversion.compose(found, resolutions, kindsById, discovery.nodesByJob(), "P" + index, statuses);

            ProcessValidator.Result verdict = ProcessValidator.validate(draft, "APPROVED");

            try {
                UUID saved = processes.save(draft);
                written.add(new Written(
                        saved,
                        draft.name(),
                        draft.steps().size(),
                        draft.observedEdges().size(),
                        found.runs(),
                        verdict.errors()));
            } catch (PublishedRefusal alreadyThere) {
                skipped.add(draft.name());
            }
        }

        return new Composed(
                discovery.stepKinds().size(), drafted, refineTheVocabulary(discovery, author, today), written, skipped);
    }

    private int refineTheVocabulary(RunNodePipeline.Discovery discovery, UUID author, LocalDate today) {
        if (!judge.isEnabled(Judgement.PlugPoint.SAME_KIND)) {
            return 0;
        }

        StepDiscovery steps = new StepDiscovery(DiscoveryWeights.reference(), discovery.directConversations());
        List<StepKind> refined =
                steps.refinedForDrafting(discovery.nodes(), discovery.excluded(), node -> conceptOf(node));

        int added = 0;
        for (StepKind kind : refined) {
            if (!kind.id().contains("#")) {
                continue;
            }
            List<PipelineNode> marks = nodesOf(kind, discovery.nodes());
            CandidateTemplate wanted = Conversion.mint(kind, marks, null, today, null);

            if (drafts.draftFor(wanted, author).created()) {
                added++;
            }
        }
        return added;
    }

    private String nameFromModel(StepKind kind) {
        if (!judge.isEnabled(Judgement.PlugPoint.NAMING)) {
            return null;
        }
        return judge.judge(new Judgement.Question(
                        Judgement.PlugPoint.NAMING, String.join(" ", kind.words()), List.of(), kind.bestName()))
                .map(Judgement.Verdict::value)
                .orElse(null);
    }

    private String conceptOf(PipelineNode node) {
        Set<String> allowed = ConceptSplit.conceptsFor(node.workType());
        if (allowed.isEmpty()) {
            return null;
        }
        return judge.judge(new Judgement.Question(
                        Judgement.PlugPoint.SAME_KIND, node.text(), List.of(), String.join(", ", allowed)))
                .map(Judgement.Verdict::value)
                .filter(allowed::contains)
                .orElse(null);
    }

    private static List<PipelineNode> nodesOf(StepKind kind, List<PipelineNode> all) {
        return all.stream().filter(node -> kind.nodeIds().contains(node.id())).toList();
    }

    public record Composed(
            int stepKindsFound,
            int taskTemplatesDrafted,
            int vocabularyRefinements,
            List<Written> processes,
            List<String> alreadyThere) {
        public Composed {
            processes = List.copyOf(processes);
            alreadyThere = List.copyOf(alreadyThere);
        }
    }

    public record Written(
            UUID templateId, String name, int steps, int observedEdges, int seenInRuns, List<String> blocksApproval) {
        public Written {
            blocksApproval = List.copyOf(blocksApproval);
        }
    }
}
