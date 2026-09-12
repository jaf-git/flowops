package com.flowops.tasklib.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record TemplateDraftRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Size(max = 60) String type,
        @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
        BigDecimal estimatedHours,
        List<@Size(max = 300) String> checklist,
        boolean submitForApproval) {}
