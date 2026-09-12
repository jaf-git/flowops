package com.flowops.chatassist.infrastructure.chat;

import com.flowops.chat.application.published.ConversationExcerptUseCase;
import com.flowops.chatassist.application.port.ConversationExcerptPort;
import com.flowops.chatassist.application.port.ConversationExcerptPort.Excerpt;
import com.flowops.chatassist.domain.ConversationExtract;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConversationExcerptAdapter implements ConversationExcerptPort {
    private final ConversationExcerptUseCase conversations;

    public ConversationExcerptAdapter(ConversationExcerptUseCase conversations) {
        this.conversations = conversations;
    }

    @Override
    public Excerpt of(UUID conversationId) {
        ConversationExcerptUseCase.Excerpt read = conversations.of(conversationId);

        ConversationExtract conversation = new ConversationExtract(read.lines().stream()
                .map(line -> new ConversationExtract.Line(line.speaker(), line.said()))
                .toList());

        Map<String, UUID> speakers = new LinkedHashMap<>();
        for (ConversationExcerptUseCase.Speaker speaker : read.speakers()) {
            speakers.put(speaker.label(), speaker.personId());
        }
        return new Excerpt(conversation, Map.copyOf(speakers));
    }
}
