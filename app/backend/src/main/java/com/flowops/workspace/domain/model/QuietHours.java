package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.exception.QuietHoursCoverTheDayException;
import java.time.LocalTime;
import java.util.Objects;

public record QuietHours(LocalTime start, LocalTime end) {
    public QuietHours {
        Objects.requireNonNull(start, "quiet hours have a start");
        Objects.requireNonNull(end, "quiet hours have an end");
        if (start.equals(end)) {
            throw new QuietHoursCoverTheDayException();
        }
    }

    public boolean covers(LocalTime moment) {
        if (wrapsMidnight()) {
            return !moment.isBefore(start) || moment.isBefore(end);
        }
        return !moment.isBefore(start) && moment.isBefore(end);
    }

    public boolean wrapsMidnight() {
        return end.isBefore(start);
    }
}
