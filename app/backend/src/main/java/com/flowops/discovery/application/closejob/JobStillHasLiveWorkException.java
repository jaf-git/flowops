package com.flowops.discovery.application.closejob;

import com.flowops.discovery.domain.model.JobId;

public class JobStillHasLiveWorkException extends RuntimeException {
    private final transient JobId job;
    private final int liveWork;

    public JobStillHasLiveWorkException(JobId job, int liveWork) {
        super("job " + job.value() + " still has " + liveWork
                + " live bracket(s); R15.2 refuses a normal close, and a force close says why");
        this.job = job;
        this.liveWork = liveWork;
    }

    public JobId job() {
        return job;
    }

    public int liveWork() {
        return liveWork;
    }
}
