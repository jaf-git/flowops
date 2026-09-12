package com.flowops.discovery.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record NudgeAnswerRequest(@NotNull @Pattern(regexp = "DONE|STILL_GOING|DROPPED|WAS_A_QUESTION") String answer) {}
