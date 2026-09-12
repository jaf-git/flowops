package com.flowops.notification.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The signed-in person's unread count. No route returns anybody else's.")
public record UnreadCountResponse(int unread) {}
