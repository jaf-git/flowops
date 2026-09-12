package com.flowops.process.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.flowops.process.domain.model.ProcessMetadata;
import org.junit.jupiter.api.Test;

class ProcessMetadataTest {
    @Test
    void aSeededFunctionalRoleIsAcceptedExactlyAsItWasSeeded() {
        assertThat(new ProcessMetadata(null, null, "Account manager").ownerRole())
                .isEqualTo("Account manager");
    }

    @Test
    void aRoleTheSeedDidNotShipIsAccepted() {
        assertThatCode(() -> new ProcessMetadata(null, null, "Videographer")).doesNotThrowAnyException();
    }

    @Test
    void blankWithdrawsAndAnEmptyRecordIsValid() {
        assertThat(new ProcessMetadata(null, null, "  Designer  ").ownerRole()).isEqualTo("Designer");
        assertThat(new ProcessMetadata(null, null, "   ").ownerRole()).isNull();
        assertThat(ProcessMetadata.empty().isEmpty()).isTrue();
    }
}
