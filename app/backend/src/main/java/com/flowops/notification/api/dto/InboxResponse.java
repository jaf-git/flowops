package com.flowops.notification.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "The signed-in person's notifications. There is no variant of this for anybody else.")
public record InboxResponse(List<NotificationRow> items) {}
