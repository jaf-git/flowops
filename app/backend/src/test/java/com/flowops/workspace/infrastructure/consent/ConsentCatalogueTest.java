package com.flowops.workspace.infrastructure.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.workspace.application.shared.port.ConsentCataloguePort.ConsentText;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("AUTH-ACCEPT-INVITE-01")
class ConsentCatalogueTest {
    private final ConsentCatalogue catalogue = new ConsentCatalogue("consent/consent.%s.md");

    @Test
    void theVersionIsTheHashOfTheTextThatWasServed() {
        for (String language : new String[] {"en"}) {
            ConsentText consent = catalogue.inLanguage(language);

            assertThat(consent.version())
                    .as("the version served for %s", language)
                    .isEqualTo(twelveHexOfSha256(consent.text()));
        }
    }

    @Test
    void aLanguageThisProductDoesNotShipFallsBackToEnglish() {
        assertThat(catalogue.inLanguage("de")).isEqualTo(catalogue.inLanguage("en"));
        assertThat(catalogue.inLanguage("EN")).isEqualTo(catalogue.inLanguage("en"));
    }

    @Test
    void aMissingTextIsRefusedWhenTheCatalogueIsBuilt() {
        assertThatThrownBy(() -> new ConsentCatalogue("consent/absent.%s.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consent");
    }

    @Test
    void theTextIsTheWholeOfWhatSomebodyAgreesTo() {
        for (String language : new String[] {"en"}) {
            assertThat(catalogue.inLanguage(language).text().length())
                    .as("the %s text is long enough to be consent rather than a placeholder", language)
                    .isGreaterThan(500);
        }
    }

    @Test
    void theVersionDoesNotDependOnHowTheFileWasCheckedOut() {
        for (String language : new String[] {"en"}) {
            assertThat(catalogue.inLanguage(language).text())
                    .as("the %s text as served", language)
                    .doesNotContain("\r");
        }
    }

    private static String twelveHexOfSha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
