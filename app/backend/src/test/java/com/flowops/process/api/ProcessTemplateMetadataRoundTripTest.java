package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("SOP-METADATA-01")
class ProcessTemplateMetadataRoundTripTest extends ProcessScenarioTest {
    private String template;

    @BeforeEach
    void aTemplate() throws Exception {
        buildTheCompany();
        template = templateId(authorOnboarding(browser, "Integrare angajat nou"));
    }

    private JsonNode metadata() throws Exception {
        return json.readTree(browser.get("/api/process-templates/" + template).getBody())
                .get("metadata");
    }

    private ResponseEntity<String> describe(String body) {
        return browser.patch("/api/process-templates/" + template + "/metadata", body);
    }

    @Test
    void aTemplateStartsSayingNothingAndKeepsWorking() throws Exception {
        JsonNode blank = metadata();

        assertThat(blank.get("triggerNote").isNull()).isTrue();
        assertThat(blank.get("ownerRole").isNull())
                .as("every field is nullable forever; a template with none of them runs as it always has")
                .isTrue();
    }

    @Test
    void thethreeFieldsAreRecordedAndReadBack() throws Exception {
        assertThat(describe(
                                """
                        {"triggerNote":"A client signs the contract",
                         "endCondition":"The client has approved the final delivery",
                         "ownerRole":"MANAGER"}
                        """)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode described = metadata();
        assertThat(described.get("triggerNote").asText()).isEqualTo("A client signs the contract");
        assertThat(described.get("endCondition").asText()).isEqualTo("The client has approved the final delivery");
        assertThat(described.get("ownerRole").asText())
                .as("a role, never a person — a procedure naming Andrei stops being true the day Andrei leaves")
                .isEqualTo("MANAGER");
    }

    @Test
    void nullsClearWhatWasThere() throws Exception {
        describe("{\"triggerNote\":\"A client signs\",\"endCondition\":null,\"ownerRole\":\"OWNER\"}");
        describe("{\"triggerNote\":null,\"endCondition\":null,\"ownerRole\":null}");

        assertThat(metadata().get("triggerNote").isNull())
                .as("how somebody withdraws what they no longer stand behind")
                .isTrue();
    }

    @Test
    void aFunctionalRoleIsAcceptedAndKeepsItsCase() throws Exception {
        assertThat(describe("{\"triggerNote\":null,\"endCondition\":null,\"ownerRole\":\"Account manager\"}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(metadata().get("ownerRole").asText()).isEqualTo("Account manager");
    }

    @Test
    void describingATemplateLeavesItsStepsAlone() throws Exception {
        describe("{\"triggerNote\":\"A client signs\",\"endCondition\":null,\"ownerRole\":\"MANAGER\"}");

        JsonNode after =
                json.readTree(browser.get("/api/process-templates/" + template).getBody());
        assertThat(after.get("steps").size()).isEqualTo(3);
        assertThat(after.get("steps").get(0).get("title").asText()).isNotBlank();
    }
}
