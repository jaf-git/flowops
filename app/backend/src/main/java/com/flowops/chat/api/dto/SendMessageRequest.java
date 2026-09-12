package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SendMessageRequest(@Schema(description = "The message body, at most 4000 characters") String body) {}
