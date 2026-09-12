package com.flowops.chatassist.application;

import com.flowops.chatassist.domain.ProposedWork;
import java.util.Optional;
import java.util.UUID;

public interface SuggestWorkFromConversationUseCase {
    Optional<ProposedWork> forConversation(UUID conversationId);

    boolean isAvailable();
}
