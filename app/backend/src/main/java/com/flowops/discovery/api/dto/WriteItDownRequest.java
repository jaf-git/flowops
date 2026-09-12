package com.flowops.discovery.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WriteItDownRequest(@NotBlank @Size(max = 200) String name) {}
