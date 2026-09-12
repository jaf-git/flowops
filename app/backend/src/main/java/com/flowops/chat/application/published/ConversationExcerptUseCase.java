package com.flowops.chat.application.published;

import java.util.List;
import java.util.UUID;

public interface ConversationExcerptUseCase {
    Excerpt of(UUID conversationId);

    record Excerpt(List<Line> lines, List<Speaker> speakers) {
        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }

    record Speaker(String label, UUID personId) {}

    record Line(String speaker, String said) {}
}
