package com.flowops.nodepipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("NODEPIPE-RESEMBLE-WORK-01")
class ResemblanceRoundTripTest extends CompanyScenarioTest {
    private static final String SAID = "Wrote the October Iulius monthly summary, what worked and what to change.";

    private UUID template;

    @BeforeEach
    void aLibraryWithOneApprovedTemplateAndSomebodyWhoDoesThatWork() throws Exception {
        Company company = buildTheCompany();
        template = work("Monthly write-up October Iulius summary");
        approve(template);
        marksTheKindOfWorkTheTemplateDescribes(company);
    }

    private void marksTheKindOfWorkTheTemplateDescribes(Company company) throws Exception {
        String room = json.readTree(browser.post("/api/conversations/group", "{\"name\":\"Nord Creative\"}")
                        .getBody())
                .get("id")
                .asText();
        String opening = said(room, "Opening the October Iulius retainer.");
        String job = json.readTree(browser.post(
                                "/api/discovery/jobs",
                                """
                                {"messageId":"%s","name":"October Iulius","projectLabel":"Retainer Iulius"}
                                """
                                        .formatted(opening))
                        .getBody())
                .get("jobId")
                .asText();

        ResponseEntity<String> marked = browser.post(
                "/api/discovery/work",
                """
                {"messageId":"%s","jobId":"%s","verb":"CREATE","performerId":"%s","workType":"SCHEDULING","joining":null}
                """
                        .formatted(said(room, "Pulled the October Iulius figures together."), job, company.maria()));
        assertThat(marked.getStatusCode())
                .as("the fixture is built through the door, or it proves nothing about the rows")
                .isEqualTo(HttpStatus.CREATED);

        assertThat(jdbc.queryForObject(
                        "select count(*) from work_node where performer_id = ? and work_type = 'SCHEDULING'",
                        Integer.class,
                        company.maria()))
                .as("the caller has to have marked SCHEDULING work for the lookup to find any")
                .isGreaterThan(0);
    }

    private String said(String room, String body) throws Exception {
        ResponseEntity<String> sent =
                browser.post("/api/conversations/" + room + "/messages", "{\"body\":\"%s\"}".formatted(body));
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(sent.getBody()).get("id").asText();
    }

    private void approve(UUID id) {
        jdbc.update(
                """
                update task_template
                   set status = 'APPROVED',
                       approved_at = now() - interval '1 day',
                       description = ?,
                       responsible_role = ?,
                       work_type = 'SCHEDULING',
                       keywords = string_to_array('iulius,monthly,october,summary,wrote', ',')
                 where id = ?
                """,
                "Scheduling and reporting does this work. People doing it called it "
                        + "“Monthly write-up October Iulius summary”. The words that recur in it: "
                        + "iulius, monthly, october, summary, wrote.",
                "Scheduling and reporting",
                id);
    }

    private void draft(UUID id) {
        jdbc.update("update task_template set status = 'DRAFT', approved_at = null where id = ?", id);
    }

    private JsonNode resembling(String text) throws Exception {
        ResponseEntity<String> answer = browser.post(
                "/api/node-pipeline/resemblance", "{\"text\":%s}".formatted(json.writeValueAsString(text)));
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(answer.getBody()).get("match");
    }

    @Test
    void aSentenceThatLooksLikeAnApprovedTemplateNamesIt() throws Exception {
        JsonNode match = resembling(SAID);

        assertThat(match.isNull())
                .as("the sentence is the template's own words; if this abstains the feature has nothing to show")
                .isFalse();
        assertThat(match.get("templateId").asText()).isEqualTo(template.toString());
        assertThat(match.get("title").asText()).isEqualTo("Monthly write-up October Iulius summary");
        assertThat(match.get("score").asDouble())
                .as("a single sentence is scored on its words alone, and only a strong reading is shown")
                .isGreaterThanOrEqualTo(0.85);
        assertThat(match.get("why").asText()).isNotBlank();
    }

    @Test
    void agreeingWithSomebodyIsNotDoingTheWork() throws Exception {
        assertThat(resembling("Thanks, that looks great.").isNull())
                .as("it shares almost no words with the template and only the caller's own work type "
                        + "ever made it look like a match")
                .isTrue();
    }

    @Test
    void aDraftIsNeverRecommended() throws Exception {
        assertThat(resembling(SAID).isNull()).isFalse();

        draft(template);

        assertThat(resembling(SAID).isNull())
                .as("the same sentence, the same template, and the only change is that nobody has approved it")
                .isTrue();
    }

    @Test
    void aMatchTooWeakToActOnIsNotOffered() throws Exception {
        jdbc.update("update task_template set keywords = '{}' where id = ?", template);

        assertThat(resembling(SAID).isNull())
                .as("the matcher names the template and then declines it: below the floor is still a refusal")
                .isTrue();
    }

    @Test
    void aQuestionIsNotWork() throws Exception {
        assertThat(resembling("Should we do the October Iulius monthly summary?")
                        .isNull())
                .isTrue();
    }

    @Test
    void aSentenceTooThinToBeEvidenceIsIgnored() throws Exception {
        assertThat(resembling("Done.").isNull()).isTrue();
    }

    @Test
    void anEmptyLibraryAnswersWithNothingRatherThanFailing() throws Exception {
        jdbc.update("update task_template set status = 'DRAFT', approved_at = null");

        assertThat(resembling(SAID).isNull()).isTrue();
    }

    @Test
    void askingManyTimesChangesNothing() throws Exception {
        int runs = jdbc.queryForObject("select count(*) from analysis_run", Integer.class);
        int decisions = jdbc.queryForObject("select count(*) from pipeline_decision", Integer.class);
        int nodes = jdbc.queryForObject("select count(*) from work_node", Integer.class);
        int templates = jdbc.queryForObject("select count(*) from task_template", Integer.class);

        for (int i = 0; i < 20; i++) {
            resembling(SAID);
        }

        assertThat(jdbc.queryForObject("select count(*) from analysis_run", Integer.class))
                .isEqualTo(runs);
        assertThat(jdbc.queryForObject("select count(*) from pipeline_decision", Integer.class))
                .isEqualTo(decisions);
        assertThat(jdbc.queryForObject("select count(*) from work_node", Integer.class))
                .isEqualTo(nodes);
        assertThat(jdbc.queryForObject("select count(*) from task_template", Integer.class))
                .isEqualTo(templates);
    }
}
