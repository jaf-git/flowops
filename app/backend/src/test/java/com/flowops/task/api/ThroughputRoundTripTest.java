package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASK-THROUGHPUT-01")
class ThroughputRoundTripTest extends TaskScenarioTest {
    private Company company;

    @BeforeEach
    void aCompany() throws Exception {
        company = buildTheCompany();
    }

    private JsonNode throughput(RoundTripClient caller, int weeks) throws Exception {
        ResponseEntity<String> answer = caller.get("/api/tasks/throughput?weeks=" + weeks);
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(answer.getBody());
    }

    private int totalCreated(JsonNode weeks) {
        int total = 0;
        for (JsonNode week : weeks) {
            total += week.get("created").asInt();
        }
        return total;
    }

    @Test
    void answersOneRowPerWeekAskedFor() throws Exception {
        createTaskFor(company.andrei());

        assertThat(throughput(browser, 12)).hasSize(12);
        assertThat(throughput(browser, 4)).hasSize(4);
    }

    @Test
    void boundsTheWindowRatherThanRefusingIt() throws Exception {
        assertThat(throughput(browser, 500)).hasSize(52);
        assertThat(throughput(browser, 1)).hasSize(4);
    }

    @Test
    void anEmployeeCountsOnlyTheWorkTheyCanSee() throws Exception {
        createTaskFor(company.andrei());
        createTaskFor(company.elena());

        int ownerSees = totalCreated(throughput(browser, 12));
        int andreiSees = totalCreated(throughput(signedInBrowser("andrei@atelier.ro"), 12));

        assertThat(ownerSees)
                .as("the owner holds TASK_VIEW_ANY and counts both")
                .isGreaterThanOrEqualTo(2);
        assertThat(andreiSees).as("Andrei counts his own work").isGreaterThanOrEqualTo(1);
        assertThat(andreiSees)
                .as("and not Elena's, which is outside his scope entirely")
                .isLessThan(ownerSees);
    }
}
