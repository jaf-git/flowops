package com.flowops.tasklib.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.tasklib.domain.exception.UnknownMetadataValueException;
import org.junit.jupiter.api.Test;

class TemplateMetadataTest {
    @Test
    void aSeededFunctionalRoleIsAcceptedExactlyAsItWasSeeded() {
        for (String seeded : new String[] {
            "Agency owner", "Account manager", "Content writer", "Editor", "Designer", "Ads specialist"
        }) {
            TemplateMetadata described = TemplateMetadata.empty().with(MetadataField.RESPONSIBLE_ROLE, seeded);

            assertThat(described.responsibleRole())
                    .as("V64 seeded '%s'; a record that stores anything else cannot be looked up", seeded)
                    .isEqualTo(seeded);
        }
    }

    @Test
    void aRoleTheSeedDidNotShipIsAcceptedBecauseTheVocabularyGovernsAndNotThisRecord() {
        assertThatCode(() -> TemplateMetadata.empty().with(MetadataField.RESPONSIBLE_ROLE, "Videographer"))
                .doesNotThrowAnyException();
    }

    @Test
    void blankWithdrawsAndSurroundingSpaceIsTrimmed() {
        TemplateMetadata answered = TemplateMetadata.empty().with(MetadataField.RESPONSIBLE_ROLE, "  Designer  ");
        assertThat(answered.responsibleRole()).isEqualTo("Designer");

        assertThat(answered.with(MetadataField.RESPONSIBLE_ROLE, "   ").responsibleRole())
                .as("a blank answer clears the field rather than storing whitespace")
                .isNull();
    }

    @Test
    void theOutputVocabularyIsStillClosed() {
        assertThatThrownBy(() -> TemplateMetadata.empty().with(MetadataField.OUTPUT_KIND, "SPREADSHEET"))
                .isInstanceOf(UnknownMetadataValueException.class);
    }

    @Test
    void aTemplateNobodyHasBeenAskedAboutIsValid() {
        TemplateMetadata empty = TemplateMetadata.empty();

        assertThat(empty.responsibleRole()).isNull();
        assertThat(empty.isComplete()).isFalse();
        assertThat(empty.nextMissing()).contains(MetadataField.RESPONSIBLE_ROLE);
    }
}
