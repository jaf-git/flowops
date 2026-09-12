package com.flowops.discovery.application.closejob;

import com.flowops.discovery.domain.model.JobId;
import java.util.UUID;

public class OnlyTheOwnerMayForceCloseException extends RuntimeException {
    private final transient JobId job;

    public OnlyTheOwnerMayForceCloseException(JobId job, UUID attemptedBy) {
        super("R16 - " + attemptedBy + " may not force-close job " + job.value()
                + "; the job owner may, and force-closure is never urgent enough to need a second route");
        this.job = job;
    }

    public JobId job() {
        return job;
    }
}
