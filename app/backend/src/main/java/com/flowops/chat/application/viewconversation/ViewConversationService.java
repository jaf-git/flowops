package com.flowops.chat.application.viewconversation;

import com.flowops.chat.application.shared.exception.ConversationNotFoundException;
import com.flowops.chat.application.shared.exception.NotAuthenticatedException;
import com.flowops.chat.application.shared.port.ChatCallerPort;
import com.flowops.chat.application.shared.port.ChatDirectoryPort;
import com.flowops.chat.application.shared.port.ConversationStorePort;
import com.flowops.chat.application.shared.port.MessageStorePort;
import com.flowops.chat.domain.model.Conversation;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.Message;
import com.flowops.chat.domain.model.PersonId;
import com.flowops.chat.domain.model.ThreadEntry;
import com.flowops.chat.domain.model.WorkMark;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewConversationService implements ViewConversationUseCase {
    private static final int MAX_LIMIT = 100;

    private final ChatCallerPort caller;
    private final ConversationStorePort conversations;
    private final MessageStorePort messages;
    private final ChatDirectoryPort directory;

    public ViewConversationService(
            ChatCallerPort caller,
            ConversationStorePort conversations,
            MessageStorePort messages,
            ChatDirectoryPort directory) {
        this.caller = caller;
        this.conversations = conversations;
        this.messages = messages;
        this.directory = directory;
    }

    @Override
    @Transactional(readOnly = true)
    public ViewConversationResult execute(UUID conversationId, Optional<Long> beforeSeq, int limit) {
        PersonId me = PersonId.of(caller.currentCaller().orElseThrow(NotAuthenticatedException::new));
        Conversation conversation = participatedBy(conversationId, me);

        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));

        List<MessageStorePort.StoredEntry> page = messages.page(conversation.id(), beforeSeq, bounded + 1);
        boolean hasMore = page.size() > bounded;
        List<MessageStorePort.StoredEntry> shown = hasMore ? page.subList(0, bounded) : page;

        Map<UUID, ChatDirectoryPort.Person> authors =
                directory
                        .describeAll(shown.stream()
                                .map(stored -> whoseIs(stored.entry()))
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(ChatDirectoryPort.Person::userId, Function.identity(), (a, b) -> a));

        return new ViewConversationResult(
                shown.stream().map(stored -> row(stored, authors)).toList(), messages.currentCursor(), hasMore);
    }

    private static UUID whoseIs(ThreadEntry entry) {
        return switch (entry) {
            case Message message -> message.author().value();
            case WorkMark mark -> mark.actor().value();
        };
    }

    private ViewConversationResult.Row row(
            MessageStorePort.StoredEntry stored, Map<UUID, ChatDirectoryPort.Person> authors) {
        ChatDirectoryPort.Person person = authors.get(whoseIs(stored.entry()));
        String name = person == null ? null : person.displayName();

        return switch (stored.entry()) {
            case Message message -> new ViewConversationResult.Row(
                    message.id().value(),
                    message.author().value(),
                    name,
                    Optional.of(message.body()),
                    message.sentAt(),
                    message.editedAt(),
                    message.deletedAt(),
                    message.convertedTaskId(),
                    Optional.empty(),
                    stored.seq());
            case WorkMark mark -> new ViewConversationResult.Row(
                    mark.id().value(),
                    mark.actor().value(),
                    name,
                    Optional.empty(),
                    mark.markedAt(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(mark.subject()),
                    stored.seq());
        };
    }

    @Override
    @Transactional
    public void markRead(UUID conversationId, UUID throughMessageId) {
        PersonId me = PersonId.of(caller.currentCaller().orElseThrow(NotAuthenticatedException::new));
        Conversation conversation = participatedBy(conversationId, me);
        conversations.advanceReadMarker(conversation.id(), me, throughMessageId);
    }

    private Conversation participatedBy(UUID conversationId, PersonId me) {
        return conversations
                .findParticipatedBy(ConversationId.of(conversationId), me)
                .orElseThrow(ConversationNotFoundException::new);
    }
}
