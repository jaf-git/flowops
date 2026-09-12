package com.flowops.discovery.api.dto;

import com.flowops.discovery.application.crossing.FormaliseWorkUseCase;

public record FormalisedResponse(String nodeId, String taskTemplateId) {
    public static FormalisedResponse of(FormaliseWorkUseCase.Formalised formalised) {
        return new FormalisedResponse(
                formalised.nodeId().toString(), formalised.taskTemplateId().toString());
    }
}
