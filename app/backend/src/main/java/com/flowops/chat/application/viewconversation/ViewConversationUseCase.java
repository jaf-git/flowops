package com.flowops.chat.application.viewconversation;

import java.util.Optional;
import java.util.UUID;

public interface ViewConversationUseCase {
    ViewConversationResult execute(UUID conversationId, Optional<Long> beforeSeq, int limit);

    void markRead(UUID conversationId, UUID throughMessageId);
}
