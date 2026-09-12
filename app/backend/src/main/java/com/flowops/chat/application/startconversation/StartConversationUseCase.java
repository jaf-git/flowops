package com.flowops.chat.application.startconversation;

import com.flowops.chat.domain.model.Conversation;
import java.util.UUID;

public interface StartConversationUseCase {
    Conversation execute(UUID personId);
}
