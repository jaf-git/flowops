package com.flowops.tasklib.domain.shape;

import com.flowops.tasklib.domain.TemplateDetails;
import java.util.Optional;

public interface ProcessShapeSignal {
    Optional<ProcessShapeHint> inspect(TemplateDetails details);
}
