package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Start a run of this template, steered by the conversation's counterpart.")
public record StartRunRequest(@Schema(description = "An active template's identifier") UUID templateId) {}
