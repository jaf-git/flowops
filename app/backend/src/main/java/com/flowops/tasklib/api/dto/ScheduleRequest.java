package com.flowops.tasklib.api.dto;

import com.flowops.tasklib.domain.Recurrence;
import com.flowops.tasklib.domain.ScheduleCadence;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ScheduleRequest(
        @NotNull UUID assigneeId,
        @NotNull ScheduleCadence cadence,
        @Min(1) @Max(7) Integer dayOfWeek,
        @Min(1) @Max(31) Integer dayOfMonth) {
    public Recurrence toRecurrence() {
        return new Recurrence(cadence, dayOfWeek, dayOfMonth);
    }
}
