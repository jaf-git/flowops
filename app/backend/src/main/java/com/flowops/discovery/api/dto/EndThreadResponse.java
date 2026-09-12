package com.flowops.discovery.api.dto;

import com.flowops.discovery.application.endthread.EndThreadUseCase;

public record EndThreadResponse(String completeness, String closeReason) {
    public static EndThreadResponse of(EndThreadUseCase.Ended ended) {
        return new EndThreadResponse(
                ended.completeness().name(), ended.closeReason().name());
    }
}
