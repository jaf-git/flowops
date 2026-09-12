package com.flowops.notification.application.shared.port;

import java.util.List;
import java.util.UUID;

public interface WeeklyRecipientsPort {
    List<UUID> everybodyWhoGetsTheWeekly();
}
