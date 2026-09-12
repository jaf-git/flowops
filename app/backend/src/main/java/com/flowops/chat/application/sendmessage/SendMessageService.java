package com.flowops.chat.application.sendmessage;

import com.flowops.chat.application.shared.exception.ConversationNotFoundException;
import com.flowops.chat.application.shared.exception.NotAuthenticatedException;
import com.flowops.chat.application.shared.port.AppendChatEventPort;
import com.flowops.chat.application.shared.port.ChatCallerPort;
import com.flowops.chat.application.shared.port.ConversationStorePort;
import com.flowops.chat.application.shared.port.MessageStorePort;
import com.flowops.chat.domain.model.Conversation;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.Message;
import com.flowops.chat.domain.model.MessageId;
import com.flowops.chat.domain.model.PersonId;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SendMessageService implements SendMessageUseCase {
    private final ChatCallerPort caller;
    private final ConversationStorePort conversations;
    private final MessageStorePort messages;
    private final AppendChatEventPort events;
    private final Clock clock;

    public SendMessageService(
            ChatCallerPort caller,
            ConversationStorePort conversations,
            MessageStorePort messages,
            AppendChatEventPort events,
            Clock clock) {
        this.caller = caller;
        this.conversations = conversations;
        this.messages = messages;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MessageStorePort.Stored execute(UUID conversationId, String body) {
        PersonId me = PersonId.of(caller.currentCaller().orElseThrow(NotAuthenticatedException::new));
        ConversationId conversation = ConversationId.of(conversationId);

        Conversation found =
                conversations.findParticipatedBy(conversation, me).orElseThrow(ConversationNotFoundException::new);

        MessageStorePort.Stored stored =
                messages.append(Message.sent(MessageId.of(UUID.randomUUID()), found.id(), me, body, clock.instant()));

        events.messageSent(found.id(), stored.message().id(), me);
        return stored;
    }
}
