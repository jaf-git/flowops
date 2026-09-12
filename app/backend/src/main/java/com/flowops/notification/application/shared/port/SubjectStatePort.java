package com.flowops.notification.application.shared.port;

import com.flowops.shared.notice.CancelCondition;
import com.flowops.shared.notice.SubjectKind;
import java.util.UUID;

public interface SubjectStatePort {
    SubjectKind answersFor();

    boolean stillRelevant(CancelCondition condition, UUID subjectId);

    boolean stillExists(UUID subjectId);
}
