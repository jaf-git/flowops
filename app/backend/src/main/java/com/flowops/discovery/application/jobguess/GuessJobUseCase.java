package com.flowops.discovery.application.jobguess;

import com.flowops.discovery.domain.model.JobId;
import java.util.List;
import java.util.UUID;

public interface GuessJobUseCase {
    List<Offer> execute(UUID conversationId);

    List<Offer> describing(UUID conversationId);

    record Offer(JobId job, String name, boolean guessed) {}
}
