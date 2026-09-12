package com.flowops.discovery.application.crossing;

import com.flowops.discovery.application.analysis.AnalysisStorePort;
import com.flowops.discovery.application.crossing.port.ProcessCompositionPort;
import com.flowops.discovery.application.crossing.port.WorkTemplateResolutionPort;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.ProposalIsNotAShapeException;
import com.flowops.discovery.application.shared.exception.RecommendationAlreadyDecidedException;
import com.flowops.discovery.application.shared.exception.UnknownRecommendationException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.domain.analysis.ShapeSteps;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WriteItDownService implements WriteItDownUseCase {
    private static final String WRITE_IT_DOWN = "WRITE_IT_DOWN";

    private static final String PROVENANCE =
            "Observed in the work graph. Added when a recurring sequence was written down as a process.";

    private final AnalysisStorePort store;
    private final WorkTemplateResolutionPort work;
    private final ProcessCompositionPort processes;
    private final IdentifyCallerPort caller;
    private final Clock clock;

    public WriteItDownService(
            AnalysisStorePort store,
            WorkTemplateResolutionPort work,
            ProcessCompositionPort processes,
            IdentifyCallerPort caller,
            Clock clock) {
        this.store = store;
        this.work = work;
        this.processes = processes;
        this.caller = caller;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WrittenDown execute(WriteItDown command) {
        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);
        Instant now = clock.instant();

        AnalysisStorePort.ProposedAction proposal = store.proposal(command.recommendationId())
                .orElseThrow(() -> new UnknownRecommendationException(
                        "no proposal " + command.recommendationId() + " to write down"));

        if (!WRITE_IT_DOWN.equals(proposal.kind())) {
            throw new ProposalIsNotAShapeException("a " + proposal.kind() + " proposal has no process to author");
        }

        List<String> steps = ShapeSteps.of(proposal.subjectKey());
        if (steps.isEmpty()) {
            throw new ProposalIsNotAShapeException(
                    "the evidence behind this proposal is not a sequence: " + proposal.subjectKey());
        }

        if (!store.claim(command.recommendationId(), me, now)) {
            throw new RecommendationAlreadyDecidedException(
                    "proposal " + command.recommendationId() + " has already been decided");
        }

        List<String> titles = ShapeSteps.titled(steps);

        List<UUID> library = titles.stream()
                .map(title -> work.templateFor(title, PROVENANCE, me))
                .toList();

        UUID authored = processes.composeProcessFrom(command.name(), library);

        store.recordProduced(command.recommendationId(), authored);

        return new WrittenDown(command.recommendationId(), authored, command.name(), titles);
    }
}
