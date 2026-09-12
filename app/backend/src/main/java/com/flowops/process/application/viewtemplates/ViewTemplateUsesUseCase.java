package com.flowops.process.application.viewtemplates;

import com.flowops.process.application.shared.port.TemplateUsesPort;
import java.util.List;
import java.util.UUID;

public interface ViewTemplateUsesUseCase {
    Uses of(UUID taskTemplateId);

    record Uses(List<TemplateUsesPort.PlannedIn> processTemplates, List<TemplateUsesPort.CutIn> runs, int runsTotal) {}
}
