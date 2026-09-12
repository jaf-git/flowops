package com.flowops.tasklib.api.dto;

import java.util.List;

public record TemplateLibraryResponse(
        List<TaskTemplateResponse> templates, List<String> types, int total, int page, int size, int totalPages) {}
