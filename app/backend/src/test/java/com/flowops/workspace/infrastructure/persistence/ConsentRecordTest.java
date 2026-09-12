package com.flowops.workspace.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.workspace.WorkspaceIntegrationTest;
import com.flowops.workspace.application.shared.port.ConsentCataloguePort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.SaveConsentRecordPort;
import com.flowops.workspace.domain.model.ConsentRecord;
import com.flowops.workspace.domain.model.ConsentRecordId;
import com.flowops.workspace.domain.model.PersonId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("AUTH-ACCEPT-INVITE-01")
class ConsentRecordTest extends WorkspaceIntegrationTest {
    @Autowired
    private SaveConsentRecordPort saveConsentRecord;

    @Autowired
    private ConsentCataloguePort catalogue;

    @Autowired
    private LoadMembershipPort loadMembership;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aConsentRecordKeepsTheWholeTextRatherThanAPointerToIt() throws Exception {
        ConsentCataloguePort.ConsentText shown = catalogue.inLanguage("en");
        PersonId person = aPersonWhoExists();

        saveConsentRecord.save(new ConsentRecord(
                ConsentRecordId.generate(), person, "en", shown.version(), shown.text(), Instant.now()));

        Map<String, Object> stored = theOnlyStoredRecord();
        assertThat(stored.get("consent_text")).isEqualTo(shown.text());
        assertThat(stored.get("version")).isEqualTo(shown.version());
        assertThat(stored.get("language")).isEqualTo("en");
    }

    @Test
    void theStoredVersionIsTheHashOfTheStoredText() throws Exception {
        ConsentCataloguePort.ConsentText shown = catalogue.inLanguage("en");
        saveConsentRecord.save(new ConsentRecord(
                ConsentRecordId.generate(), aPersonWhoExists(), "en", shown.version(), shown.text(), Instant.now()));

        Map<String, Object> stored = theOnlyStoredRecord();

        assertThat(stored.get("version")).isEqualTo(twelveHexOf((String) stored.get("consent_text")));
    }

    @Test
    void aStoredRecordKeepsItsOwnVersionAfterTheWordsHaveMovedOn() throws Exception {
        String whatCosminRead = "An older consent text, agreed to before the words were revised.";
        String versionHeSaw = twelveHexOf(whatCosminRead);

        saveConsentRecord.save(new ConsentRecord(
                ConsentRecordId.generate(), aPersonWhoExists(), "en", versionHeSaw, whatCosminRead, Instant.now()));

        Map<String, Object> stored = theOnlyStoredRecord();
        assertThat(stored.get("version")).isEqualTo(versionHeSaw);
        assertThat(stored.get("consent_text")).isEqualTo(whatCosminRead);
        assertThat(stored.get("version"))
                .as("the row answered with the catalogue's current version rather than the one that was agreed to")
                .isNotEqualTo(catalogue.inLanguage("en").version());
    }

    private PersonId aPersonWhoExists() throws Exception {
        setUpTheWorkspace(anOwnerSignedIn());
        return loadMembership.listAll().getFirst().person();
    }

    private Map<String, Object> theOnlyStoredRecord() {
        return jdbc.queryForList("select * from workspace_consent_record").getFirst();
    }

    private static String twelveHexOf(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest).substring(0, 12);
    }
}
