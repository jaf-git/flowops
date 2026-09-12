package com.flowops.nodepipeline.application.port;

import com.flowops.nodepipeline.domain.notify.PipelineMessage;

public interface NotifyPipelineFindingsPort {
    void tell(PipelineMessage message);
}
