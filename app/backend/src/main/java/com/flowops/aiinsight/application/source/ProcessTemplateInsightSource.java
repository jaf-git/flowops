package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.application.ViewInsightsUseCase.Insight;
import com.flowops.aiinsight.application.port.TemplateHistoryPort;
import java.util.List;
import java.util.UUID;

public interface ProcessTemplateInsightSource {
    List<Insight> findIn(UUID templateId, TemplateHistoryPort.History history);
}
