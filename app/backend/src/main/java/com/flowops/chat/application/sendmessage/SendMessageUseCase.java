package com.flowops.chat.application.sendmessage;

import com.flowops.chat.application.shared.port.MessageStorePort;
import java.util.UUID;

public interface SendMessageUseCase {
    MessageStorePort.Stored execute(UUID conversationId, String body);
}
