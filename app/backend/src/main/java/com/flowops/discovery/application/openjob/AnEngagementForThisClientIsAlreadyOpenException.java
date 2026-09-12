package com.flowops.discovery.application.openjob;

import com.flowops.discovery.domain.model.JobId;

public class AnEngagementForThisClientIsAlreadyOpenException extends RuntimeException {
    private final JobId alreadyOpen;
    private final String itsName;

    public AnEngagementForThisClientIsAlreadyOpenException(JobId alreadyOpen, String itsName) {
        super("an engagement for this client is already open: " + alreadyOpen.value());
        this.alreadyOpen = alreadyOpen;
        this.itsName = itsName;
    }

    public JobId alreadyOpen() {
        return alreadyOpen;
    }

    public String itsName() {
        return itsName;
    }
}
