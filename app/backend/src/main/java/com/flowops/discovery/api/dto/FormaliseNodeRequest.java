package com.flowops.discovery.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FormaliseNodeRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String detail,
        List<@NotBlank @Size(max = 500) String> steps) {}
