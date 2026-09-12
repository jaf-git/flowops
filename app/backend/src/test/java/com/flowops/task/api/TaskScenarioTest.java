package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.support.CompanyScenarioTest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

abstract class TaskScenarioTest extends CompanyScenarioTest {
    protected String createTaskFor(UUID assignee) throws Exception {
        ResponseEntity<String> created = browser.post("/api/tasks", body(assignee, tomorrow()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(created.getBody()).get("id").asText();
    }

    protected static String body(UUID assignee, Instant deadline) {
        return ("{\"title\":\"Draft the supplier review\","
                        + "\"description\":\"Compare last quarter against this one.\","
                        + "\"assigneeId\":\"%s\",\"deadline\":\"%s\",\"priority\":\"NORMAL\"}")
                .formatted(assignee, deadline);
    }

    protected static Instant tomorrow() {
        return Instant.now().plusSeconds(86_400);
    }
}
