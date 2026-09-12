package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.JobId;
import java.util.List;
import java.util.UUID;

public interface CollaborationReadPort {
    List<Pairing> declaredJoins(JobId job);

    List<Pairing> sharingAnOutput(JobId job);

    List<Pairing> askedForTogether(JobId job);

    List<Pairing> workingAlongside(JobId job);

    List<Reuse> assetsReusedAcrossJobs();

    record Pairing(UUID oneBracket, UUID otherBracket, String workType, String evidence) {}

    record Reuse(String value, UUID firstJob, UUID secondJob, UUID firstBracket, UUID secondBracket) {}
}
