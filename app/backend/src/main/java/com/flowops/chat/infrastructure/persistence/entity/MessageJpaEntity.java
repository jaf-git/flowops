package com.flowops.chat.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message")
public class MessageJpaEntity {
    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "body")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private ThreadEntryKind kind;

    @Column(name = "work_subject_kind")
    private String workSubjectKind;

    @Column(name = "work_subject_id")
    private UUID workSubjectId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "converted_task_id")
    private UUID convertedTaskId;

    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    protected MessageJpaEntity() {}

    private MessageJpaEntity(
            UUID id,
            UUID conversationId,
            UUID authorId,
            ThreadEntryKind kind,
            String body,
            String workSubjectKind,
            UUID workSubjectId,
            Instant sentAt,
            Instant editedAt,
            Instant deletedAt,
            UUID convertedTaskId) {
        this.id = id;
        this.conversationId = conversationId;
        this.authorId = authorId;
        this.kind = kind;
        this.body = body;
        this.workSubjectKind = workSubjectKind;
        this.workSubjectId = workSubjectId;
        this.sentAt = sentAt;
        this.editedAt = editedAt;
        this.deletedAt = deletedAt;
        this.convertedTaskId = convertedTaskId;
    }

    public static MessageJpaEntity spoken(
            UUID id,
            UUID conversationId,
            UUID authorId,
            String body,
            Instant sentAt,
            Instant editedAt,
            Instant deletedAt,
            UUID convertedTaskId) {
        return new MessageJpaEntity(
                id,
                conversationId,
                authorId,
                ThreadEntryKind.SPOKEN,
                body,
                null,
                null,
                sentAt,
                editedAt,
                deletedAt,
                convertedTaskId);
    }

    public static MessageJpaEntity workMark(
            UUID id, UUID conversationId, UUID actorId, String workSubjectKind, UUID workSubjectId, Instant markedAt) {
        return new MessageJpaEntity(
                id,
                conversationId,
                actorId,
                ThreadEntryKind.WORK_MARK,
                null,
                workSubjectKind,
                workSubjectId,
                markedAt,
                null,
                null,
                null);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(Instant editedAt) {
        this.editedAt = editedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public UUID getConvertedTaskId() {
        return convertedTaskId;
    }

    public void setConvertedTaskId(UUID convertedTaskId) {
        this.convertedTaskId = convertedTaskId;
    }

    public Long getSeq() {
        return seq;
    }

    public ThreadEntryKind getKind() {
        return kind;
    }

    public String getWorkSubjectKind() {
        return workSubjectKind;
    }

    public UUID getWorkSubjectId() {
        return workSubjectId;
    }
}
