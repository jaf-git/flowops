package com.flowops.tasklib.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.support.CompanyScenarioTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASKLIB-EDIT-TEMPLATE-01")
class SubmittingADraftForApprovalSubmitsItTest extends CompanyScenarioTest {
    @BeforeEach
    void aCompany() throws Exception {
        buildTheCompany();
    }

    @Test
    @DisplayName("a draft edited with submitForApproval leaves the author's hands")
    void submittingADraftSubmitsIt() throws Exception {
        UUID draft = writeADraft("Verificare factură lunară");
        assertThat(statusOf(draft)).isEqualTo("DRAFT");

        ResponseEntity<String> edited = browser.patch(
                "/api/task-templates/" + draft,
                """
                {"title":"Verificare factură lunară","priority":"NORMAL","submitForApproval":true}
                """);

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(draft))
                .as("the flag arrived on the request and has to reach the aggregate")
                .isEqualTo("PROPOSED");
    }

    @Test
    @DisplayName("an ordinary edit leaves it a draft, so saving is not a submission")
    void anOrdinaryEditDoesNotSubmit() throws Exception {
        UUID draft = writeADraft("Raport lunar");

        ResponseEntity<String> edited = browser.patch(
                "/api/task-templates/" + draft,
                """
                {"title":"Raport lunar corectat","priority":"NORMAL","submitForApproval":false}
                """);

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(statusOf(draft)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("re-submitting something already in the queue is accepted and changes nothing")
    void resubmittingIsNotAFailure() throws Exception {
        UUID draft = writeADraft("Pregătire ședință");
        browser.patch(
                "/api/task-templates/" + draft,
                """
                {"title":"Pregătire ședință","priority":"NORMAL","submitForApproval":true}
                """);

        ResponseEntity<String> again = browser.patch(
                "/api/task-templates/" + draft,
                """
                {"title":"Pregătire ședință","priority":"NORMAL","submitForApproval":true}
                """);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(draft)).isEqualTo("PROPOSED");
    }

    private UUID writeADraft(String title) throws Exception {
        ResponseEntity<String> created = browser.post(
                "/api/task-templates",
                """
                {"title":"%s","priority":"NORMAL","submitForApproval":false}
                """
                        .formatted(title));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(json.readTree(created.getBody()).get("id").asText());
    }

    private String statusOf(UUID template) {
        return jdbc.queryForObject("select status from task_template where id = ?", String.class, template);
    }
}
