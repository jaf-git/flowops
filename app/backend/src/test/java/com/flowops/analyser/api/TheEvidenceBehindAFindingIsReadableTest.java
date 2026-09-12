package com.flowops.analyser.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("ANALYSER-VIEW-FINDINGS-01")
class TheEvidenceBehindAFindingIsReadableTest extends CompanyScenarioTest {
    @Test
    @DisplayName("a finding's marks arrive with their words, their people and their departments")
    void theMarksArriveResolved() throws Exception {
        buildTheCompany();
        RoundTripClient owner = signedInBrowser("ionut@atelier.ro");
        assertThat(owner.post("/api/analysis/runs", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode withNodes = aFindingRestingOnNodes(owner);
        if (withNodes == null) {
            return;
        }

        JsonNode node = withNodes.get("nodes").get(0);

        assertThat(node.has("text")).isTrue();
        assertThat(node.has("messageText")).isTrue();
        assertThat(node.has("checklist")).isTrue();

        assertThat(node.get("marker").has("name")).isTrue();
        assertThat(node.get("performer").has("name")).isTrue();
        assertThat(node.get("marker").has("department")).isTrue();
        assertThat(node.get("performer").has("department")).isTrue();

        assertThat(node.has("jobId")).isTrue();
        assertThat(node.has("conversationId")).isTrue();
        assertThat(node.has("messageId")).isTrue();

        JsonNode spread = withNodes.get("spread");
        assertThat(spread.has("crossesDepartments")).isTrue();
        assertThat(spread.get("crossesDepartments").isBoolean()).isTrue();
        assertThat(spread.get("departments").isArray()).isTrue();
    }

    @Test
    @DisplayName("an identifier naming nothing is a 404, not three empty lists")
    void nothingIsNotEmptiness() throws Exception {
        buildTheCompany();
        RoundTripClient owner = signedInBrowser("ionut@atelier.ro");

        ResponseEntity<String> absent = owner.get("/api/analysis/findings/" + UUID.randomUUID() + "/evidence");

        assertThat(absent.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private JsonNode aFindingRestingOnNodes(RoundTripClient who) throws Exception {
        ResponseEntity<String> queue = who.get("/api/analysis/findings");
        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> ids = new ArrayList<>();
        for (JsonNode group : json.readTree(queue.getBody()).get("groups")) {
            for (JsonNode item : group.get("items")) {
                ids.add(item.get("id").asText());
            }
        }

        for (String id : ids) {
            ResponseEntity<String> response = who.get("/api/analysis/findings/" + id + "/evidence");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode evidence = json.readTree(response.getBody());
            if (evidence.get("nodes").size() > 0) {
                return evidence;
            }
        }

        return null;
    }
}
