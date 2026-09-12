package com.flowops.discovery.api.dto;

import com.flowops.discovery.application.jobguess.GuessJobUseCase;
import java.util.UUID;

public record JobGuessResponse(UUID jobId, String name, boolean guessed) {
    public static JobGuessResponse of(GuessJobUseCase.Offer offer) {
        return new JobGuessResponse(offer.job().value(), offer.name(), offer.guessed());
    }
}
