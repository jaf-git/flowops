package com.flowops.chat.infrastructure.tasklib;

import com.flowops.chat.application.shared.port.ResolveWorkTemplatePort;
import com.flowops.tasklib.application.published.TemplateResolutionUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatWorkTemplateAdapter implements ResolveWorkTemplatePort {
    private final TemplateResolutionUseCase templates;

    public ChatWorkTemplateAdapter(TemplateResolutionUseCase templates) {
        this.templates = templates;
    }

    @Override
    public UUID resolve(String title, String description, UUID author) {
        return templates.resolve(title, description, author);
    }
}
