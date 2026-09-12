package com.flowops.nodepipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@Tag("NODEPIPE-COMPOSE-01")
@Tag("roundtrip")
class ComposeDiscoveredRoundTripTest extends CompanyScenarioTest {
    @Test
    void writesDownWhatWasDiscoveredAndSaysWhatItWrote() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        var composed = maria.post("/api/node-pipeline/compositions", "");

        assertThat(composed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode answer = json.readTree(composed.getBody());
        assertThat(answer.has("stepKindsFound")).isTrue();
        assertThat(answer.has("taskTemplatesDrafted")).isTrue();
        assertThat(answer.get("processes").isArray()).isTrue();
        assertThat(answer.get("alreadyThere").isArray()).isTrue();
    }

    @Test
    void writesNothingThatIsApprovedAndNothingThatBlocks() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        maria.post("/api/node-pipeline/compositions", "");

        assertThat(jdbc.queryForObject(
                        "select count(*) from step_dependency where kind = 'OBSERVED' and confirmed_by is not null",
                        Integer.class))
                .as("an observation nobody made a decision about cannot name a decider")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_template where status = 'APPROVED' and description like "
                                + "'Drafted from%'",
                        Integer.class))
                .as("ADR-015: no path in this system produces an APPROVED artefact")
                .isZero();
    }

    @Test
    void pressingItTwiceDoesNotWriteTheLibraryTwice() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        maria.post("/api/node-pipeline/compositions", "");
        int processesAfterFirst = countProcesses();
        int templatesAfterFirst = countTemplates();

        var again = maria.post("/api/node-pipeline/compositions", "");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(countProcesses())
                .as("a second press must not produce 'Client launch (2)'")
                .isEqualTo(processesAfterFirst);
        assertThat(countTemplates()).as("nor a second Design beside the first").isEqualTo(templatesAfterFirst);
    }

    private int countProcesses() {
        return jdbc.queryForObject("select count(*) from process_template", Integer.class);
    }

    private int countTemplates() {
        return jdbc.queryForObject("select count(*) from task_template", Integer.class);
    }
}
