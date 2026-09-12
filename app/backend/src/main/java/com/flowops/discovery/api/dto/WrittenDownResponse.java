package com.flowops.discovery.api.dto;

import com.flowops.discovery.application.crossing.WriteItDownUseCase;
import java.util.List;

public record WrittenDownResponse(String recommendationId, String processTemplateId, String name, List<String> steps) {
    public static WrittenDownResponse of(WriteItDownUseCase.WrittenDown written) {
        return new WrittenDownResponse(
                written.recommendationId().toString(),
                written.processTemplateId().toString(),
                written.name(),
                written.steps());
    }
}
