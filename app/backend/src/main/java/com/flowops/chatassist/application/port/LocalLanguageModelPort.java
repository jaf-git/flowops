package com.flowops.chatassist.application.port;

import com.flowops.chatassist.domain.ConversationExtract;
import com.flowops.chatassist.domain.WorkOpinion;
import java.util.Optional;

public interface LocalLanguageModelPort {
    boolean isAvailable();

    Optional<WorkOpinion> readWorkIn(ConversationExtract conversation);
}
