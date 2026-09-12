package com.flowops.chat.application.managerooms;

import com.flowops.chat.application.shared.exception.CounterpartNotActiveException;
import com.flowops.chat.application.shared.exception.NotAuthenticatedException;
import com.flowops.chat.application.shared.exception.PersonNotFoundException;
import com.flowops.chat.application.shared.port.ChatCallerPort;
import com.flowops.chat.application.shared.port.ChatDirectoryPort;
import com.flowops.chat.application.shared.port.ConversationStorePort;
import com.flowops.chat.application.shared.port.MessageStorePort;
import com.flowops.chat.domain.enums.ConversationKind;
import com.flowops.chat.domain.model.Conversation;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.Message;
import com.flowops.chat.domain.model.MessageId;
import com.flowops.chat.domain.model.PersonId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageRoomsService implements ManageRoomsUseCase {
    private final ChatCallerPort caller;
    private final ChatDirectoryPort directory;
    private final ConversationStorePort conversations;
    private final MessageStorePort messages;
    private final Clock clock;

    public ManageRoomsService(
            ChatCallerPort caller,
            ChatDirectoryPort directory,
            ConversationStorePort conversations,
            MessageStorePort messages,
            Clock clock) {
        this.caller = caller;
        this.directory = directory;
        this.conversations = conversations;
        this.messages = messages;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Conversation startGroup(String name) {
        PersonId me = me();

        Conversation group = Conversation.groupNamed(
                ConversationId.of(UUID.randomUUID()), directory.currentWorkspaceId(), name, clock.instant());

        return conversations.open(group, List.of(me));
    }

    @Override
    @Transactional
    public void join(UUID conversationId) {
        PersonId me = me();
        Conversation room = mustFind(conversationId);

        if (room.kind() != ConversationKind.GROUP) {
            throw new ThatRoomIsNotYoursToJoinException(room.kind().name());
        }

        conversations.addParticipant(room.id(), me);
    }

    @Override
    @Transactional
    public void leave(UUID conversationId) {
        PersonId me = me();
        Conversation room = mustFind(conversationId);

        if (room.kind() != ConversationKind.GROUP) {
            throw new ThatRoomIsNotYoursToJoinException(room.kind().name());
        }

        conversations.removeParticipant(room.id(), me);
    }

    @Override
    @Transactional
    public void rename(UUID conversationId, String name) {
        Conversation room = mustFind(conversationId);

        if (room.kind() != ConversationKind.GROUP) {
            throw new ThatRoomIsNotYoursToJoinException(room.kind().name());
        }

        conversations.rename(room.id(), room.renamedTo(name).name().orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Participant> participants(UUID conversationId) {
        PersonId me = me();

        Conversation room = conversations
                .findParticipatedBy(ConversationId.of(conversationId), me)
                .orElseThrow(() -> new UnknownRoomException(conversationId));

        List<PersonId> members = conversations.participantsOf(room.id());

        return directory.describeAll(members.stream().map(PersonId::value).toList()).stream()
                .map(person -> new Participant(person.userId(), person.displayName(), person.active()))
                .toList();
    }

    @Override
    @Transactional
    public void addParticipant(UUID conversationId, UUID personId) {
        PersonId me = me();
        PersonId newcomer = PersonId.of(personId);

        Conversation room = conversations
                .findParticipatedBy(ConversationId.of(conversationId), me)
                .orElseThrow(() -> new UnknownRoomException(conversationId));

        if (room.kind() != ConversationKind.GROUP) {
            throw new ThatRoomIsNotYoursToJoinException(room.kind().name());
        }

        ChatDirectoryPort.Person person = directory.describe(personId).orElseThrow(PersonNotFoundException::new);

        if (!person.active()) {
            throw new CounterpartNotActiveException();
        }

        if (conversations.participantsOf(room.id()).contains(newcomer)) {
            return;
        }

        conversations.addParticipant(room.id(), newcomer);

        announce(room, me, person);
    }

    private void announce(Conversation room, PersonId by, ChatDirectoryPort.Person added) {
        String who = directory
                .describe(by.value())
                .map(ChatDirectoryPort.Person::displayName)
                .orElse("Somebody");

        messages.append(Message.sent(
                MessageId.of(UUID.randomUUID()),
                room.id(),
                by,
                who + " added " + added.displayName() + " to this room.",
                clock.instant()));
    }

    @Override
    @Transactional
    public Conversation announcements() {
        UUID workspace = directory.currentWorkspaceId();

        return conversations
                .findAnnouncements(workspace)
                .orElseGet(() -> conversations.open(
                        Conversation.announcementsFor(ConversationId.of(UUID.randomUUID()), workspace, clock.instant()),
                        List.of()));
    }

    @Transactional
    public Conversation channelForRole(UUID workspaceId, UUID functionalRoleId, String roleName) {
        return conversations
                .findChannelForRole(workspaceId, functionalRoleId)
                .orElseGet(() -> conversations.open(
                        Conversation.channelForRole(
                                ConversationId.of(UUID.randomUUID()),
                                workspaceId,
                                functionalRoleId,
                                roleName,
                                clock.instant()),
                        List.of()));
    }

    @Transactional
    public void renameChannelForRole(UUID workspaceId, UUID functionalRoleId, String roleName) {
        conversations
                .findChannelForRole(workspaceId, functionalRoleId)
                .ifPresent(room -> conversations.rename(room.id(), roleName));
    }

    @Transactional
    public void roleMembershipChanged(UUID workspaceId, UUID personId, UUID nowHolds, UUID previouslyHeld) {
        if (previouslyHeld != null && !previouslyHeld.equals(nowHolds)) {
            conversations
                    .findChannelForRole(workspaceId, previouslyHeld)
                    .ifPresent(room -> conversations.removeParticipant(room.id(), PersonId.of(personId)));
        }

        if (nowHolds != null) {
            conversations
                    .findChannelForRole(workspaceId, nowHolds)
                    .ifPresent(room -> conversations.addParticipant(room.id(), PersonId.of(personId)));
        }
    }

    private PersonId me() {
        return PersonId.of(caller.currentCaller().orElseThrow(NotAuthenticatedException::new));
    }

    private Conversation mustFind(UUID conversationId) {
        return conversations
                .find(ConversationId.of(conversationId))
                .orElseThrow(() -> new UnknownRoomException(conversationId));
    }
}
