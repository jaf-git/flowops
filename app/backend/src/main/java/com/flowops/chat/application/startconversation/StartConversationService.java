package com.flowops.chat.application.startconversation;

import com.flowops.chat.application.shared.exception.CounterpartNotActiveException;
import com.flowops.chat.application.shared.exception.NotAuthenticatedException;
import com.flowops.chat.application.shared.exception.PersonNotFoundException;
import com.flowops.chat.application.shared.port.ChatCallerPort;
import com.flowops.chat.application.shared.port.ChatDirectoryPort;
import com.flowops.chat.application.shared.port.ConversationStorePort;
import com.flowops.chat.domain.model.Conversation;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.PersonId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartConversationService implements StartConversationUseCase {
    private final ChatCallerPort caller;
    private final ChatDirectoryPort directory;
    private final ConversationStorePort conversations;
    private final Clock clock;

    public StartConversationService(
            ChatCallerPort caller, ChatDirectoryPort directory, ConversationStorePort conversations, Clock clock) {
        this.caller = caller;
        this.directory = directory;
        this.conversations = conversations;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Conversation execute(UUID personId) {
        PersonId me = PersonId.of(caller.currentCaller().orElseThrow(NotAuthenticatedException::new));

        ChatDirectoryPort.Person counterpart = directory.describe(personId).orElseThrow(PersonNotFoundException::new);
        if (!counterpart.active()) {
            throw new CounterpartNotActiveException();
        }

        PersonId them = PersonId.of(counterpart.userId());
        UUID workspace = directory.currentWorkspaceId();

        return conversations
                .findDirectBetween(workspace, me, them)
                .orElseGet(() -> conversations.open(
                        Conversation.directBetween(
                                ConversationId.of(UUID.randomUUID()), workspace, me, them, clock.instant()),
                        List.of(me, them)));
    }
}
