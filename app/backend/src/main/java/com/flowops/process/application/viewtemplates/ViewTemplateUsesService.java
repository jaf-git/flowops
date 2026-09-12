package com.flowops.process.application.viewtemplates;

import com.flowops.process.application.shared.port.TemplateUsesPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewTemplateUsesService implements ViewTemplateUsesUseCase {
    private static final int RUNS_SHOWN = 20;

    private final TemplateUsesPort uses;

    public ViewTemplateUsesService(TemplateUsesPort uses) {
        this.uses = uses;
    }

    @Override
    @Transactional(readOnly = true)
    public Uses of(UUID taskTemplateId) {
        return new Uses(
                uses.plannedIn(taskTemplateId),
                uses.cutIn(taskTemplateId, RUNS_SHOWN),
                uses.countCutIn(taskTemplateId));
    }
}
