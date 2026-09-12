package com.flowops.task.application.viewtasks;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewTasksForCallerService implements ViewTasksForCallerUseCase {
    private final ViewTasksUseCase viewTasksUseCase;

    public ViewTasksForCallerService(ViewTasksUseCase viewTasksUseCase) {
        this.viewTasksUseCase = viewTasksUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Row> visibleToCaller() {
        List<Row> rows = new ArrayList<>();
        for (ViewTasksResult.Row row : viewTasksUseCase.execute().tasks()) {
            rows.add(new Row(
                    row.id().value(),
                    row.title(),
                    row.state().name(),
                    row.assigneeId(),
                    row.assigneeName(),
                    row.deadline()));
        }
        return rows;
    }
}
