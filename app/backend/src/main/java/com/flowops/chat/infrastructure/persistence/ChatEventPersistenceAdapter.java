package com.flowops.chat.infrastructure.persistence;

import com.flowops.chat.application.shared.port.AppendChatEventPort;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.MessageId;
import com.flowops.chat.domain.model.PersonId;
import com.flowops.chat.infrastructure.persistence.entity.ChatEventJpaEntity;
import com.flowops.chat.infrastructure.persistence.repository.ChatEventJpaRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatEventPersistenceAdapter implements AppendChatEventPort {
    private final ChatEventJpaRepository events;
    private final Clock clock;

    public ChatEventPersistenceAdapter(ChatEventJpaRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Override
    public void messageSent(ConversationId conversation, MessageId message, PersonId actor) {
        append(conversation, message, "MESSAGE_SENT", actor);
    }

    @Override
    public void messageEdited(ConversationId conversation, MessageId message, PersonId actor) {
        append(conversation, message, "MESSAGE_EDITED", actor);
    }

    @Override
    public void messageDeleted(ConversationId conversation, MessageId message, PersonId actor) {
        append(conversation, message, "MESSAGE_DELETED", actor);
    }

    @Override
    public void messageConverted(ConversationId conversation, MessageId message, PersonId actor) {
        append(conversation, message, "MESSAGE_CONVERTED", actor);
    }

    @Override
    public void workAssigned(ConversationId conversation, MessageId mark, PersonId actor) {
        append(conversation, mark, "WORK_ASSIGNED", actor);
    }

    private void append(ConversationId conversation, MessageId message, String action, PersonId actor) {
        events.save(new ChatEventJpaEntity(
                UUID.randomUUID(), conversation.value(), message.value(), action, actor.value(), clock.instant()));
    }
}
