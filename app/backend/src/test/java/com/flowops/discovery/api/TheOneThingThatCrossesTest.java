package com.flowops.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-FORMALISE-WORK-01")
@Tag("DISCOVERY-COMPOSE-PROCESS-01")
class TheOneThingThatCrossesTest extends CompanyScenarioTest {
    private String conversationBetween(RoundTripClient who, UUID person) throws Exception {
        ResponseEntity<String> started = who.post("/api/conversations", "{\"personId\":\"%s\"}".formatted(person));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("id").asText();
    }

    private String said(RoundTripClient who, String conversation, String body) throws Exception {
        ResponseEntity<String> sent =
                who.post("/api/conversations/" + conversation + "/messages", "{\"body\":\"%s\"}".formatted(body));
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(sent.getBody()).get("id").asText();
    }

    private String openJob(RoundTripClient who, String messageId, String name) throws Exception {
        ResponseEntity<String> opened =
                who.post("/api/discovery/jobs", "{\"messageId\":\"%s\",\"name\":\"%s\"}".formatted(messageId, name));
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(opened.getBody()).get("jobId").asText();
    }

    private JsonNode mark(RoundTripClient who, String messageId, String jobId, String direction, UUID performer)
            throws Exception {
        String body = performer == null
                ? "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":null}"
                        .formatted(messageId, jobId, direction)
                : "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":\"%s\"}"
                        .formatted(messageId, jobId, direction, performer);
        ResponseEntity<String> marked = who.post("/api/discovery/nodes", body);
        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(marked.getBody());
    }

    private ResponseEntity<String> formalise(RoundTripClient who, String nodeId, String title, String... steps) {
        String listed = String.join("\",\"", steps);
        String body = steps.length == 0
                ? "{\"title\":\"%s\",\"detail\":null,\"steps\":[]}".formatted(title)
                : "{\"title\":\"%s\",\"detail\":null,\"steps\":[\"%s\"]}".formatted(title, listed);
        return who.post("/api/discovery/nodes/" + nodeId + "/formalise", body);
    }

    private ResponseEntity<String> compose(RoundTripClient who, String trackId, String name) {
        return who.post("/api/discovery/tracks/" + trackId + "/compose", "{\"name\":\"%s\"}".formatted(name));
    }

    private record Executed(Long tasks, Long runs) {}

    private Executed whatTheApplicationHolds() {
        return new Executed(
                jdbc.queryForObject("select count(*) from task", Long.class),
                jdbc.queryForObject("select count(*) from process_instance", Long.class));
    }

    @Test
    void aMarkedNodeBecomesATaskTemplateThatTasklibItselfCanBeAskedFor() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job = openJob(browser, brief, "Rebranding Aurora Coffee");
        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        String node = mark(browser, asked, job, "REQUEST", company.andrei())
                .get("nodeId")
                .asText();
        Executed before = whatTheApplicationHolds();

        ResponseEntity<String> saved =
                formalise(browser, node, "Moodboard for a rebrand", "Collect references", "Lay out the board");

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode crossed = json.readTree(saved.getBody());
        assertThat(crossed.get("nodeId").asText())
                .as("the observation is not consumed; it is still there and still says what it said")
                .isEqualTo(node);
        String template = crossed.get("taskTemplateId").asText();

        ResponseEntity<String> fromTheLibrary = browser.get("/api/task-templates/" + template);

        assertThat(fromTheLibrary.getStatusCode())
                .as("read back through TASKLIB's own door, because a returned identifier is not a row")
                .isEqualTo(HttpStatus.OK);
        JsonNode entry = json.readTree(fromTheLibrary.getBody());
        assertThat(entry.get("title").asText()).isEqualTo("Moodboard for a rebrand");
        assertThat(entry.get("status").asText())
                .as("a template a person saved with the evidence in front of them is approved, not queued")
                .isEqualTo("APPROVED");
        assertThat(entry.get("checklist"))
                .as("the steps somebody wrote become the checklist — DISCOVERY_03 section 3")
                .hasSize(2);
        assertThat(entry.get("checklist").get(0).asText()).isEqualTo("Collect references");
        assertThat(entry.get("checklist").get(1).asText()).isEqualTo("Lay out the board");

        assertThat(jdbc.queryForObject("select task_template_id from work_node where id = ?::uuid", String.class, node))
                .as("the node records that the crossing happened, which is what the composition gate reads")
                .isEqualTo(template);
        assertThat(whatTheApplicationHolds())
                .as("A NODE BECOMES A TEMPLATE AND NEVER A TASK — invariant I7, counted rather than trusted")
                .isEqualTo(before);
    }

    @Test
    void formalisingTheSameWorkTwiceIsRefusedAndTheFirstTemplateStands() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job = openJob(browser, brief, "Rebranding Aurora Coffee");
        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        String node = mark(browser, asked, job, "REQUEST", company.andrei())
                .get("nodeId")
                .asText();

        String mariasTemplate = json.readTree(
                        formalise(browser, node, "Moodboard for a rebrand").getBody())
                .get("taskTemplateId")
                .asText();
        Long templatesAfterTheFirst = jdbc.queryForObject("select count(*) from task_template", Long.class);
        Executed before = whatTheApplicationHolds();

        ResponseEntity<String> again = formalise(ionut, node, "Moodboard, second attempt");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(again.getBody()).get("code").asText()).isEqualTo("NODE_ALREADY_TEMPLATED");
        assertThat(jdbc.queryForObject("select task_template_id from work_node where id = ?::uuid", String.class, node))
                .as("the node still points at the first template, because the second would disown it")
                .isEqualTo(mariasTemplate);
        assertThat(jdbc.queryForObject("select count(*) from task_template", Long.class))
                .as("refused before anything was created — a refusal that left a row behind is the duplicate")
                .isEqualTo(templatesAfterTheFirst);
        assertThat(whatTheApplicationHolds()).isEqualTo(before);
    }

    @Test
    void aThreadWithOneUnTemplatedUnitOfWorkIsRefusedRatherThanComposedWithAHoleInIt() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job = openJob(browser, brief, "Rebranding Aurora Coffee");

        String first = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        JsonNode moodboard = mark(browser, first, job, "REQUEST", company.andrei());
        String thread = moodboard.get("trackId").asText();
        String second = said(browser, conversation, "Si variantele de logo, tot pentru joi");
        JsonNode variants = mark(browser, second, job, "REQUEST", company.andrei());
        assertThat(variants.get("trackId").asText())
                .as("the same pair in the same engagement is one thread, which is what makes this case exist")
                .isEqualTo(thread);

        formalise(browser, moodboard.get("nodeId").asText(), "Moodboard for a rebrand");
        Long processTemplatesBefore = jdbc.queryForObject("select count(*) from process_template", Long.class);
        Executed before = whatTheApplicationHolds();

        ResponseEntity<String> refused = compose(browser, thread, "Rebrand");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TRACK_NOT_FULLY_TEMPLATED");
        assertThat(jdbc.queryForObject(
                        "select process_template_id from track where id = ?::uuid", String.class, thread))
                .as("nothing was composed, so the thread records no crossing")
                .isNull();
        assertThat(jdbc.queryForObject("select count(*) from process_template", Long.class))
                .as("and no half-written template was left behind for somebody to find later")
                .isEqualTo(processTemplatesBefore);
        assertThat(whatTheApplicationHolds()).isEqualTo(before);
    }

    @Test
    void aFullyTemplatedThreadBecomesAProcessTemplateAndStartsNothing() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job = openJob(browser, brief, "Rebranding Aurora Coffee");

        String first = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        JsonNode moodboard = mark(browser, first, job, "REQUEST", company.andrei());
        String thread = moodboard.get("trackId").asText();
        String second = said(browser, conversation, "Si variantele de logo, tot pentru joi");
        JsonNode variants = mark(browser, second, job, "REQUEST", company.andrei());

        String moodboardTemplate = json.readTree(
                        formalise(browser, moodboard.get("nodeId").asText(), "Moodboard for a rebrand")
                                .getBody())
                .get("taskTemplateId")
                .asText();
        String variantsTemplate = json.readTree(
                        formalise(browser, variants.get("nodeId").asText(), "Logo variants")
                                .getBody())
                .get("taskTemplateId")
                .asText();
        Executed before = whatTheApplicationHolds();

        ResponseEntity<String> composed = compose(browser, thread, "Rebrand");

        assertThat(composed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode crossed = json.readTree(composed.getBody());
        assertThat(crossed.get("trackId").asText()).isEqualTo(thread);
        String processTemplate = crossed.get("processTemplateId").asText();

        ResponseEntity<String> fromTheLibrary = browser.get("/api/process-templates/" + processTemplate);

        assertThat(fromTheLibrary.getStatusCode())
                .as("read back through PROCESS's own door, for the same reason the task template was")
                .isEqualTo(HttpStatus.OK);
        JsonNode template = json.readTree(fromTheLibrary.getBody());
        assertThat(template.get("name").asText()).isEqualTo("Rebrand");
        assertThat(template.get("steps"))
                .as("one step per unit of work in the thread, and no step that is not a task template — I6")
                .hasSize(2);
        assertThat(template.get("steps").get(0).get("taskTemplateId").asText())
                .as("oldest first, which is the only order an observed thread has")
                .isEqualTo(moodboardTemplate);
        assertThat(template.get("steps").get(1).get("taskTemplateId").asText()).isEqualTo(variantsTemplate);

        assertThat(jdbc.queryForObject(
                        "select process_template_id from track where id = ?::uuid", String.class, thread))
                .isEqualTo(processTemplate);
        assertThat(whatTheApplicationHolds())
                .as("NO TASK AND NO RUN WAS CREATED BY ANY OF IT — the boundary this whole zone rests on")
                .isEqualTo(before);
    }

    @Test
    void anEmployeeWhoMarksWorkAllDayStillCannotAuthorAProcessTemplateThroughThisDoor() throws Exception {
        buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        Executed before = whatTheApplicationHolds();

        ResponseEntity<String> refused = compose(andrei, UUID.randomUUID().toString(), "Rebrand");

        assertThat(refused.getStatusCode())
                .as("refused before the thread is even looked for, which is where a permission belongs")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
        assertThat(whatTheApplicationHolds()).isEqualTo(before);
    }
}
