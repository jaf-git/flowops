package com.flowops.chat.domain.model;

public sealed interface ThreadEntry permits Message, WorkMark {
    MessageId id();

    ConversationId conversation();
}
