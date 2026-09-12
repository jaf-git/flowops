package com.flowops.chat.application.convertmessage;

import com.flowops.chat.application.shared.port.AttachableInstancesPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConvertMessageToTaskUseCase {
    ConversionContext contextFor(UUID conversationId, UUID messageId);

    UUID convert(UUID conversationId, UUID messageId, Draft draft);

    record ConversionContext(
            Optional<UUID> suggestedAssignee,
            Optional<String> suggestedAssigneeName,
            boolean suggestedAssigneeActive,
            String title,
            String description,
            List<AttachableInstancesPort.Attachable> instances) {}

    record Draft(
            String title,
            String description,
            UUID assigneeId,
            Instant deadline,
            String priority,
            Optional<UUID> instanceId) {}
}
