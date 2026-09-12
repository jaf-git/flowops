package com.flowops.automation.application.detect;

import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import java.time.Instant;
import java.util.UUID;

public record Finding(NotificationKind kind, SubjectRef subject, UUID recipient, Instant episodeStartedAt) {
    public Finding {
        if (kind == null || subject == null || recipient == null || episodeStartedAt == null) {
            throw new IllegalArgumentException(
                    "a finding is a kind, what it is about, who can act on it, and when the episode began");
        }
    }

    public NoticeRequest asNotice() {
        return new NoticeRequest(kind, recipient, subject);
    }
}
