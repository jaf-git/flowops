package com.flowops.notification.application.published;

import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import java.util.UUID;

public record NoticeRequest(NotificationKind kind, UUID recipient, SubjectRef subject) {
    public NoticeRequest {
        if (kind == null || recipient == null || subject == null) {
            throw new IllegalArgumentException("a notice needs a kind, one recipient and a subject");
        }
        if (kind.subjectKind() != subject.kind()) {
            throw new IllegalArgumentException(
                    "%s is about a %s, not a %s".formatted(kind, kind.subjectKind(), subject.kind()));
        }
    }
}
