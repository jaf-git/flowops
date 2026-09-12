package com.flowops.process.application.archiveinstance;

import com.flowops.process.domain.model.InstanceId;

public interface ArchiveInstanceUseCase {
    void archive(InstanceId instance);

    void restore(InstanceId instance);
}
