package com.flowops.notification.domain;

import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import java.time.Instant;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID recipient,
        NotificationKind kind,
        SubjectRef subject,
        NotificationState state,
        Instant createdAt,
        Instant deliverAfter,
        Instant deliveredAt,
        Instant readAt,
        Instant cancelledAt,
        CancelReason cancelReason) {
    public static Notification queued(UUID id, UUID recipient, NotificationKind kind, SubjectRef subject, Instant at) {
        return new Notification(
                id, recipient, kind, subject, NotificationState.QUEUED, at, null, null, null, null, null);
    }

    public static Notification held(
            UUID id, UUID recipient, NotificationKind kind, SubjectRef subject, Instant at, Instant deliverAfter) {
        return new Notification(
                id, recipient, kind, subject, NotificationState.HELD, at, deliverAfter, null, null, null, null);
    }

    public Notification delivered(Instant at) {
        refuseIfSettled("deliver");
        return new Notification(
                id,
                recipient,
                kind,
                subject,
                NotificationState.DELIVERED,
                createdAt,
                deliverAfter,
                at,
                null,
                null,
                null);
    }

    public Notification read(Instant at) {
        if (state == NotificationState.READ) {
            return this;
        }
        if (state != NotificationState.DELIVERED) {
            throw new IllegalStateException("a notification is read after it is delivered, not from " + state);
        }
        return new Notification(
                id,
                recipient,
                kind,
                subject,
                NotificationState.READ,
                createdAt,
                deliverAfter,
                deliveredAt,
                at,
                null,
                null);
    }

    public Notification cancelled(Instant at, CancelReason reason) {
        refuseIfSettled("cancel");
        return new Notification(
                id,
                recipient,
                kind,
                subject,
                NotificationState.CANCELLED,
                createdAt,
                deliverAfter,
                deliveredAt,
                readAt,
                at,
                reason);
    }

    public boolean waiting() {
        return state == NotificationState.HELD;
    }

    private void refuseIfSettled(String attempted) {
        if (state == NotificationState.CANCELLED || state == NotificationState.READ) {
            throw new IllegalStateException("cannot %s a notification that is already %s".formatted(attempted, state));
        }
    }
}
