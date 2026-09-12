package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-CATEGORISE-RUNS-01")
class ProcessCategoryRoundTripTest extends CompanyScenarioTest {
    private static final String CATEGORIES = "/api/process-categories";
    private static final String ONBOARDING = "Client onboarding";

    @Test
    void aSecondGroupingOfTheSameNameIgnoringCaseAndSpaceIsAConflictRatherThanAFault() throws Exception {
        buildTheCompany();
        nameAGrouping(ONBOARDING);

        ResponseEntity<String> refused =
                send(browser, HttpMethod.POST, CATEGORIES, Map.of("name", "  client ONBOARDING "));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("CATEGORY_NAME_TAKEN");
        assertThat(jdbc.queryForObject("select count(*) from process_category", Integer.class))
                .as("refused means nothing was written")
                .isEqualTo(1);
    }

    @Test
    void aGroupingFromSomewhereElseIsNotFoundRatherThanAFault() throws Exception {
        buildTheCompany();

        ResponseEntity<String> refused =
                send(browser, HttpMethod.PUT, CATEGORIES + "/" + UUID.randomUUID(), Map.of("name", "Anything"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("CATEGORY_NOT_FOUND");
    }

    @Test
    void aGroupingIsNamedThenRenamedThenRemoved() throws Exception {
        buildTheCompany();
        String onboarding = nameAGrouping(ONBOARDING);

        assertThat(send(browser, HttpMethod.PUT, CATEGORIES + "/" + onboarding, Map.of("name", "Onboarding"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode after = json.readTree(browser.get(CATEGORIES).getBody());
        assertThat(after.get("categories")).hasSize(1);
        assertThat(after.get("categories").get(0).get("name").asText()).isEqualTo("Onboarding");
        assertThat(after.get("categories").get(0).get("runCount").asInt())
                .as("a count keyed to the grouping, and nothing is filed in it yet")
                .isZero();

        assertThat(browser.delete(CATEGORIES + "/" + onboarding).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(json.readTree(browser.get(CATEGORIES).getBody()).get("categories"))
                .isEmpty();
    }

    @Test
    void aManagerMayReadTheGroupingsAndMayNotInventOne() throws Exception {
        buildTheCompany();
        nameAGrouping(ONBOARDING);
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");

        assertThat(json.readTree(ionut.get(CATEGORIES).getBody()).get("categories"))
                .as("reading the vocabulary is not the same act as shaping it")
                .hasSize(1);

        ResponseEntity<String> refused = send(ionut, HttpMethod.POST, CATEGORIES, Map.of("name", "Mine"));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String nameAGrouping(String name) throws Exception {
        ResponseEntity<String> created = send(browser, HttpMethod.POST, CATEGORIES, Map.of("name", name));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(created.getBody()).get("id").asText();
    }

    private ResponseEntity<String> send(RoundTripClient client, HttpMethod method, String path, Map<String, ?> body)
            throws Exception {
        return client.exchange(method, path, json.writeValueAsString(body));
    }
}
