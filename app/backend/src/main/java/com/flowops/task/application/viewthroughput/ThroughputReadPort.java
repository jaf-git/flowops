package com.flowops.task.application.viewthroughput;

import com.flowops.task.application.shared.TaskAudience;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public interface ThroughputReadPort {
    List<ViewThroughputUseCase.Week> weeklyCounts(TaskAudience audience, LocalDate from, LocalDate to, ZoneId zone);
}
