package com.flowops.chat.application.shared.port;

import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.Message;
import com.flowops.chat.domain.model.MessageId;
import com.flowops.chat.domain.model.ThreadEntry;
import com.flowops.chat.domain.model.WorkMark;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageStorePort {
    Stored append(Message message);

    StoredEntry appendMark(WorkMark mark);

    Optional<Stored> find(MessageId message);

    Optional<Stored> findConvertedInto(UUID taskId);

    void update(Message message);

    List<StoredEntry> page(ConversationId conversation, Optional<Long> beforeSeq, int limit);

    long currentCursor();

    record Stored(Message message, long seq) {}

    record StoredEntry(ThreadEntry entry, long seq) {}
}
