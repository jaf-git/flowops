package com.flowops.process.application.assignstep;

import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.StepId;
import java.time.Instant;

public record AssignStepCommand(InstanceId instance, StepId step, PersonId assignee, Instant deadline) {}
