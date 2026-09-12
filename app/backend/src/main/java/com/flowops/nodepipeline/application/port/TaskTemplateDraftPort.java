package com.flowops.nodepipeline.application.port;

import java.util.UUID;

public interface TaskTemplateDraftPort {
    Draft draftFor(com.flowops.nodepipeline.domain.CandidateTemplate observed, UUID author);

    record Draft(UUID id, boolean created) {}
}
