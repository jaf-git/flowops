package com.flowops.nodepipeline.application;

import com.flowops.nodepipeline.application.port.PipelineGraphPort;
import com.flowops.nodepipeline.application.port.WorkJudgePort;
import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.job.ProcessShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Writes the guidance a person would need to do a discovered piece of work for the first time.
 *
 * <p>Everything the model is given comes from the graph: the title people used, the kind of work,
 * the role that does it, the checklist steps somebody actually wrote down, what the work takes in
 * and produces. The model orders that into prose and adds nothing — or is asked not to. It is the
 * first plug point that composes rather than selects, so what it returns is labelled as written by
 * a model everywhere it appears.
 *
 * <p>Nothing here writes. Guidance is produced on request and returned; it is not stored, not
 * approved, and does not become part of any template.
 */
@Service
public class ExplainDiscovery {
    private final PipelineGraphPort graph;
    private final WorkJudgePort judge;

    public ExplainDiscovery(PipelineGraphPort graph, WorkJudgePort judge) {
        this.graph = graph;
        this.judge = judge;
    }

    /** What the pipeline has proposed, with the facts each proposal rests on. */
    public record Discovered(
            String id,
            String kind,
            String title,
            String status,
            String workType,
            String responsibleRole,
            String description,
            List<String> steps) {}

    public record Guidance(String id, String kind, String text, boolean writtenByModel, String modelId) {}

    /**
     * How much of the graph names what it did, rather than only who did it.
     *
     * <p>It belongs beside the discoveries because it is the number that explains them. A page
     * reporting six steps where thirteen activities happened is reporting a low figure here, not a
     * failure of clustering, and the two are indistinguishable without it.
     */
    public PipelineGraphPort.Adoption adoption() {
        return graph.activityAdoption();
    }

    public List<Discovered> everythingFound() {
        List<Discovered> found = new ArrayList<>();

        for (CandidateTemplate template : graph.discoveredTemplates()) {
            found.add(new Discovered(
                    template.id(),
                    "TEMPLATE",
                    template.title(),
                    template.status(),
                    template.workType(),
                    template.responsibleRole(),
                    template.description(),
                    template.checklist()));
        }

        java.util.Map<String, String> titles = graph.templateTitles();

        for (ProcessShape process : graph.processShapes()) {
            // The shape's steps are template ids. Anybody reading this wants their names.
            List<String> named = process.steps().stream()
                    .map(step -> titles.getOrDefault(step, null))
                    .filter(java.util.Objects::nonNull)
                    .toList();

            found.add(new Discovered(process.id(), "PROCESS", process.name(), "WRITTEN_DOWN", null, null, null, named));
        }

        return List.copyOf(found);
    }

    public Optional<Guidance> explain(String id) {
        Optional<Discovered> subject =
                everythingFound().stream().filter(d -> d.id().equals(id)).findFirst();

        if (subject.isEmpty()) {
            return Optional.empty();
        }
        if (!judge.isEnabled(Judgement.PlugPoint.EXPLAIN)) {
            return Optional.empty();
        }

        Discovered found = subject.get();
        return judge.judge(new Judgement.Question(Judgement.PlugPoint.EXPLAIN, evidenceFor(found), List.of(), null))
                .map(verdict -> new Guidance(found.id(), found.kind(), verdict.value(), true, judge.modelId()));
    }

    /**
     * Drops the drafter's recurring-words sentence before the description is shown to the model.
     *
     * <p>It is vocabulary the matcher uses, not something a person does, and a model reading it as
     * an instruction wrote a paragraph telling the reader to "write down the words that recur in
     * this work, such as after, before, budget". Nobody joining a company needs that, and its
     * presence in a handover note makes every sentence beside it harder to trust.
     */
    private static String withoutTheVocabularyLine(String description) {
        if (description == null) {
            return null;
        }
        int recurring = description.indexOf("The words that recur");
        return recurring < 0 ? description : description.substring(0, recurring).trim();
    }

    /**
     * The observations, written out as the only thing the model is allowed to work from. Fields the
     * graph never filled are omitted rather than sent as empty: a prompt that says "responsible
     * role: none" invites the model to supply one.
     */
    private static String evidenceFor(Discovered found) {
        StringBuilder evidence = new StringBuilder();
        evidence.append("Name people used: ").append(found.title()).append('\n');
        evidence.append("This is a ")
                .append("PROCESS".equals(found.kind()) ? "process" : "kind of task")
                .append('\n');

        if (found.workType() != null) {
            evidence.append("Kind of work: ").append(found.workType()).append('\n');
        }
        if (found.responsibleRole() != null) {
            evidence.append("Usually done by: ").append(found.responsibleRole()).append('\n');
        }
        String observed = withoutTheVocabularyLine(found.description());
        if (observed != null && !observed.isBlank()) {
            evidence.append("What was observed about it: ").append(observed).append('\n');
        }
        if (!found.steps().isEmpty()) {
            evidence.append("Steps people wrote down, in order:\n");
            for (String step : found.steps()) {
                evidence.append("  - ").append(step).append('\n');
            }
        }
        return evidence.toString();
    }
}
