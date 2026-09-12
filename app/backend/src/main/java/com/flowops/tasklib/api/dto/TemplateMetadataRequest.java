package com.flowops.tasklib.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TemplateMetadataRequest(
        @NotNull(message = "a field is named")
                @Pattern(
                        regexp = "RESPONSIBLE_ROLE|TRIGGER_NOTE|REQUIRED_INPUT|EXPECTED_OUTPUT|OUTPUT_KIND"
                                + "|COMPLETION_CRITERIA",
                        message = "that is not one of the six things a template says about its work")
                String field,
        @Size(max = 2000) String answer) {}
