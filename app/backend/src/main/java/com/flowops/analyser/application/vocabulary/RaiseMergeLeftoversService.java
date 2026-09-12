package com.flowops.analyser.application.vocabulary;

import com.flowops.analyser.application.shared.port.FindingReadPort;
import com.flowops.analyser.application.shared.port.FindingStorePort;
import com.flowops.analyser.domain.AnalyserStage;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.FindingContext;
import com.flowops.analyser.domain.Lifecycle;
import com.flowops.analyser.domain.StrandedTemplates;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RaiseMergeLeftoversService implements RaiseMergeLeftoversUseCase {
    private final FindingStorePort findings;
    private final FindingReadPort latest;

    public RaiseMergeLeftoversService(FindingStorePort findings, FindingReadPort latest) {
        this.findings = findings;
        this.latest = latest;
    }

    /**
     * Attaches the finding to the newest completed analysis rather than opening one of its own.
     *
     * <p>A run of its own would become "the latest run" for three separate views and hide a real
     * analysis behind a single finding, which is a worse failure than the one being reported.
     * Findings already carry a lifecycle, a first-seen time and a sighting count, so the model
     * expects one to recur and to outlive the run it was first written into.
     *
     * <p>Where no analysis has ever completed, nothing is raised and nothing pretends to have been.
     * A finding attached to no run cannot be opened, dismissed or acted on.
     */
    @Override
    @Transactional
    public Optional<UUID> execute(MergeLeftovers leftovers) {
        if (leftovers.templates().isEmpty()) {
            return Optional.empty();
        }

        Optional<UUID> runId = latest.latestFindings().map(FindingReadPort.Run::runId);
        if (runId.isEmpty()) {
            return Optional.empty();
        }

        findings.store(
                runId.get(),
                findingFor(leftovers),
                new FindingStorePort.Presentation(
                        leftovers.absorbedActivity(),
                        new FindingContext(List.of(), List.of(), 0, List.of(), null, null),
                        AnalyserStage.CORRELATE),
                Lifecycle.NEW,
                leftovers.at(),
                1,
                leftovers.at());

        return runId;
    }

    private static Finding findingFor(MergeLeftovers leftovers) {
        return new StrandedTemplates(
                        leftovers.absorbedActivity(),
                        leftovers.survivingActivity(),
                        leftovers.templates().stream()
                                .map(one -> new StrandedTemplates.Stranded(
                                        one.id().toString(), one.title(), one.timesUsed()))
                                .toList())
                .asFinding();
    }
}
