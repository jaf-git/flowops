package com.flowops.notification.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

public record QuietHours(LocalTime from, LocalTime to, ZoneId zone) {
    public boolean covers(Instant at) {
        LocalTime time = at.atZone(zone).toLocalTime();
        if (from.equals(to)) {
            return true;
        }
        if (from.isAfter(to)) {
            return !time.isBefore(from) || time.isBefore(to);
        }
        return !time.isBefore(from) && time.isBefore(to);
    }
}
