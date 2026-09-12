package com.flowops.chat.application.viewconversations;

import com.flowops.chat.application.managerooms.ManageRoomsUseCase;
import com.flowops.chat.application.shared.exception.NotAuthenticatedException;
import com.flowops.chat.application.shared.port.ChatCallerPort;
import com.flowops.chat.application.shared.port.ChatDirectoryPort;
import com.flowops.chat.application.shared.port.ConversationStorePort;
import com.flowops.chat.application.shared.port.MessageStorePort;
import com.flowops.chat.domain.enums.ConversationKind;
import com.flowops.chat.domain.model.Conversation;
import com.flowops.chat.domain.model.PersonId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewConversationsService implements ViewConversationsUseCase {
    private final ChatCallerPort caller;
    private final ConversationStorePort conversations;
    private final MessageStorePort messages;
    private final ChatDirectoryPort directory;
    private final ManageRoomsUseCase rooms;

    public ViewConversationsService(
            ChatCallerPort caller,
            ConversationStorePort conversations,
            MessageStorePort messages,
            ChatDirectoryPort directory,
            ManageRoomsUseCase rooms) {
        this.caller = caller;
        this.conversations = conversations;
        this.messages = messages;
        this.directory = directory;
        this.rooms = rooms;
    }

    @Override
    @Transactional
    public ViewConversationsResult execute() {
        PersonId me = PersonId.of(caller.currentCaller().orElseThrow(NotAuthenticatedException::new));

        conversations.addParticipant(rooms.announcements().id(), me);

        List<ConversationStorePort.Participation> mine = conversations.participationsOf(me);

        Map<UUID, ChatDirectoryPort.Person> people = directory
                .describeAll(mine.stream()
                        .map(participation -> participation.conversation().counterpartOf(me))
                        .flatMap(Optional::stream)
                        .map(PersonId::value)
                        .toList())
                .stream()
                .collect(Collectors.toMap(ChatDirectoryPort.Person::userId, Function.identity(), (a, b) -> a));

        List<ViewConversationsResult.Row> rows = mine.stream()
                .map(participation -> row(participation, me, people))
                .sorted(Comparator.comparing((ViewConversationsResult.Row row) ->
                                row.kind() == ConversationKind.ANNOUNCEMENT ? 0 : 1)
                        .thenComparing(
                                row -> row.lastMessageAt().orElse(java.time.Instant.EPOCH), Comparator.reverseOrder()))
                .toList();

        return new ViewConversationsResult(rows, joinableBy(mine), messages.currentCursor());
    }

    private List<ViewConversationsResult.Room> joinableBy(List<ConversationStorePort.Participation> mine) {
        Set<UUID> alreadyIn = mine.stream()
                .map(participation -> participation.conversation().id().value())
                .collect(Collectors.toSet());

        return conversations.openRooms(directory.currentWorkspaceId()).stream()
                .filter(room -> !alreadyIn.contains(room.id().value()))
                .map(room -> new ViewConversationsResult.Room(
                        room.id().value(), room.name().orElse("")))
                .toList();
    }

    private ViewConversationsResult.Row row(
            ConversationStorePort.Participation participation,
            PersonId me,
            Map<UUID, ChatDirectoryPort.Person> people) {
        Conversation conversation = participation.conversation();
        Optional<PersonId> counterpart = conversation.counterpartOf(me);
        Optional<ChatDirectoryPort.Person> person =
                counterpart.map(PersonId::value).map(people::get).filter(java.util.Objects::nonNull);

        return new ViewConversationsResult.Row(
                conversation.id().value(),
                conversation.kind(),
                counterpart.map(PersonId::value),
                person.map(ChatDirectoryPort.Person::displayName),
                conversation.kind() == ConversationKind.DIRECT
                        ? person.map(ChatDirectoryPort.Person::active).orElse(false)
                        : true,
                conversation.name(),
                participation.lastMessagePreview(),
                participation.lastMessageDeleted(),
                participation.lastMessageAt(),
                participation.unreadCount());
    }
}
