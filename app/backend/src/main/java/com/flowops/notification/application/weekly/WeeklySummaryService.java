package com.flowops.notification.application.weekly;

import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.published.NotifyUseCase;
import com.flowops.notification.application.shared.port.WeeklyRecipientsPort;
import com.flowops.notification.application.shared.port.WeeklySectionPort;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklySummaryService implements WeeklySummaryUseCase {
    private final List<WeeklySectionPort> sections;
    private final WeeklyRecipientsPort recipients;
    private final NotifyUseCase notify;
    private final WorkspaceIdentityPort workspace;

    public WeeklySummaryService(
            List<WeeklySectionPort> sections,
            WeeklyRecipientsPort recipients,
            NotifyUseCase notify,
            WorkspaceIdentityPort workspace) {
        this.sections = List.copyOf(sections);
        this.recipients = recipients;
        this.notify = notify;
        this.workspace = workspace;
    }

    @Override
    @Transactional
    public int raise() {
        boolean anythingToSay = sections.stream().anyMatch(section -> section.count() > 0);
        if (!anythingToSay) {
            return 0;
        }

        UUID subject = workspace.current();
        int raised = 0;
        for (UUID recipient : recipients.everybodyWhoGetsTheWeekly()) {
            notify.raise(new NoticeRequest(NotificationKind.WEEKLY_SUMMARY, recipient, SubjectRef.workspace(subject)));
            raised++;
        }
        return raised;
    }
}
