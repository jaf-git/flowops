package com.flowops.tasklib.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ShapeCheckRequest(
        String title,
        String description,
        String type,
        String priority,
        BigDecimal estimatedHours,
        List<String> checklist) {}
