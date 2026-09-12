package com.flowops.tasklib.domain.shape;

import com.flowops.tasklib.domain.TemplateDetails;
import java.util.Optional;

public final class ChecklistLengthSignal implements ProcessShapeSignal {
    private final int suspiciousAt;

    public ChecklistLengthSignal(int suspiciousAt) {
        this.suspiciousAt = suspiciousAt;
    }

    @Override
    public Optional<ProcessShapeHint> inspect(TemplateDetails details) {
        int items = details.checklist().size();
        if (items < suspiciousAt) {
            return Optional.empty();
        }
        return Optional.of(new ProcessShapeHint("checklist-length", String.valueOf(items)));
    }
}
