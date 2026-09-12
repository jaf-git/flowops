package com.flowops.discovery.api.dto;

import com.flowops.discovery.application.crossing.ComposeProcessUseCase;

public record ComposedResponse(String trackId, String processTemplateId) {
    public static ComposedResponse of(ComposeProcessUseCase.Composed composed) {
        return new ComposedResponse(
                composed.trackId().toString(), composed.processTemplateId().toString());
    }
}
