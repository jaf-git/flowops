package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

abstract class ProcessScenarioTest extends CompanyScenarioTest {
    protected JsonNode authorOnboarding(RoundTripClient browser, String name) throws Exception {
        ResponseEntity<String> created = browser.post("/api/process-templates", onboarding(name));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(created.getBody());
    }

    protected String onboarding(String name) {
        return ("{\"name\":\"%s\",\"overview\":\"Cum integrăm un coleg nou\",\"steps\":["
                        + "{\"taskTemplateId\":\"%s\",\"expectedDurationHours\":4},"
                        + "{\"taskTemplateId\":\"%s\",\"expectedDurationHours\":8},"
                        + "{\"taskTemplateId\":\"%s\",\"expectedDurationHours\":2}]}")
                .formatted(name, work("Pregătește echipamentul"), work("Prima zi"), work("Evaluare la o lună"));
    }

    protected static String edge(String dependent, String dependsOn) {
        return "{\"dependentStepId\":\"%s\",\"dependsOnStepId\":\"%s\"}".formatted(dependent, dependsOn);
    }

    protected String stepId(JsonNode template, int position) {
        return template.get("steps").get(position).get("id").asText();
    }

    protected String templateId(JsonNode template) {
        return template.get("id").asText();
    }

    protected int eventsFor(String templateId) {
        return jdbc.queryForObject(
                "select count(*) from process_event where template_id = ?::uuid", Integer.class, templateId);
    }

    protected int eventsOn(String instanceId) {
        return jdbc.queryForObject(
                "select count(*) from process_event where instance_id = ?::uuid", Integer.class, instanceId);
    }
}
