package com.flowops.notification.api.dto;

import com.flowops.notification.application.inbox.InboxRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "One notification, or a digest standing for several released together.")
public record NotificationRow(
        @Schema(description = "Absent on a digest row, which stands for its items rather than for itself.") UUID id,
        @Schema(description = "The catalogue kind. The screen composes the sentence from it.") String kind,
        @Schema(description = "Which switch would have silenced it. ESCALATION has none.") String group,
        String subjectKind,
        @Schema(description = "Null when the subject is gone; the row then renders as unavailable.") UUID subjectId,
        Instant createdAt,
        @Schema(description = "Null while unread.") Instant readAt,
        @Schema(description = "Non-empty only on a digest row.") List<NotificationRow> items) {
    public static NotificationRow of(InboxRow row) {
        return new NotificationRow(
                row.id(),
                row.kind() == null ? null : row.kind().name(),
                row.kind() == null ? null : row.kind().group().name(),
                row.kind() == null ? null : row.kind().subjectKind().name(),
                row.subjectId(),
                row.createdAt(),
                row.readAt(),
                row.items().stream().map(NotificationRow::of).toList());
    }
}
