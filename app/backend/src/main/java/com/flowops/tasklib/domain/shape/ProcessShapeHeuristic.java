package com.flowops.tasklib.domain.shape;

import com.flowops.tasklib.domain.TemplateDetails;
import java.util.List;
import java.util.Optional;

public final class ProcessShapeHeuristic {
    private static final int CHECKLIST_LENGTH_WORTH_QUESTIONING = 5;

    private final List<ProcessShapeSignal> signals;

    public ProcessShapeHeuristic(List<ProcessShapeSignal> signals) {
        this.signals = List.copyOf(signals);
    }

    public static ProcessShapeHeuristic standard() {
        return new ProcessShapeHeuristic(
                List.of(new HandoverLanguageSignal(), new ChecklistLengthSignal(CHECKLIST_LENGTH_WORTH_QUESTIONING)));
    }

    public List<ProcessShapeHint> inspect(TemplateDetails details) {
        return signals.stream()
                .map(signal -> signal.inspect(details))
                .flatMap(Optional::stream)
                .toList();
    }
}
