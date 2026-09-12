package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.ConsentRecord;

public interface SaveConsentRecordPort {
    ConsentRecord save(ConsentRecord record);
}
