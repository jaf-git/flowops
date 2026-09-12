package com.flowops.tasklib.domain;

import com.flowops.tasklib.domain.exception.IllegalTemplateTransitionException;
import java.time.Instant;
import java.util.UUID;

public record TaskTemplate(
        UUID id,
        TemplateDetails details,
        TemplateStatus status,
        UUID authorId,
        int timesUsed,
        String rejectionReason,
        UUID convertedToProcessTemplateId,
        TemplateMetadata metadata,
        Instant createdAt,
        Instant updatedAt,
        Instant approvedAt,
        boolean discoveredByPipeline) {
    public TaskTemplate {
        metadata = metadata == null ? TemplateMetadata.empty() : metadata;
    }

    public static TaskTemplate written(TemplateDetails details, UUID authorId, boolean submitForApproval, Instant now) {
        return written(details, TemplateMetadata.empty(), authorId, submitForApproval, now);
    }

    public static TaskTemplate written(
            TemplateDetails details, TemplateMetadata known, UUID authorId, boolean submitForApproval, Instant now) {
        return new TaskTemplate(
                UUID.randomUUID(),
                details,
                submitForApproval ? TemplateStatus.PROPOSED : TemplateStatus.DRAFT,
                authorId,
                0,
                null,
                null,
                known,
                now,
                now,
                null,
                false);
    }

    public boolean isUsable() {
        return status == TemplateStatus.APPROVED;
    }

    public boolean awaitsApproval() {
        return status == TemplateStatus.PROPOSED;
    }

    public boolean isDraft() {
        return status == TemplateStatus.DRAFT;
    }

    public boolean belongsTo(UUID person) {
        return authorId.equals(person);
    }

    public TaskTemplate revisedTo(TemplateDetails revised, Instant now) {
        return new TaskTemplate(
                id,
                revised,
                status,
                authorId,
                timesUsed,
                rejectionReason,
                convertedToProcessTemplateId,
                metadata,
                createdAt,
                now,
                approvedAt,
                discoveredByPipeline);
    }

    public TaskTemplate describedBy(TemplateMetadata answered, Instant now) {
        return new TaskTemplate(
                id,
                details,
                status,
                authorId,
                timesUsed,
                rejectionReason,
                convertedToProcessTemplateId,
                answered,
                createdAt,
                now,
                approvedAt,
                discoveredByPipeline);
    }

    public TaskTemplate approved(Instant now) {
        if (!awaitsApproval()) {
            throw new IllegalTemplateTransitionException("only a template awaiting approval can be approved");
        }
        return new TaskTemplate(
                id,
                details,
                TemplateStatus.APPROVED,
                authorId,
                timesUsed,
                null,
                convertedToProcessTemplateId,
                metadata,
                createdAt,
                now,
                approvedAt == null ? now : approvedAt,
                discoveredByPipeline);
    }

    public TaskTemplate sentBack(String reason, Instant now) {
        if (!awaitsApproval()) {
            throw new IllegalTemplateTransitionException("only a template awaiting approval can be sent back");
        }
        return movedTo(TemplateStatus.DRAFT, reason, now);
    }

    public TaskTemplate submitted(Instant now) {
        if (status != TemplateStatus.DRAFT) {
            throw new IllegalTemplateTransitionException("only a draft can be submitted for approval");
        }
        return movedTo(TemplateStatus.PROPOSED, null, now);
    }

    public TaskTemplate retired(Instant now) {
        if (status == TemplateStatus.RETIRED) {
            throw new IllegalTemplateTransitionException("this template is already retired");
        }
        return movedTo(TemplateStatus.RETIRED, rejectionReason, now);
    }

    public void requireUsable() {
        if (!isUsable()) {
            throw new IllegalTemplateTransitionException("only an approved template can be used");
        }
    }

    public TaskTemplate convertedTo(UUID processTemplateId, Instant now) {
        if (status == TemplateStatus.RETIRED) {
            throw new IllegalTemplateTransitionException("this template is already retired");
        }
        return new TaskTemplate(
                id,
                details,
                TemplateStatus.RETIRED,
                authorId,
                timesUsed,
                rejectionReason,
                processTemplateId,
                metadata,
                createdAt,
                now,
                approvedAt,
                discoveredByPipeline);
    }

    public TaskTemplate alsoRunsIn(UUID processTemplateId, Instant now) {
        if (status == TemplateStatus.RETIRED) {
            throw new IllegalTemplateTransitionException("this template is already retired");
        }
        return new TaskTemplate(
                id,
                details,
                status,
                authorId,
                timesUsed,
                rejectionReason,
                processTemplateId,
                metadata,
                createdAt,
                now,
                approvedAt,
                discoveredByPipeline);
    }

    private TaskTemplate movedTo(TemplateStatus next, String reason, Instant now) {
        return new TaskTemplate(
                id,
                details,
                next,
                authorId,
                timesUsed,
                reason,
                convertedToProcessTemplateId,
                metadata,
                createdAt,
                now,
                approvedAt,
                discoveredByPipeline);
    }
}
