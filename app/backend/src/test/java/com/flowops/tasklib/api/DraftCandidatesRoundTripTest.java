package com.flowops.tasklib.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASKLIB-DRAFT-FROM-TASK-01")
class DraftCandidatesRoundTripTest extends CompanyScenarioTest {
    private Company company;

    @BeforeEach
    void aCompany() throws Exception {
        company = buildTheCompany();
    }

    private void freeFormTask(String title, UUID assignee) throws Exception {
        ResponseEntity<String> created = browser.post(
                "/api/tasks",
                ("{\"title\":\"%s\",\"description\":\"Detalii.\",\"assigneeId\":\"%s\","
                                + "\"deadline\":\"%s\",\"priority\":\"NORMAL\"}")
                        .formatted(title, assignee, Instant.now().plusSeconds(86_400)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode candidates() throws Exception {
        ResponseEntity<String> queue = browser.get("/api/task-templates/draft-candidates");
        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(queue.getBody());
    }

    @Test
    void twentyFreeFormTasksAcrossFourTitlesAnswerFourRows() throws Exception {
        List<String> jobs = List.of(
                "Pregătește raportul lunar",
                "Trimite oferta către client",
                "Verifică factura furnizorului",
                "Programează postările săptămânii");
        for (int round = 0; round < 5; round++) {
            for (String job : jobs) {
                freeFormTask(job, company.andrei());
            }
        }

        JsonNode rows = candidates();

        assertThat(rows.size())
                .as("twenty drafts describing four jobs is four rows, which is the whole ruling")
                .isEqualTo(4);
        List<Integer> counts = new ArrayList<>();
        rows.forEach(row -> counts.add(row.get("drafts").asInt()));
        assertThat(counts).as("and every job is counted five times").containsExactly(5, 5, 5, 5);
    }

    @Test
    void theSameJobDoneByTwoPeopleIsOneRow() throws Exception {
        freeFormTask("Pregătește raportul lunar", company.andrei());
        freeFormTask("Pregătește raportul lunar", company.elena());
        freeFormTask("Actualizează lista de prețuri", company.andrei());

        JsonNode rows = candidates();

        assertThat(rows.size())
                .as("two jobs, not three, and not one per person")
                .isEqualTo(2);
        assertThat(rows.get(0).get("title").asText()).isEqualTo("Pregătește raportul lunar");
        assertThat(rows.get(0).get("drafts").asInt()).isEqualTo(2);
    }

    @Test
    void spellingsOfOneJobGroupTogetherAndAreAllShown() throws Exception {
        freeFormTask("Verifică factura lunară", company.andrei());
        freeFormTask("verifica factura lunara", company.andrei());
        freeFormTask("Verifică  factura   lunară", company.andrei());

        JsonNode rows = candidates();

        assertThat(rows.size()).as("one job however it was typed").isEqualTo(1);
        assertThat(rows.get(0).get("drafts").asInt()).isEqualTo(3);
        assertThat(rows.get(0).get("variants").size())
                .as("and every distinct wording is offered, because *what differs* is the useful answer")
                .isEqualTo(3);
    }

    @Test
    void anEmployeeMayNotReadTheCurationQueue() throws Exception {
        freeFormTask("Pregătește raportul lunar", company.andrei());

        ResponseEntity<String> refused =
                signedInBrowser("andrei@atelier.ro").get("/api/task-templates/draft-candidates");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
