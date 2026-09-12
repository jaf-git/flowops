package com.flowops.nodepipeline.application.resemble;

import com.flowops.nodepipeline.application.port.PipelineCallerPort;
import com.flowops.nodepipeline.application.port.PipelineGraphPort;
import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.MatchTier;
import com.flowops.nodepipeline.domain.NodeVerdict;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.match.Lexicons;
import com.flowops.nodepipeline.domain.match.MatchWeights;
import com.flowops.nodepipeline.domain.match.NodeMatcher;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResembleApprovedWorkService implements ResembleApprovedWorkUseCase {
    private static final Set<MatchTier> RECOMMENDABLE = EnumSet.of(MatchTier.OK, MatchTier.NUDGE);

    private final PipelineGraphPort graph;
    private final PipelineCallerPort caller;
    private final Clock clock;

    public ResembleApprovedWorkService(PipelineGraphPort graph, PipelineCallerPort caller, Clock clock) {
        this.graph = graph;
        this.caller = caller;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_VIEW')")
    public Optional<Resemblance> forWhatWasSaid(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Optional<String> doing = caller.currentCaller().flatMap(graph::commonestWorkTypeOf);
        if (doing.isEmpty()) {
            return Optional.empty();
        }

        List<CandidateTemplate> approved = graph.library();
        if (approved.isEmpty()) {
            return Optional.empty();
        }

        NodeVerdict verdict = new NodeMatcher(MatchWeights.forOneSentence(), Lexicons.empty())
                .match(asSaid(text, doing.get()), approved);

        if (!RECOMMENDABLE.contains(verdict.tier()) || verdict.topTemplateId() == null) {
            return Optional.empty();
        }

        return approved.stream()
                .filter(template -> template.id().equals(verdict.topTemplateId()))
                .findFirst()
                .map(template -> new Resemblance(
                        template.id(),
                        template.title(),
                        verdict.score() == null ? 0.0 : verdict.score(),
                        verdict.why()));
    }

    private PipelineNode asSaid(String text, String workType) {
        return new PipelineNode(
                "said",
                null,
                text,
                null,
                null,
                null,
                "WORK",
                null,
                null,
                LocalDate.now(clock.withZone(ZoneOffset.UTC)),
                PipelineNode.Closure.MARKED,
                "STANDALONE",
                false,
                null,
                workType,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }
}
