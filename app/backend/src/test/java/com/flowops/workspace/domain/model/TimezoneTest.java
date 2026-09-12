package com.flowops.workspace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.workspace.domain.exception.UnknownTimezoneException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("WORKSPACE-SETUP-01")
class TimezoneTest {
    @Test
    void aZoneTheRuntimeKnowsIsAccepted() {
        Timezone zone = new Timezone("Europe/Bucharest");

        assertThat(zone.value()).isEqualTo("Europe/Bucharest");
    }

    @Test
    void aFixedOffsetIsNotAZone() {
        assertThatThrownBy(() -> new Timezone("+02:00")).isInstanceOf(UnknownTimezoneException.class);
    }

    @Test
    void aZoneTheRuntimeDoesNotKnowIsRefused() {
        assertThatThrownBy(() -> new Timezone("Europe/Atlantis")).isInstanceOf(UnknownTimezoneException.class);
    }

    @Test
    void anEmptyValueIsRefused() {
        assertThatThrownBy(() -> new Timezone("  ")).isInstanceOf(UnknownTimezoneException.class);
    }

    @Test
    void theOfferedSetHoldsBothLocalesThisProductShipsIn() {
        assertThat(Timezone.known()).contains("Europe/Bucharest", "Europe/London");
    }

    @Test
    void theOfferedSetHoldsNoOffsets() {
        assertThat(Timezone.known()).noneMatch(zone -> zone.startsWith("+") || zone.startsWith("-"));
    }
}
