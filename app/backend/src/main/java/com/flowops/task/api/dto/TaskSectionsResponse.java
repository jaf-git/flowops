package com.flowops.task.api.dto;

import java.util.List;

public record TaskSectionsResponse(List<SectionCountResponse> sections, int total) {
    public record SectionCountResponse(String section, int count) {}
}
