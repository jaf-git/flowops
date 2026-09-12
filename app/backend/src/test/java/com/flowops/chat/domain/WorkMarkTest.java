package com.flowops.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.Message;
import com.flowops.chat.domain.model.MessageId;
import com.flowops.chat.domain.model.PersonId;
import com.flowops.chat.domain.model.ThreadEntry;
import com.flowops.chat.domain.model.WorkMark;
import com.flowops.chat.domain.model.WorkSubject;
import com.flowops.chat.domain.model.WorkSubjectKind;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("CHAT-ASSIGN-DIRECT-01")
class WorkMarkTest {
    private static final ConversationId CONVERSATION = ConversationId.of(UUID.randomUUID());
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-21T09:14:00Z");

    private static WorkMark markOf(WorkSubject subject) {
        return WorkMark.recorded(MessageId.of(UUID.randomUUID()), CONVERSATION, IONUT, subject, NOW);
    }

    @Test
    void aMarkCarriesWhoDidItWhatTheyMadeAndWhen() {
        UUID task = UUID.randomUUID();

        WorkMark mark = markOf(WorkSubject.task(task));

        assertThat(mark.actor()).isEqualTo(IONUT);
        assertThat(mark.conversation()).isEqualTo(CONVERSATION);
        assertThat(mark.markedAt()).isEqualTo(NOW);
        assertThat(mark.subject().kind()).isEqualTo(WorkSubjectKind.TASK);
        assertThat(mark.subject().id()).isEqualTo(task);
    }

    @Test
    void aRunIsAMarkOfItsOwnKind() {
        UUID run = UUID.randomUUID();

        WorkMark mark = markOf(WorkSubject.run(run));

        assertThat(mark.subject().kind()).isEqualTo(WorkSubjectKind.RUN);
        assertThat(mark.subject().id()).isEqualTo(run);
    }

    @Test
    void aMarkHasNowhereToStoreASentence() {
        assertThat(Arrays.stream(WorkMark.class.getDeclaredFields()).map(Field::getType))
                .as("a String field on a work mark is a user-facing sentence in one language for ever")
                .doesNotContain(String.class);

        assertThat(Arrays.stream(WorkMark.class.getMethods()).map(Method::getName))
                .doesNotContain("body");
    }

    @Test
    void aMarkCanBeNeitherEditedNorDeletedNorConverted() {
        assertThat(Arrays.stream(WorkMark.class.getMethods()).map(Method::getName))
                .as("nobody typed a mark, so there is no author to authorise changing it")
                .doesNotContain("editedBy", "deletedBy", "becameTask", "requireConvertible");
    }

    @Test
    void aMarkAndAMessageAreBothEntriesInOneThread() {
        Message said = Message.sent(MessageId.of(UUID.randomUUID()), CONVERSATION, IONUT, "Poți să suni?", NOW);

        assertThat(markOf(WorkSubject.task(UUID.randomUUID()))).isInstanceOf(ThreadEntry.class);
        assertThat(said).isInstanceOf(ThreadEntry.class);
    }

    @Test
    void aThreadHasExactlyTwoKindsOfEntry() {
        assertThat(ThreadEntry.class.isSealed()).isTrue();
        assertThat(ThreadEntry.class.getPermittedSubclasses()).containsExactlyInAnyOrder(Message.class, WorkMark.class);
    }

    @Test
    void aMarkWithoutAnActorOrASubjectIsRefused() {
        MessageId id = MessageId.of(UUID.randomUUID());

        assertThatThrownBy(() -> WorkMark.recorded(id, CONVERSATION, null, WorkSubject.task(UUID.randomUUID()), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkMark.recorded(id, CONVERSATION, IONUT, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkMark.recorded(id, CONVERSATION, IONUT, WorkSubject.task(UUID.randomUUID()), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aSubjectWithoutAnIdentifierIsRefused() {
        assertThatThrownBy(() -> WorkSubject.task(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkSubject.run(null)).isInstanceOf(NullPointerException.class);
    }
}
