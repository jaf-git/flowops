package com.flowops.task.application.viewtasks;

public interface SectionedTaskQueryUseCase {
    SectionedTasksResult.Counts count(TaskFilter filter);

    SectionedTasksResult.Page page(TaskSection section, TaskFilter filter, TaskSort sort, int page, int size);
}
