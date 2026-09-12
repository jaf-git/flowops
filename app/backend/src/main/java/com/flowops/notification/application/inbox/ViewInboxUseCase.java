package com.flowops.notification.application.inbox;

import java.util.List;

public interface ViewInboxUseCase {
    List<InboxRow> execute();

    int unreadCount();
}
