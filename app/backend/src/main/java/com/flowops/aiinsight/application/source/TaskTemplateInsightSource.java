package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.application.ViewInsightsUseCase.Insight;
import com.flowops.aiinsight.application.port.TemplateUsagePort;
import java.util.List;
import java.util.UUID;

public interface TaskTemplateInsightSource {
    List<Insight> findIn(UUID templateId, TemplateUsagePort.Usage usage);
}
