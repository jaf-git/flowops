package com.flowops.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.auth.domain.exception.DisplayNameRequiredException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("WORKSPACE-SETUP-01")
class DisplayNameTest {
    @Test
    void itKeepsWhatThePersonTyped() {
        assertThat(new DisplayName("Maria Ionescu").value()).isEqualTo("Maria Ionescu");
    }

    @Test
    void surroundingSpaceIsTrimmedRatherThanStored() {
        assertThat(new DisplayName("  Maria Ionescu  ").value()).isEqualTo("Maria Ionescu");
    }

    @Test
    void anEmptyNameIsRefused() {
        assertThatThrownBy(() -> new DisplayName("")).isInstanceOf(DisplayNameRequiredException.class);
    }

    @Test
    void aNameOfNothingButSpaceIsRefused() {
        assertThatThrownBy(() -> new DisplayName("   ")).isInstanceOf(DisplayNameRequiredException.class);
    }

    @Test
    void aNameLongerThanTheColumnIsRefusedRatherThanTruncated() {
        String tooLong = "a".repeat(DisplayName.MAXIMUM_LENGTH + 1);

        assertThatThrownBy(() -> new DisplayName(tooLong)).isInstanceOf(DisplayNameRequiredException.class);
    }

    @Test
    void aRomanianNameIsAccepted() {
        assertThat(new DisplayName("Ionuț Ștefănescu").value()).isEqualTo("Ionuț Ștefănescu");
    }

    @Test
    void twoPeopleMayShareOne() {
        assertThat(new DisplayName("Maria Ionescu")).isEqualTo(new DisplayName("Maria Ionescu"));
    }
}
