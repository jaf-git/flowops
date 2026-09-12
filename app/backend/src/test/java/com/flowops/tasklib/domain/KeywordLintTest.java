package com.flowops.tasklib.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordLintTest {
    @Test
    void aSingleGenericTokenIsRejectedAndPublishIsTheNamedCase() {
        assertThat(KeywordLint.check("Schedule the weekly posts", List.of("publish")))
                .singleElement()
                .satisfies(fault -> {
                    assertThat(fault.keyword()).isEqualTo("publish");
                    assertThat(fault.reason()).isEqualTo(KeywordLint.Reason.TOO_GENERIC);
                });
    }

    @Test
    void theOtherGenericTokensAreRejectedToo() {
        List<String> generic = List.of("stuff", "work", "task", "misc", "todo", "update", "urgent", "final");

        assertThat(KeywordLint.check("Monthly report", generic))
                .as("every one of these appears in every kind of work, which is what makes it useless as a key")
                .hasSize(generic.size());
    }

    @Test
    void aKeywordContainedInItsOwnTitleIsRejected() {
        assertThat(KeywordLint.check("Write the monthly client report", List.of("monthly client")))
                .singleElement()
                .satisfies(fault -> assertThat(fault.reason()).isEqualTo(KeywordLint.Reason.ALREADY_IN_THE_TITLE));
    }

    @Test
    void theTitleComparisonIgnoresCaseAndAccents() {
        assertThat(KeywordLint.check("Verificare factură lunară", List.of("FACTURA")))
                .singleElement()
                .satisfies(fault -> assertThat(fault.reason()).isEqualTo(KeywordLint.Reason.ALREADY_IN_THE_TITLE));
    }

    @Test
    void aPhraseContainingAGenericWordIsAccepted() {
        assertThat(KeywordLint.check("Weekly grocer posts", List.of("publish the carousel", "grocer captions")))
                .isEmpty();
    }

    @Test
    void blanksAndDuplicatesAreReported() {
        assertThat(KeywordLint.check("Monthly report", List.of("client brief", "  ", "client brief")))
                .extracting(KeywordLint.Fault::reason)
                .containsExactly(KeywordLint.Reason.BLANK, KeywordLint.Reason.DUPLICATE);
    }

    @Test
    void noKeywordsIsNotAFault() {
        assertThat(KeywordLint.check("Monthly report", List.of())).isEmpty();
        assertThat(KeywordLint.check("Monthly report", null)).isEmpty();
    }

    @Test
    void aWellAuthoredSetPasses() {
        assertThat(KeywordLint.check(
                        "Write the monthly client report",
                        List.of("numbers for the month", "ads recap", "performance summary")))
                .isEmpty();
    }
}
