package com.flowops.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.chat.domain.exception.MessageAlreadyConvertedException;
import com.flowops.chat.domain.exception.MessageDeletedException;
import com.flowops.chat.domain.exception.MessageEmptyException;
import com.flowops.chat.domain.exception.MessageTooLongException;
import com.flowops.chat.domain.exception.NotTheAuthorException;
import com.flowops.chat.domain.exception.NothingChangedException;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.Message;
import com.flowops.chat.domain.model.MessageId;
import com.flowops.chat.domain.model.PersonId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("CHAT-SEND-MESSAGE-01")
class MessageTest {
    private static final ConversationId CONVERSATION = ConversationId.of(UUID.randomUUID());
    private static final PersonId ANA = PersonId.of(UUID.randomUUID());
    private static final PersonId MIHAI = PersonId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-18T09:00:00Z");

    private static Message said(String body) {
        return Message.sent(MessageId.of(UUID.randomUUID()), CONVERSATION, ANA, body, NOW);
    }

    @Test
    void aMessageCarriesWhatWasSaidAndWhoSaidIt() {
        Message message = said("Trebuie să sunăm furnizorul până joi");

        assertThat(message.body()).isEqualTo("Trebuie să sunăm furnizorul până joi");
        assertThat(message.author()).isEqualTo(ANA);
        assertThat(message.sentAt()).isEqualTo(NOW);
        assertThat(message.isDeleted()).isFalse();
        assertThat(message.editedAt()).isEmpty();
        assertThat(message.convertedTaskId()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n", "   \n  "})
    void aBlankBodyIsRefused(String blank) {
        assertThatThrownBy(() -> said(blank)).isInstanceOf(MessageEmptyException.class);
    }

    @Test
    void aNullBodyIsRefusedAsBlankRatherThanAsAnAccident() {
        assertThatThrownBy(() -> said(null)).isInstanceOf(MessageEmptyException.class);
    }

    @Test
    void aBodyPastTheCeilingIsRefusedAndNamesTheLimit() {
        String tooLong = "a".repeat(Message.MAX_BODY_LENGTH + 1);

        assertThatThrownBy(() -> said(tooLong))
                .isInstanceOf(MessageTooLongException.class)
                .satisfies(failure ->
                        assertThat(((MessageTooLongException) failure).limit()).isEqualTo(Message.MAX_BODY_LENGTH));
    }

    @Test
    void aBodyExactlyAtTheCeilingIsAccepted() {
        assertThatNoException().isThrownBy(() -> said("a".repeat(Message.MAX_BODY_LENGTH)));
    }

    @Test
    void nothingIsTruncated() {
        String body = "  Sunăm furnizorul  ";

        assertThat(said(body).body()).isEqualTo("Sunăm furnizorul");
    }

    @Test
    void theAuthorMayReplaceTheirOwnBodyAndTheMessageSaysItWasEdited() {
        Message message = said("Sunăm furnizoul");

        message.editedBy(ANA, "Sunăm furnizorul", NOW.plusSeconds(30));

        assertThat(message.body()).isEqualTo("Sunăm furnizorul");
        assertThat(message.editedAt()).contains(NOW.plusSeconds(30));
    }

    @Test
    void nobodyButTheAuthorMayEditAMessage() {
        Message message = said("Sunăm furnizorul");

        assertThatThrownBy(() -> message.editedBy(MIHAI, "Nu sunăm pe nimeni", NOW))
                .isInstanceOf(NotTheAuthorException.class);
        assertThat(message.body()).isEqualTo("Sunăm furnizorul");
    }

    @Test
    void nobodyButTheAuthorMayDeleteAMessage() {
        Message message = said("Sunăm furnizorul");

        assertThatThrownBy(() -> message.deletedBy(MIHAI, NOW)).isInstanceOf(NotTheAuthorException.class);
        assertThat(message.isDeleted()).isFalse();
    }

    @Test
    void anEditToBlankIsRefusedRatherThanTreatedAsADeletion() {
        Message message = said("Sunăm furnizorul");

        assertThatThrownBy(() -> message.editedBy(ANA, "   ", NOW)).isInstanceOf(MessageEmptyException.class);
        assertThat(message.isDeleted()).isFalse();
    }

    @Test
    void anEditThatChangesNothingIsRefused() {
        Message message = said("Sunăm furnizorul");

        assertThatThrownBy(() -> message.editedBy(ANA, "Sunăm furnizorul", NOW))
                .isInstanceOf(NothingChangedException.class);
        assertThat(message.editedAt()).isEmpty();
    }

    @Test
    void anEditDifferingOnlyInSurroundingSpaceIsNothingChanged() {
        Message message = said("Sunăm furnizorul");

        assertThatThrownBy(() -> message.editedBy(ANA, "  Sunăm furnizorul  ", NOW))
                .isInstanceOf(NothingChangedException.class);
    }

    @Test
    void deletionIsSoftAndTheRowKeepsItsAuthorAndTime() {
        Message message = said("Sunăm furnizorul");

        message.deletedBy(ANA, NOW.plusSeconds(60));

        assertThat(message.isDeleted()).isTrue();
        assertThat(message.deletedAt()).contains(NOW.plusSeconds(60));
        assertThat(message.author()).isEqualTo(ANA);
        assertThat(message.sentAt()).isEqualTo(NOW);
    }

    @Test
    void aDeletedMessageCannotBeEditedOrDeletedAgain() {
        Message message = said("Sunăm furnizorul");
        message.deletedBy(ANA, NOW);

        assertThatThrownBy(() -> message.editedBy(ANA, "altceva", NOW)).isInstanceOf(MessageDeletedException.class);
        assertThatThrownBy(() -> message.deletedBy(ANA, NOW)).isInstanceOf(MessageDeletedException.class);
    }

    @Test
    void aMessageRecordsTheTaskItBecame() {
        Message message = said("Sunăm furnizorul");
        UUID task = UUID.randomUUID();

        message.becameTask(task);

        assertThat(message.convertedTaskId()).contains(task);
    }

    @Test
    void aMessageBecomesAtMostOneTaskAndTheRefusalNamesIt() {
        Message message = said("Sunăm furnizorul");
        UUID first = UUID.randomUUID();
        message.becameTask(first);

        assertThatThrownBy(() -> message.becameTask(UUID.randomUUID()))
                .isInstanceOf(MessageAlreadyConvertedException.class)
                .satisfies(failure -> assertThat(((MessageAlreadyConvertedException) failure).taskId())
                        .isEqualTo(first));
        assertThat(message.convertedTaskId()).contains(first);
    }

    @Test
    void aDeletedMessageCannotBecomeATask() {
        Message message = said("Sunăm furnizorul");
        message.deletedBy(ANA, NOW);

        assertThatThrownBy(() -> message.becameTask(UUID.randomUUID())).isInstanceOf(MessageDeletedException.class);
    }

    @Test
    void editingOrDeletingAMessageLeavesTheTaskItBecameNamed() {
        Message message = said("Sunăm furnizorul");
        UUID task = UUID.randomUUID();
        message.becameTask(task);

        message.editedBy(ANA, "Sunăm furnizorul azi", NOW.plusSeconds(10));
        assertThat(message.convertedTaskId()).contains(task);

        message.deletedBy(ANA, NOW.plusSeconds(20));
        assertThat(message.convertedTaskId()).contains(task);
    }
}
