package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.DependencyKind;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.StepDependency;
import com.flowops.process.domain.model.TemplateId;
import java.math.BigDecimal;
import java.time.Instant;

public interface SaveTemplatePort {
    void create(ProcessTemplate template);

    void replace(ProcessTemplate template);

    void addDependency(
            TemplateId template, StepDependency edge, DependencyKind kind, BigDecimal confidence, Instant at);

    boolean promoteDependency(TemplateId template, StepDependency edge, PersonId by, Instant at);

    void removeDependency(TemplateId template, StepDependency edge);

    void retire(ProcessTemplate template);

    void markComposedDraft(TemplateId template);
}
