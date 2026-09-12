package com.flowops.tasklib.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("SOP-METADATA-01")
class TemplateMetadataRoundTripTest extends CompanyScenarioTest {
    private UUID template;

    @BeforeEach
    void aLibraryWithOneTemplate() throws Exception {
        buildTheCompany();
        template = work("Pregătește raportul lunar");
    }

    private JsonNode readTemplate() throws Exception {
        ResponseEntity<String> read = browser.get("/api/task-templates/" + template);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(read.getBody()).get("metadata");
    }

    private ResponseEntity<String> answer(RoundTripClient who, String field, String value) throws Exception {
        return who.patch(
                "/api/task-templates/" + template + "/metadata",
                "{\"field\":\"%s\",\"answer\":\"%s\"}".formatted(field, value));
    }

    @Test
    void sixAnswersCompleteATemplateAndTheQuestionMovesOnEachTime() throws Exception {
        assertThat(readTemplate().get("nextAsk").asText())
                .as("a template nobody has been asked about starts at the first question")
                .isEqualTo("RESPONSIBLE_ROLE");

        assertThat(answer(browser, "RESPONSIBLE_ROLE", "EMPLOYEE").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(readTemplate().get("nextAsk").asText())
                .as("answering one question moves to the next, and never repeats the one just answered")
                .isEqualTo("TRIGGER_NOTE");

        answer(browser, "TRIGGER_NOTE", "The month closes");
        answer(browser, "REQUIRED_INPUT", "Last month's figures");
        answer(browser, "EXPECTED_OUTPUT", "A signed-off report");
        answer(browser, "OUTPUT_KIND", "REPORT");
        answer(browser, "COMPLETION_CRITERIA", "The client has replied approving it");

        JsonNode complete = readTemplate();
        assertThat(complete.get("nextAsk").isNull())
                .as("after six uses the template is complete and nobody opened a form")
                .isTrue();
        assertThat(complete.get("missing")).isEmpty();
        assertThat(complete.get("responsibleRole").asText()).isEqualTo("EMPLOYEE");
        assertThat(complete.get("outputKind").asText()).isEqualTo("REPORT");
    }

    @Test
    void skippingLeavesNoTraceAndTheSameQuestionIsAskedAgain() throws Exception {
        assertThat(readTemplate().get("nextAsk").asText()).isEqualTo("RESPONSIBLE_ROLE");

        assertThat(readTemplate().get("nextAsk").asText())
                .as("the next use asks the same question, because nothing recorded that it was refused")
                .isEqualTo("RESPONSIBLE_ROLE");

        answer(browser, "TRIGGER_NOTE", "The month closes");
        assertThat(readTemplate().get("triggerNote").asText()).isEqualTo("The month closes");
        answer(browser, "TRIGGER_NOTE", "");
        assertThat(readTemplate().get("triggerNote").isNull())
                .as("a blank answer clears the field, so somebody can withdraw what they no longer stand behind")
                .isTrue();
    }

    @Test
    void anEmployeeMayNotRecordAnOrganisationalDecision() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        assertThat(answer(andrei, "RESPONSIBLE_ROLE", "OWNER").getStatusCode())
                .as("asking an employee who normally does this is asking them to decide it")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(readTemplate().get("responsibleRole").isNull())
                .as("and the refusal wrote nothing")
                .isTrue();
    }

    @Test
    void aClosedVocabularyRefusesAnOpenOneDoesNotAndFreeTextCannotBeWrong() throws Exception {
        assertThat(answer(browser, "OUTPUT_KIND", "SPREADSHEET").getStatusCode())
                .as("a kind of output this product does not recognise is still refused")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(answer(browser, "RESPONSIBLE_ROLE", "Designer").getStatusCode())
                .as("a role V64 seeded is exactly what this field is for")
                .isEqualTo(HttpStatus.OK);
        assertThat(readTemplate().get("responsibleRole").asText())
                .as("and it is stored in the case it was seeded in — uppercasing it would match no "
                        + "functional_role row, and the lookup would come back empty silently")
                .isEqualTo("Designer");

        assertThat(answer(browser, "RESPONSIBLE_ROLE", "Videographer").getStatusCode())
                .as("a workspace that hired one is not blocked on a migration")
                .isEqualTo(HttpStatus.OK);

        assertThat(answer(browser, "COMPLETION_CRITERIA", "Whenever it feels finished, honestly")
                        .getStatusCode())
                .as("free text cannot be wrong, which is the point of its being free text")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void aTemplateWithNoMetadataIsStillAValidTemplate() throws Exception {
        JsonNode untouched = readTemplate();

        assertThat(untouched.get("responsibleRole").isNull()).isTrue();
        assertThat(untouched.get("missing").size())
                .as("all six unanswered, and the template is perfectly usable")
                .isEqualTo(6);
        assertThat(untouched.has("answeredCount"))
                .as("no count travels to the client: SOP_01 section 4 refuses a completeness meter, "
                        + "because a screen measuring how much of a chore remains makes it feel compulsory")
                .isFalse();
    }
}
