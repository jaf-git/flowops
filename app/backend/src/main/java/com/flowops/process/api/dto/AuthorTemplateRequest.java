package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AuthorTemplateRequest(
        @NotBlank @Size(max = 200) @Schema(example = "Integrare angajat nou") String name,
        @Schema(example = "Cum integrăm un coleg nou") String overview,
        @NotEmpty(message = "a template with no work is not a process") List<StepRequest> steps) {}
