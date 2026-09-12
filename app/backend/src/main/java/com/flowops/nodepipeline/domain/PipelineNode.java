package com.flowops.nodepipeline.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PipelineNode(
        String id,
        String jobId,
        String text,
        String detail,
        UUID creatorId,
        UUID markerId,
        String kind,
        String creatorRole,
        String performerRole,
        LocalDate createdAt,
        Closure closure,
        String direction,
        boolean postClose,
        String outputType,
        String workType,
        String taskTemplateId,
        boolean disrupted,
        String conversationId,
        String precedingRole,
        String precedingDirection,
        String followingRole,
        Integer positionInTrack,
        String title,
        List<String> checklist,
        String activitySlug,
        String activityName,
        Instant markedAt) {
    public PipelineNode(
            String id,
            String jobId,
            String text,
            String detail,
            UUID creatorId,
            UUID markerId,
            String kind,
            String creatorRole,
            String performerRole,
            LocalDate createdAt,
            Closure closure,
            String direction,
            boolean postClose,
            String outputType,
            String workType,
            String taskTemplateId,
            boolean disrupted,
            String conversationId,
            String precedingRole,
            String precedingDirection,
            String followingRole,
            Integer positionInTrack,
            String title,
            List<String> checklist,
            String activitySlug,
            String activityName) {
        this(
                id,
                jobId,
                text,
                detail,
                creatorId,
                markerId,
                kind,
                creatorRole,
                performerRole,
                createdAt,
                closure,
                direction,
                postClose,
                outputType,
                workType,
                taskTemplateId,
                disrupted,
                conversationId,
                precedingRole,
                precedingDirection,
                followingRole,
                positionInTrack,
                title,
                checklist,
                activitySlug,
                activityName,
                null);
    }

    public PipelineNode(
            String id,
            String jobId,
            String text,
            String detail,
            UUID creatorId,
            UUID markerId,
            String kind,
            String creatorRole,
            String performerRole,
            LocalDate createdAt,
            Closure closure,
            String direction,
            boolean postClose,
            String outputType,
            String workType,
            String taskTemplateId,
            boolean disrupted,
            String conversationId,
            String precedingRole,
            String precedingDirection,
            String followingRole,
            Integer positionInTrack,
            String title,
            List<String> checklist) {
        this(
                id,
                jobId,
                text,
                detail,
                creatorId,
                markerId,
                kind,
                creatorRole,
                performerRole,
                createdAt,
                closure,
                direction,
                postClose,
                outputType,
                workType,
                taskTemplateId,
                disrupted,
                conversationId,
                precedingRole,
                precedingDirection,
                followingRole,
                positionInTrack,
                title,
                checklist,
                null,
                null);
    }

    public PipelineNode(
            String id,
            String jobId,
            String text,
            String detail,
            UUID creatorId,
            UUID markerId,
            String kind,
            String creatorRole,
            String performerRole,
            LocalDate createdAt,
            Closure closure,
            String direction,
            boolean postClose,
            String outputType,
            String workType,
            String taskTemplateId,
            boolean disrupted,
            String conversationId,
            String precedingRole,
            String precedingDirection,
            String followingRole,
            Integer positionInTrack) {
        this(
                id,
                jobId,
                text,
                detail,
                creatorId,
                markerId,
                kind,
                creatorRole,
                performerRole,
                createdAt,
                closure,
                direction,
                postClose,
                outputType,
                workType,
                taskTemplateId,
                disrupted,
                conversationId,
                precedingRole,
                precedingDirection,
                followingRole,
                positionInTrack,
                null,
                null);
    }

    public boolean namesAnActivity() {
        return activitySlug != null && !activitySlug.isBlank();
    }

    public String describedAs() {
        return title == null || title.isBlank() ? text : title;
    }

    public boolean isDescribed() {
        return title != null && !title.isBlank();
    }

    public enum Closure {
        MARKED,

        LAPSED,

        DROPPED,

        UNKNOWN
    }

    public boolean isBoundary() {
        return "JOB_START".equals(kind) || "JOB_END".equals(kind);
    }

    public boolean isConversation() {
        return "QUERY".equals(direction) || "ANSWERED".equals(direction);
    }

    public boolean diedRatherThanFinished() {
        return closure == Closure.LAPSED || closure == Closure.DROPPED || closure == Closure.UNKNOWN;
    }

    public UUID address() {
        return markerId != null ? markerId : creatorId;
    }
}
