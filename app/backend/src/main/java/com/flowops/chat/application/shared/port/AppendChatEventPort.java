package com.flowops.chat.application.shared.port;

import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.MessageId;
import com.flowops.chat.domain.model.PersonId;

public interface AppendChatEventPort {
    void messageSent(ConversationId conversation, MessageId message, PersonId actor);

    void messageEdited(ConversationId conversation, MessageId message, PersonId actor);

    void messageDeleted(ConversationId conversation, MessageId message, PersonId actor);

    void messageConverted(ConversationId conversation, MessageId message, PersonId actor);

    void workAssigned(ConversationId conversation, MessageId mark, PersonId actor);
}
