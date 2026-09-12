package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentOnTaskRequest(
        @NotBlank
                @Size(max = 4000)
                @Schema(
                        description = "What you want to say. Cannot be edited or deleted afterwards — a"
                                + " correction is a new comment.",
                        example = "Am sunat furnizorul; revin cu termenul mâine.")
                String body) {}
