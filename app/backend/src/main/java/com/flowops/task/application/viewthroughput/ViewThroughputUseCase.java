package com.flowops.task.application.viewthroughput;

import java.time.LocalDate;
import java.util.List;

public interface ViewThroughputUseCase {
    List<Week> of(int weeks);

    record Week(LocalDate starting, int created, int closed) {}
}
