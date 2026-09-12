package com.flowops.chat.infrastructure.persistence;

import com.flowops.chat.application.shared.port.ConversationStorePort;
import com.flowops.chat.application.shared.port.MessageStorePort;
import com.flowops.chat.domain.enums.ConversationKind;
import com.flowops.chat.domain.model.Conversation;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.Message;
import com.flowops.chat.domain.model.MessageId;
import com.flowops.chat.domain.model.PersonId;
import com.flowops.chat.domain.model.ThreadEntry;
import com.flowops.chat.domain.model.WorkMark;
import com.flowops.chat.domain.model.WorkSubject;
import com.flowops.chat.domain.model.WorkSubjectKind;
import com.flowops.chat.infrastructure.persistence.entity.ConversationJpaEntity;
import com.flowops.chat.infrastructure.persistence.entity.ConversationParticipantJpaEntity;
import com.flowops.chat.infrastructure.persistence.entity.MessageJpaEntity;
import com.flowops.chat.infrastructure.persistence.entity.ThreadEntryKind;
import com.flowops.chat.infrastructure.persistence.repository.ConversationJpaRepository;
import com.flowops.chat.infrastructure.persistence.repository.ConversationParticipantJpaRepository;
import com.flowops.chat.infrastructure.persistence.repository.MessageJpaRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class ChatPersistenceAdapter implements ConversationStorePort, MessageStorePort {
    private static final int PREVIEW_LENGTH = 140;

    private final ConversationJpaRepository conversations;
    private final ConversationParticipantJpaRepository participants;
    private final MessageJpaRepository messages;
    private final EntityManager entityManager;

    public ChatPersistenceAdapter(
            ConversationJpaRepository conversations,
            ConversationParticipantJpaRepository participants,
            MessageJpaRepository messages,
            EntityManager entityManager) {
        this.conversations = conversations;
        this.participants = participants;
        this.messages = messages;
        this.entityManager = entityManager;
    }

    @Override
    public List<Participation> participationsOf(PersonId caller) {
        return participants.findByKeyPersonId(caller.value()).stream()
                .map(row -> conversations
                        .findById(row.getConversationId())
                        .map(conversation -> participation(conversation, row))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Participation participation(ConversationJpaEntity conversation, ConversationParticipantJpaEntity row) {
        Optional<MessageJpaEntity> latest = messages.findFirstByConversationIdOrderBySeqDesc(conversation.getId());
        Optional<MessageJpaEntity> spoken =
                messages.findFirstByConversationIdAndKindOrderBySeqDesc(conversation.getId(), ThreadEntryKind.SPOKEN);
        boolean deleted = spoken.map(message -> message.getDeletedAt() != null).orElse(false);

        return new Participation(
                domain(conversation),
                latest.map(MessageJpaEntity::getId),
                deleted ? Optional.empty() : spoken.map(message -> preview(message.getBody())),
                latest.map(MessageJpaEntity::getSentAt),
                deleted,
                messages.countUnread(conversation.getId(), row.getPersonId(), row.getLastReadMessageId()));
    }

    private static String preview(String body) {
        return body.length() <= PREVIEW_LENGTH ? body : body.substring(0, PREVIEW_LENGTH);
    }

    @Override
    public Optional<Conversation> findParticipatedBy(ConversationId conversation, PersonId caller) {
        return participants
                .findByKeyConversationIdAndKeyPersonId(conversation.value(), caller.value())
                .flatMap(row -> conversations.findById(conversation.value()))
                .map(ChatPersistenceAdapter::domain);
    }

    @Override
    public Optional<Conversation> findDirectBetween(UUID workspaceId, PersonId one, PersonId other) {
        boolean oneIsLower = one.compareTo(other) < 0;
        UUID lo = oneIsLower ? one.value() : other.value();
        UUID hi = oneIsLower ? other.value() : one.value();

        return conversations
                .findByWorkspaceIdAndParticipantLoAndParticipantHi(workspaceId, lo, hi)
                .map(ChatPersistenceAdapter::domain);
    }

    @Override
    public Conversation open(Conversation conversation, List<PersonId> people) {
        conversations.save(new ConversationJpaEntity(
                conversation.id().value(),
                conversation.workspaceId(),
                conversation.kind().name(),
                conversation.createdAt(),
                conversation.participantLo().map(PersonId::value).orElse(null),
                conversation.participantHi().map(PersonId::value).orElse(null),
                null,
                conversation.functionalRoleId().orElse(null),
                conversation.name().orElse(null)));

        people.forEach(person -> participants.save(
                new ConversationParticipantJpaEntity(conversation.id().value(), person.value(), null)));

        return conversation;
    }

    @Override
    public void advanceReadMarker(ConversationId conversation, PersonId caller, UUID throughMessageId) {
        participants
                .findByKeyConversationIdAndKeyPersonId(conversation.value(), caller.value())
                .ifPresent(row -> {
                    long claimed = seqOf(throughMessageId);
                    long stored = seqOf(row.getLastReadMessageId());
                    if (claimed > stored) {
                        row.setLastReadMessageId(throughMessageId);
                        participants.save(row);
                    }
                });
    }

    private long seqOf(UUID message) {
        if (message == null) {
            return 0L;
        }
        return messages.findById(message).map(MessageJpaEntity::getSeq).orElse(0L);
    }

    @Override
    public List<PersonId> participantsOf(ConversationId conversation) {
        return participants.findByKeyConversationId(conversation.value()).stream()
                .map(row -> PersonId.of(row.getPersonId()))
                .toList();
    }

    @Override
    public Stored append(Message message) {
        MessageJpaEntity saved = messages.saveAndFlush(MessageJpaEntity.spoken(
                message.id().value(),
                message.conversation().value(),
                message.author().value(),
                message.body(),
                message.sentAt(),
                message.editedAt().orElse(null),
                message.deletedAt().orElse(null),
                message.convertedTaskId().orElse(null)));

        entityManager.refresh(saved);
        return new Stored(message, saved.getSeq());
    }

    @Override
    public StoredEntry appendMark(WorkMark mark) {
        MessageJpaEntity saved = messages.saveAndFlush(MessageJpaEntity.workMark(
                mark.id().value(),
                mark.conversation().value(),
                mark.actor().value(),
                mark.subject().kind().name(),
                mark.subject().id(),
                mark.markedAt()));

        entityManager.refresh(saved);
        return new StoredEntry(mark, saved.getSeq());
    }

    @Override
    public Optional<Stored> find(MessageId message) {
        return messages.findById(message.value())
                .filter(entity -> entity.getKind() == ThreadEntryKind.SPOKEN)
                .map(ChatPersistenceAdapter::stored);
    }

    @Override
    public Optional<Stored> findConvertedInto(UUID taskId) {
        return messages.findByConvertedTaskId(taskId).map(ChatPersistenceAdapter::stored);
    }

    @Override
    public void update(Message message) {
        messages.findById(message.id().value()).ifPresent(entity -> {
            entity.setBody(message.body());
            entity.setEditedAt(message.editedAt().orElse(null));
            entity.setDeletedAt(message.deletedAt().orElse(null));
            entity.setConvertedTaskId(message.convertedTaskId().orElse(null));
            messages.save(entity);
        });
    }

    @Override
    public List<StoredEntry> page(ConversationId conversation, Optional<Long> beforeSeq, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<MessageJpaEntity> found = beforeSeq
                .map(before ->
                        messages.findByConversationIdAndSeqLessThanOrderBySeqDesc(conversation.value(), before, page))
                .orElseGet(() -> messages.findByConversationIdOrderBySeqDesc(conversation.value(), page));

        return found.stream()
                .map(entity -> new StoredEntry(entry(entity), entity.getSeq()))
                .toList();
    }

    private static ThreadEntry entry(MessageJpaEntity entity) {
        if (entity.getKind() == ThreadEntryKind.WORK_MARK) {
            return WorkMark.rehydrated(
                    MessageId.of(entity.getId()),
                    ConversationId.of(entity.getConversationId()),
                    PersonId.of(entity.getAuthorId()),
                    new WorkSubject(WorkSubjectKind.valueOf(entity.getWorkSubjectKind()), entity.getWorkSubjectId()),
                    entity.getSentAt());
        }
        return stored(entity).message();
    }

    @Override
    public long currentCursor() {
        return messages.currentCursor();
    }

    private static Stored stored(MessageJpaEntity entity) {
        return new Stored(
                Message.rehydrated(
                        MessageId.of(entity.getId()),
                        ConversationId.of(entity.getConversationId()),
                        PersonId.of(entity.getAuthorId()),
                        entity.getBody(),
                        entity.getSentAt(),
                        entity.getEditedAt(),
                        entity.getDeletedAt(),
                        entity.getConvertedTaskId()),
                entity.getSeq());
    }

    @Override
    public Optional<Conversation> find(ConversationId conversation) {
        return conversations.findById(conversation.value()).map(ChatPersistenceAdapter::domain);
    }

    @Override
    public Optional<Conversation> findChannelForRole(UUID workspaceId, UUID functionalRoleId) {
        return conversations
                .findByWorkspaceIdAndFunctionalRoleId(workspaceId, functionalRoleId)
                .map(ChatPersistenceAdapter::domain);
    }

    @Override
    public Optional<Conversation> findAnnouncements(UUID workspaceId) {
        return conversations
                .findByWorkspaceIdAndKind(workspaceId, ConversationKind.ANNOUNCEMENT.name())
                .map(ChatPersistenceAdapter::domain);
    }

    @Override
    public void addParticipant(ConversationId conversation, PersonId person) {
        if (participants
                .findByKeyConversationIdAndKeyPersonId(conversation.value(), person.value())
                .isEmpty()) {
            participants.save(new ConversationParticipantJpaEntity(conversation.value(), person.value(), null));
        }
    }

    @Override
    public void removeParticipant(ConversationId conversation, PersonId person) {
        participants
                .findByKeyConversationIdAndKeyPersonId(conversation.value(), person.value())
                .ifPresent(participants::delete);
    }

    @Override
    public void rename(ConversationId conversation, String name) {
        conversations
                .findById(conversation.value())
                .ifPresent(row -> conversations.save(new ConversationJpaEntity(
                        row.getId(),
                        row.getWorkspaceId(),
                        row.getKind(),
                        row.getCreatedAt(),
                        row.getParticipantLo(),
                        row.getParticipantHi(),
                        row.getTeamManagerId(),
                        row.getFunctionalRoleId(),
                        name)));
    }

    @Override
    public List<Conversation> openRooms(UUID workspaceId) {
        return conversations.findByWorkspaceIdAndKindOrderByNameAsc(workspaceId, ConversationKind.GROUP.name()).stream()
                .map(ChatPersistenceAdapter::domain)
                .toList();
    }

    private static Conversation domain(ConversationJpaEntity entity) {
        return Conversation.rehydrated(
                ConversationId.of(entity.getId()),
                entity.getWorkspaceId(),
                ConversationKind.valueOf(entity.getKind()),
                entity.getCreatedAt(),
                Optional.ofNullable(entity.getParticipantLo()).map(PersonId::of).orElse(null),
                Optional.ofNullable(entity.getParticipantHi()).map(PersonId::of).orElse(null),
                entity.getFunctionalRoleId(),
                entity.getName());
    }
}
