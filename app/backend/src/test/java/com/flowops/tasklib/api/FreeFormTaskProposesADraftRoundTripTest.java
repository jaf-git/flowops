package com.flowops.tasklib.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.support.CompanyScenarioTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASKLIB-DRAFT-FROM-TASK-01")
class FreeFormTaskProposesADraftRoundTripTest extends CompanyScenarioTest {
    private Company company;

    @BeforeEach
    void aCompany() throws Exception {
        company = buildTheCompany();
    }

    private String createTask(String title, UUID assignee, String extra) throws Exception {
        ResponseEntity<String> created = browser.post(
                "/api/tasks",
                ("{\"title\":\"%s\",\"description\":\"Compară trimestrul trecut cu acesta.\","
                                + "\"assigneeId\":\"%s\",\"deadline\":\"%s\",\"priority\":\"HIGH\"%s}")
                        .formatted(title, assignee, Instant.now().plusSeconds(86_400), extra));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(created.getBody()).get("id").asText();
    }

    private Map<String, Object> templateOf(String taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                select t.id, t.title, t.status, t.times_used, t.priority, t.description
                from task k join task_template t on t.id = k.template_id
                where k.id = ?::uuid
                """,
                taskId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Test
    void aTaskThatNamesNoTemplateGetsOneAndTheLibraryCountsIt() throws Exception {
        String task = createTask("Pregătește raportul lunar pentru Aurora Coffee", company.andrei(), "");

        Map<String, Object> template = templateOf(task);
        assertThat(template)
                .as("the task points at a library entry — the owner's requirement, held in SQL")
                .isNotEmpty();
        assertThat(template.get("title")).isEqualTo("Pregătește raportul lunar pentru Aurora Coffee");
        assertThat(String.valueOf(template.get("status")))
                .as("it lands in the curation queue, not in the approved library")
                .isEqualTo("DRAFT");
        assertThat(((Number) template.get("times_used")).intValue())
                .as("gate 8 joins these two tables and asserts exactly this")
                .isEqualTo(1);
    }

    @Test
    void theDraftTakesThePersonsWordsAndTheirPriority() throws Exception {
        String task = createTask("Trimite oferta către Nimbus Fitness", company.andrei(), "");

        Map<String, Object> template = templateOf(task);
        assertThat(template.get("description")).isEqualTo("Compară trimestrul trecut cu acesta.");
        assertThat(String.valueOf(template.get("priority"))).isEqualTo("HIGH");

        List<Map<String, Object>> unchanged =
                jdbc.queryForList("select title, description, priority from task where id = ?::uuid", task);
        assertThat(unchanged.get(0).get("title"))
                .as("nothing wrote back over what the person typed")
                .isEqualTo("Trimite oferta către Nimbus Fitness");
    }

    @Test
    void aTaskStampedFromATemplateProposesNothing() throws Exception {
        UUID existing = work("Pregătește raportul lunar");
        int draftsBefore = drafts();

        String task =
                createTask("Pregătește raportul lunar", company.andrei(), ",\"templateId\":\"%s\"".formatted(existing));

        assertThat(templateOf(task).get("id"))
                .as("it kept the entry it was stamped from")
                .isEqualTo(existing);
        assertThat(drafts()).as("and proposed no second one").isEqualTo(draftsBefore);
    }

    @Test
    void aTicketProposesNothingAndIsCreatedAnyway() throws Exception {
        int draftsBefore = drafts();

        String ticket = createTask("Sună-l pe furnizor", company.andrei(), ",\"kind\":\"TICKET\"");

        assertThat(templateOf(ticket))
                .as("a ticket names no library entry, by constraint")
                .isEmpty();
        assertThat(drafts()).isEqualTo(draftsBefore);
        assertThat(jdbc.queryForObject("select count(*) from task where id = ?::uuid", Integer.class, ticket))
                .as("and the ticket itself was still created")
                .isEqualTo(1);
    }

    private int drafts() {
        return jdbc.queryForObject("select count(*) from task_template where status = 'DRAFT'", Integer.class);
    }
}
