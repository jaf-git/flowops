package com.flowops.chat.application.taskorigin;

import com.flowops.chat.application.shared.exception.ConversationNotFoundException;
import com.flowops.chat.application.shared.exception.NotAuthenticatedException;
import com.flowops.chat.application.shared.port.ChatCallerPort;
import com.flowops.chat.application.shared.port.ConversationStorePort;
import com.flowops.chat.application.shared.port.MessageStorePort;
import com.flowops.chat.domain.model.Message;
import com.flowops.chat.domain.model.PersonId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindTaskOriginService implements FindTaskOriginUseCase {
    private final ChatCallerPort caller;
    private final ConversationStorePort conversations;
    private final MessageStorePort messages;

    public FindTaskOriginService(
            ChatCallerPort caller, ConversationStorePort conversations, MessageStorePort messages) {
        this.caller = caller;
        this.conversations = conversations;
        this.messages = messages;
    }

    @Override
    @Transactional(readOnly = true)
    public Origin execute(UUID taskId) {
        PersonId me = PersonId.of(caller.currentCaller().orElseThrow(NotAuthenticatedException::new));

        Message message = messages.findConvertedInto(taskId)
                .map(MessageStorePort.Stored::message)
                .orElseThrow(ConversationNotFoundException::new);

        conversations.findParticipatedBy(message.conversation(), me).orElseThrow(ConversationNotFoundException::new);

        return new Origin(message.conversation().value(), message.id().value());
    }
}
