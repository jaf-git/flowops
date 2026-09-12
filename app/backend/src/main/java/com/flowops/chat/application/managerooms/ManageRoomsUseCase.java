package com.flowops.chat.application.managerooms;

import com.flowops.chat.domain.model.Conversation;
import java.util.List;
import java.util.UUID;

public interface ManageRoomsUseCase {
    Conversation startGroup(String name);

    void join(UUID conversationId);

    void leave(UUID conversationId);

    void rename(UUID conversationId, String name);

    List<Participant> participants(UUID conversationId);

    void addParticipant(UUID conversationId, UUID personId);

    record Participant(UUID personId, String displayName, boolean active) {}

    Conversation announcements();
}
