package com.flowops.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.chat.domain.enums.ConversationKind;
import com.flowops.chat.domain.model.Conversation;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.PersonId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("CHAT-VIEW-CONVERSATIONS-01")
class ConversationTest {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-18T09:00:00Z");

    @Test
    void thePairIsNormalisedSoTheSameTwoPeopleAlwaysKeyTheSame() {
        PersonId ana = PersonId.of(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
        PersonId mihai = PersonId.of(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

        Conversation anaFirst = Conversation.directBetween(id(), WORKSPACE, ana, mihai, NOW);
        Conversation mihaiFirst = Conversation.directBetween(id(), WORKSPACE, mihai, ana, NOW);

        assertThat(anaFirst.participantLo()).contains(ana);
        assertThat(anaFirst.participantHi()).contains(mihai);
        assertThat(mihaiFirst.participantLo()).isEqualTo(anaFirst.participantLo());
        assertThat(mihaiFirst.participantHi()).isEqualTo(anaFirst.participantHi());
    }

    @Test
    void thePairIsOrderedTheWayTheDatabaseOrdersItNotTheWayJavaDoes() {
        PersonId high = PersonId.of(UUID.fromString("ffffffff-0000-0000-0000-000000000001"));
        PersonId low = PersonId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        Conversation between = Conversation.directBetween(id(), WORKSPACE, high, low, NOW);

        assertThat(between.participantLo())
                .as("unsigned: 0000… sorts below ffff…, which is what the check constraint asserts")
                .contains(low);
        assertThat(between.participantHi()).contains(high);
        assertThat(high.value().compareTo(low.value()))
                .as("and Java disagrees, which is the whole reason PersonId defines its own ordering")
                .isLessThan(0);
    }

    @Test
    void theCounterpartIsTheOtherPersonWhicheverSideYouAskFrom() {
        PersonId ana = PersonId.of(UUID.randomUUID());
        PersonId mihai = PersonId.of(UUID.randomUUID());
        Conversation between = Conversation.directBetween(id(), WORKSPACE, ana, mihai, NOW);

        assertThat(between.counterpartOf(ana)).contains(mihai);
        assertThat(between.counterpartOf(mihai)).contains(ana);
    }

    @Test
    void aDirectConversationNeedsTwoDifferentPeople() {
        PersonId ana = PersonId.of(UUID.randomUUID());

        assertThatThrownBy(() -> Conversation.directBetween(id(), WORKSPACE, ana, ana, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void announcementsHasNoCounterpartAndNoPair() {
        Conversation announcements = Conversation.announcementsFor(id(), WORKSPACE, NOW);

        assertThat(announcements.kind()).isEqualTo(ConversationKind.ANNOUNCEMENT);
        assertThat(announcements.counterpartOf(PersonId.of(UUID.randomUUID()))).isEmpty();
        assertThat(announcements.participantLo()).isEmpty();
        assertThat(announcements.participantHi()).isEmpty();
    }

    @Test
    void aChannelNamesItsRoleAndCarriesNoMemberList() {
        UUID copyEditor = UUID.randomUUID();

        Conversation channel = Conversation.channelForRole(id(), WORKSPACE, copyEditor, "COPY_EDITOR", NOW);

        assertThat(channel.kind()).isEqualTo(ConversationKind.CHANNEL);
        assertThat(channel.functionalRoleId()).contains(copyEditor);
        assertThat(channel.name()).contains("COPY_EDITOR");
        assertThat(channel.participantLo()).isEmpty();
        assertThat(channel.participantHi()).isEmpty();
    }

    @Test
    void aChannelHasNoCounterpartToInferAnAssigneeFrom() {
        Conversation channel = Conversation.channelForRole(id(), WORKSPACE, UUID.randomUUID(), "DESIGN", NOW);

        assertThat(channel.counterpartOf(PersonId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void aChannelDoesNotAnswerMembershipFromItsOwnState() {
        PersonId writer = PersonId.of(UUID.randomUUID());
        Conversation channel = Conversation.channelForRole(id(), WORKSPACE, UUID.randomUUID(), "CONTENT", NOW);

        assertThat(channel.hasParticipant(writer)).isFalse();
    }

    @Test
    void aGroupIsNamedAndRefusesToBeNameless() {
        Conversation group = Conversation.groupNamed(id(), WORKSPACE, "  Aurora launch  ", NOW);

        assertThat(group.kind()).isEqualTo(ConversationKind.GROUP);
        assertThat(group.name()).contains("Aurora launch");
        assertThat(group.functionalRoleId()).isEmpty();

        assertThatThrownBy(() -> Conversation.groupNamed(id(), WORKSPACE, "   ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDirectConversationKnowsWhoIsInIt() {
        PersonId ana = PersonId.of(UUID.randomUUID());
        PersonId mihai = PersonId.of(UUID.randomUUID());
        PersonId stranger = PersonId.of(UUID.randomUUID());
        Conversation between = Conversation.directBetween(id(), WORKSPACE, ana, mihai, NOW);

        assertThat(between.hasParticipant(ana)).isTrue();
        assertThat(between.hasParticipant(mihai)).isTrue();
        assertThat(between.hasParticipant(stranger)).isFalse();
    }

    private static ConversationId id() {
        return ConversationId.of(UUID.randomUUID());
    }
}
