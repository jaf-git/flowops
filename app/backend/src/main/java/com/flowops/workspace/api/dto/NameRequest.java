package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NameRequest(@Schema(example = "Client services") @NotBlank @Size(max = 60) String name) {}
