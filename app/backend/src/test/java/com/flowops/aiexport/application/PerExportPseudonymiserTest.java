package com.flowops.aiexport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PerExportPseudonymiserTest {
    private static final UUID MARIA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID IONUT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void givesOnePersonTheSameTokenThroughoutOneExport() {
        PerExportPseudonymiser export = new PerExportPseudonymiser();

        assertThat(export.of(MARIA))
                .as("an analysis must be able to see that several records share an actor")
                .isEqualTo(export.of(MARIA));
    }

    @Test
    void givesDifferentPeopleDifferentTokens() {
        PerExportPseudonymiser export = new PerExportPseudonymiser();

        assertThat(export.of(MARIA)).isNotEqualTo(export.of(IONUT));
    }

    @Test
    void givesThatPersonAnUnrelatedTokenInTheNextExport() {
        String monday = new PerExportPseudonymiser().of(MARIA);
        String friday = new PerExportPseudonymiser().of(MARIA);

        assertThat(monday).isNotEqualTo(friday);
    }

    @Test
    void neverRepeatsATokenAcrossManyExports() {
        Set<String> seen = new HashSet<>();
        for (int export = 0; export < 500; export++) {
            seen.add(new PerExportPseudonymiser().of(MARIA));
        }

        assertThat(seen).hasSize(500);
    }

    @Test
    void carriesNothingOfTheIdentifierItStandsFor() {
        PerExportPseudonymiser export = new PerExportPseudonymiser();

        String token = export.of(MARIA);

        assertThat(token).doesNotContain(MARIA.toString());
        assertThat(token).doesNotContain(MARIA.toString().replace("-", ""));
        assertThat(token).doesNotContain("1111");
    }

    @Test
    void looksLikeATokenAndNotLikeAnIdentifier() {
        String token = new PerExportPseudonymiser().of(MARIA);

        assertThat(token).startsWith("p_");
        assertThat(token).matches("p_[A-Za-z0-9_-]{22}");
    }

    @Test
    void movesTheWorkspaceTokenBetweenExportsToo() {
        assertThat(new PerExportPseudonymiser().ofWorkspace()).isNotEqualTo(new PerExportPseudonymiser().ofWorkspace());
    }

    @Test
    void isAPureFunctionOnceTheSaltIsFixed() {
        byte[] salt = "a-fixed-salt-for-this-test-only".getBytes(StandardCharsets.UTF_8);

        assertThat(new PerExportPseudonymiser(salt).of(MARIA)).isEqualTo(new PerExportPseudonymiser(salt).of(MARIA));
        assertThat(new PerExportPseudonymiser(salt).of(MARIA)).isNotEqualTo(new PerExportPseudonymiser(salt).of(IONUT));
    }
}
