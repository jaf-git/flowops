package com.flowops.task.domain.exception;

import com.flowops.shared.domain.RefusedByDomain;

public class DeadlineInThePastException extends RefusedByDomain {
    public DeadlineInThePastException() {
        super("DEADLINE_IN_THE_PAST", "a task cannot be created with a deadline that has already passed");
    }
}
