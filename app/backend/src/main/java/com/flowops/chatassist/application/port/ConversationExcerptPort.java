package com.flowops.chatassist.application.port;

import com.flowops.chatassist.domain.ConversationExtract;
import java.util.UUID;

public interface ConversationExcerptPort {
    Excerpt of(UUID conversationId);

    record Excerpt(ConversationExtract conversation, java.util.Map<String, UUID> speakers) {}
}
