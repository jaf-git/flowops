package com.flowops.task.domain.model;

import com.flowops.task.domain.exception.ApprovalScoreOutOfRangeException;
import com.flowops.task.domain.exception.ApprovalScoreRequiredException;

public record ApprovalScore(int value) {
    public static final int LOWEST = 1;

    public static final int HIGHEST = 5;

    public ApprovalScore {
        if (value < LOWEST || value > HIGHEST) {
            throw new ApprovalScoreOutOfRangeException(value);
        }
    }

    public static ApprovalScore of(Integer offered) {
        if (offered == null) {
            throw new ApprovalScoreRequiredException();
        }
        return new ApprovalScore(offered);
    }
}
