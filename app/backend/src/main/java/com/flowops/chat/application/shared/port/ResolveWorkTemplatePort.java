package com.flowops.chat.application.shared.port;

import java.util.UUID;

public interface ResolveWorkTemplatePort {
    UUID resolve(String title, String description, UUID author);
}
