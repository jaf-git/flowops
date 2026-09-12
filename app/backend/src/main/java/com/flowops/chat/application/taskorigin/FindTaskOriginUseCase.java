package com.flowops.chat.application.taskorigin;

import java.util.UUID;

public interface FindTaskOriginUseCase {
    Origin execute(UUID taskId);

    record Origin(UUID conversationId, UUID messageId) {}
}
