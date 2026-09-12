package com.flowops.tasklib.application.draftfromtask;

import com.flowops.tasklib.application.TaskTemplateUseCase;
import com.flowops.tasklib.application.port.DescribeTaskPort;
import com.flowops.tasklib.application.port.RecordTaskProvenancePort;
import com.flowops.tasklib.application.published.TemplateStampUseCase;
import com.flowops.tasklib.domain.TaskTemplate;
import com.flowops.tasklib.domain.TemplateDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftFromTaskService implements DraftFromTaskUseCase {
    private final DescribeTaskPort describeTaskPort;
    private final TaskTemplateUseCase taskTemplateUseCase;
    private final TemplateStampUseCase templateStampUseCase;
    private final RecordTaskProvenancePort recordTaskProvenancePort;

    public DraftFromTaskService(
            DescribeTaskPort describeTaskPort,
            TaskTemplateUseCase taskTemplateUseCase,
            TemplateStampUseCase templateStampUseCase,
            RecordTaskProvenancePort recordTaskProvenancePort) {
        this.describeTaskPort = describeTaskPort;
        this.taskTemplateUseCase = taskTemplateUseCase;
        this.templateStampUseCase = templateStampUseCase;
        this.recordTaskProvenancePort = recordTaskProvenancePort;
    }

    @Override
    @Transactional
    public void draftFor(UUID task) {
        Optional<DescribeTaskPort.Words> words = describeTaskPort.describe(task);
        if (words.isEmpty()) {
            return;
        }

        TaskTemplate draft = taskTemplateUseCase.create(detailsFrom(words.get()), false);
        templateStampUseCase.recordStamp(draft.id());
        recordTaskProvenancePort.stampedFrom(task, draft.id());
    }

    private TemplateDetails detailsFrom(DescribeTaskPort.Words words) {
        return new TemplateDetails(words.title(), words.description(), null, words.priority(), null, List.of());
    }
}
