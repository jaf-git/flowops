package com.flowops.tasklib.application.draftcandidates;

import com.flowops.tasklib.application.port.TaskTemplatePort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewDraftCandidatesService implements ViewDraftCandidatesUseCase {
    private final TaskTemplatePort templates;

    public ViewDraftCandidatesService(TaskTemplatePort templates) {
        this.templates = templates;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskTemplatePort.DraftCandidate> candidates() {
        return templates.draftCandidates();
    }
}
