package com.flowops.chatassist.application;

import com.flowops.chatassist.application.port.ConversationExcerptPort;
import com.flowops.chatassist.application.port.IdentifyCallerPort;
import com.flowops.chatassist.application.port.LocalLanguageModelPort;
import com.flowops.chatassist.domain.ConversationExtract;
import com.flowops.chatassist.domain.ProposedWork;
import com.flowops.chatassist.domain.WorkGroundingValidator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuggestWorkFromConversationService implements SuggestWorkFromConversationUseCase {
    private final ConversationExcerptPort conversations;
    private final LocalLanguageModelPort model;
    private final DraftComposer composer;
    private final IdentifyCallerPort caller;

    public SuggestWorkFromConversationService(
            ConversationExcerptPort conversations,
            LocalLanguageModelPort model,
            DraftComposer composer,
            IdentifyCallerPort caller) {
        this.conversations = conversations;
        this.model = model;
        this.composer = composer;
        this.caller = caller;
    }

    @Override
    public boolean isAvailable() {
        return model.isAvailable();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProposedWork> forConversation(UUID conversationId) {
        ConversationExcerptPort.Excerpt excerpt = conversations.of(conversationId);
        ConversationExtract conversation = excerpt.conversation();

        if (!model.isAvailable() || conversation.isEmpty()) {
            return Optional.empty();
        }

        UUID requester = caller.currentCaller()
                .orElseThrow(() -> new IllegalStateException("a draft is prepared for a person, and there is none"));

        return model.readWorkIn(conversation)
                .flatMap(claimed -> WorkGroundingValidator.check(claimed, conversation))
                .map(grounded -> composer.compose(grounded, conversation, excerpt.speakers(), requester));
    }
}
