package com.flowops.nodepipeline.application;

import com.flowops.nodepipeline.domain.discovery.DiscoveredProcess;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscoverShapes implements DiscoverShapesUseCase {
    private final RunNodePipeline pipeline;

    public DiscoverShapes(RunNodePipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    @Transactional(readOnly = true)
    public Shapes shapesIn(Instant from, Instant to) {
        RunNodePipeline.Discovery discovery = pipeline.discover(from, to);

        return new Shapes(
                discovery.nodes().size(),
                discovery.excluded().size(),
                discovery.stepKinds().stream().map(DiscoverShapes::view).toList(),
                discovery.processes().stream().map(DiscoverShapes::view).toList());
    }

    private static StepKindView view(StepKind kind) {
        return new StepKindView(
                kind.id(),
                kind.workType(),
                kind.isSubprocess(),
                kind.nodeIds(),
                kind.jobIds().stream().sorted().toList(),
                kind.words(),
                kind.cohesion(),
                kind.certainty(),
                kind.activity(),
                kind.activityName());
    }

    private static ProcessView view(DiscoveredProcess process) {
        List<String> steps = new ArrayList<>(process.core());
        return new ProcessView(
                steps,
                process.displayOrder(),
                process.orderReliable(),
                process.orderConfidence(),
                process.jobIds(),
                process.runs(),
                process.certainty());
    }
}
