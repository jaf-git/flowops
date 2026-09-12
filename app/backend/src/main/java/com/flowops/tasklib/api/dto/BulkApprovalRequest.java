package com.flowops.tasklib.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record BulkApprovalRequest(@NotEmpty List<UUID> templateIds) {}
