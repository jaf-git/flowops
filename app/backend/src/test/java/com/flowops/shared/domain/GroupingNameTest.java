package com.flowops.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GroupingNameTest {
    @Test
    void surroundingSpaceIsNotPartOfTheName() {
        assertThat(GroupingName.of("  Aurora Coffee  ").value()).isEqualTo("Aurora Coffee");
    }

    @Test
    void anAbsentNameIsEmptyRatherThanAFailure() {
        assertThat(GroupingName.of(null).isBlank()).isTrue();
        assertThat(GroupingName.of("   ").isBlank()).isTrue();
    }

    @Test
    void caseAndSurroundingSpaceDoNotMakeASecondName() {
        GroupingName clients = GroupingName.of("Clients");

        assertThat(clients.sameAs(GroupingName.of("clients"))).isTrue();
        assertThat(clients.sameAs(GroupingName.of("  CLIENTS "))).isTrue();
        assertThat(clients.sameAs(GroupingName.of("Client"))).isFalse();
    }

    @Test
    void spaceInsideTheNameIsPartOfIt() {
        assertThat(GroupingName.of("Aurora Coffee").sameAs(GroupingName.of("AuroraCoffee")))
                .isFalse();
    }

    @Test
    void nothingIsTheSameAsAnAbsentName() {
        assertThat(GroupingName.of("Clients").sameAs(null)).isFalse();
    }
}
