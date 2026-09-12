package com.flowops.discovery.application.closejob;

import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;

public class JobAlreadyEndedException extends RuntimeException {
    private final transient JobId job;
    private final transient Job.Status status;

    public JobAlreadyEndedException(JobId job, Job.Status status) {
        super("job " + job.value() + " ended as " + status + "; work that returns opens a new job");
        this.job = job;
        this.status = status;
    }

    public JobId job() {
        return job;
    }

    public Job.Status status() {
        return status;
    }

    public boolean wasForced() {
        return status == Job.Status.FORCE_CLOSED;
    }
}
