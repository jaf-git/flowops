package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-AUTHOR-TEMPLATE-01")
@Tag("PROCESS-DEFINE-DEPENDENCIES-01")
@Tag("PROCESS-EDIT-TEMPLATE-01")
class ProcessTemplateRoundTripTest extends ProcessScenarioTest {
    private Company company;

    @BeforeEach
    void buildTheCompanyFirst() throws Exception {
        company = buildTheCompany();
    }

    @Test
    void authorsATemplateAndGivesEveryStepAnIdentifier() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");

        assertThat(template.get("name").asText()).isEqualTo("Integrare angajat nou");
        assertThat(template.get("active").asBoolean()).isTrue();
        assertThat(template.get("steps")).hasSize(3);
        assertThat(stepId(template, 0)).isNotBlank();
        assertThat(template.get("dependencies")).isEmpty();
        assertThat(eventsFor(templateId(template))).isEqualTo(1);
    }

    @Test
    void persistsTheStepsItReturns() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");

        Integer stored = jdbc.queryForObject(
                "select count(*) from step_definition where template_id = ?::uuid",
                Integer.class,
                templateId(template));

        assertThat(stored).isEqualTo(3);
    }

    @Test
    void refusesATemplateWithNoSteps() throws Exception {
        registerNothingMore();

        ResponseEntity<String> refused =
                browser.post("/api/process-templates", "{\"name\":\"Gol\",\"overview\":\"nimic\",\"steps\":[]}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("REQUEST_INVALID");
    }

    @Test
    void refusesAStepNamingNoWorkAndNamesItsPosition() throws Exception {
        registerNothingMore();

        ResponseEntity<String> refused = browser.post(
                "/api/process-templates",
                "{\"name\":\"Integrare\",\"overview\":null,\"steps\":["
                        + "{\"taskTemplateId\":\"" + work("Pregătește") + "\",\"expectedDurationHours\":null},"
                        + "{\"taskTemplateId\":null,\"expectedDurationHours\":null}]}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = json.readTree(refused.getBody());
        assertThat(body.get("code").asText()).isEqualTo("STEP_TASK_TEMPLATE_REQUIRED");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("steps[1].taskTemplateId");
    }

    @Test
    void refusesASecondTemplateWithTheSameActiveName() throws Exception {
        authorOnboarding(browser, "Integrare angajat nou");

        ResponseEntity<String> refused = browser.post("/api/process-templates", onboarding("Integrare angajat nou"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TEMPLATE_NAME_TAKEN");
    }

    @Test
    void refusesAnEmployeeAuthoringATemplate() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> refused = andrei.post("/api/process-templates", onboarding("Integrare"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("select count(*) from process_template", Integer.class))
                .isZero();
    }

    @Test
    void drawsADependencyAndReturnsTheGraph() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");

        ResponseEntity<String> drawn = browser.post(
                "/api/process-templates/" + templateId(template) + "/dependencies",
                edge(stepId(template, 1), stepId(template, 0)));

        assertThat(drawn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(drawn.getBody()).get("dependencies")).hasSize(1);
        assertThat(eventsFor(templateId(template))).isEqualTo(2);
    }

    @Test
    void refusesAnEdgeThatWouldCloseACycleAndNamesTheSteps() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");
        String id = templateId(template);
        browser.post("/api/process-templates/" + id + "/dependencies", edge(stepId(template, 1), stepId(template, 0)));
        browser.post("/api/process-templates/" + id + "/dependencies", edge(stepId(template, 2), stepId(template, 1)));

        ResponseEntity<String> refused = browser.post(
                "/api/process-templates/" + id + "/dependencies", edge(stepId(template, 0), stepId(template, 2)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = json.readTree(refused.getBody());
        assertThat(body.get("code").asText()).isEqualTo("GRAPH_CYCLE");
        assertThat(body.get("details")).hasSize(3);
    }

    @Test
    void addingTheSameEdgeTwiceAppendsNoSecondEvent() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");
        String id = templateId(template);
        String body = edge(stepId(template, 1), stepId(template, 0));

        browser.post("/api/process-templates/" + id + "/dependencies", body);
        int afterOne = eventsFor(id);
        ResponseEntity<String> again = browser.post("/api/process-templates/" + id + "/dependencies", body);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(again.getBody()).get("dependencies")).hasSize(1);
        assertThat(eventsFor(id)).isEqualTo(afterOne);
    }

    @Test
    void refusesAnEdgeNamingAStepOfAnotherTemplate() throws Exception {
        JsonNode mine = authorOnboarding(browser, "Integrare angajat nou");
        JsonNode other = authorOnboarding(browser, "Închidere lunară");

        ResponseEntity<String> refused = browser.post(
                "/api/process-templates/" + templateId(mine) + "/dependencies",
                edge(stepId(mine, 1), stepId(other, 0)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("CROSS_TEMPLATE_EDGE");
    }

    @Test
    void removesAnEdgeAndLeavesTheTemplateValid() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");
        String id = templateId(template);
        browser.post("/api/process-templates/" + id + "/dependencies", edge(stepId(template, 1), stepId(template, 0)));

        ResponseEntity<String> removed = browser.delete(
                "/api/process-templates/" + id + "/dependencies/" + stepId(template, 1) + "/" + stepId(template, 0));

        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(removed.getBody()).get("dependencies")).isEmpty();
    }

    @Test
    void editsTheOverviewAndLeavesRunningNothingBehind() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");

        ResponseEntity<String> edited = browser.exchange(
                HttpMethod.PATCH,
                "/api/process-templates/" + templateId(template),
                "{\"overview\":\"Varianta scurtă\",\"steps\":[" + keeping(template, 0) + "," + keeping(template, 1)
                        + "," + keeping(template, 2) + "]}");

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(edited.getBody()).get("overview").asText()).isEqualTo("Varianta scurtă");
    }

    @Test
    void removingAStepRemovesTheEdgesThatTouchedIt() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");
        String id = templateId(template);
        browser.post("/api/process-templates/" + id + "/dependencies", edge(stepId(template, 1), stepId(template, 0)));

        ResponseEntity<String> edited = browser.exchange(
                HttpMethod.PATCH,
                "/api/process-templates/" + id,
                "{\"overview\":\"Cum integrăm un coleg nou\",\"steps\":[" + keeping(template, 0) + ","
                        + keeping(template, 2) + "]}");

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(edited.getBody()).get("steps")).hasSize(2);
        assertThat(json.readTree(edited.getBody()).get("dependencies")).isEmpty();
        assertThat(jdbc.queryForObject(
                        "select count(*) from step_dependency where template_id = ?::uuid", Integer.class, id))
                .isZero();
    }

    @Test
    void refusesAnEditThatWouldRemoveEveryStep() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");

        ResponseEntity<String> refused = browser.exchange(
                HttpMethod.PATCH,
                "/api/process-templates/" + templateId(template),
                "{\"overview\":\"nimic\",\"steps\":[]}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TEMPLATE_NEEDS_A_STEP");
    }

    @Test
    void refusesAManagerEditingATemplateSomebodyElseAuthored() throws Exception {
        JsonNode mariasTemplate = authorOnboarding(browser, "Integrare angajat nou");
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");

        ResponseEntity<String> refused = ionut.exchange(
                HttpMethod.PATCH,
                "/api/process-templates/" + templateId(mariasTemplate),
                "{\"overview\":\"al meu acum\",\"steps\":[" + keeping(mariasTemplate, 0) + "]}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject(
                        "select overview from process_template where id = ?::uuid",
                        String.class,
                        templateId(mariasTemplate)))
                .isEqualTo("Cum integrăm un coleg nou");
    }

    @Test
    void aManagerMayEditTheTemplateTheyAuthoredThemselves() throws Exception {
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        JsonNode his = authorOnboarding(ionut, "Recepție marfă");

        ResponseEntity<String> edited = ionut.exchange(
                HttpMethod.PATCH,
                "/api/process-templates/" + templateId(his),
                "{\"overview\":\"varianta mea\",\"steps\":[" + keeping(his, 0) + "]}");

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theOwnerMayEditATemplateAManagerAuthored() throws Exception {
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        JsonNode his = authorOnboarding(ionut, "Recepție marfă");

        ResponseEntity<String> edited = browser.exchange(
                HttpMethod.PATCH,
                "/api/process-templates/" + templateId(his),
                "{\"overview\":\"revizuit de proprietar\",\"steps\":[" + keeping(his, 0) + "]}");

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(edited.getBody()).get("overview").asText()).isEqualTo("revizuit de proprietar");
    }

    @Test
    void listsTheTemplatesSomebodyMayInstantiate() throws Exception {
        authorOnboarding(browser, "Integrare angajat nou");
        authorOnboarding(browser, "Închidere lunară");

        ResponseEntity<String> listed = browser.get("/api/process-templates");

        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(listed.getBody()).get("templates")).hasSize(2);
    }

    @Test
    void readsOneTemplateWithItsGraph() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");
        String id = templateId(template);
        browser.post("/api/process-templates/" + id + "/dependencies", edge(stepId(template, 1), stepId(template, 0)));

        ResponseEntity<String> read = browser.get("/api/process-templates/" + id);

        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(read.getBody()).get("dependencies")).hasSize(1);
    }

    @Test
    void refusesAnEmployeeReadingTheTemplateLibrary() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        assertThat(andrei.get("/api/process-templates").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void refusesACallerWithNoSession() {
        RoundTripClient stranger = new RoundTripClient(rest);

        assertThat(stranger.get("/api/process-templates").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reorderingATemplateRewritesItsPositionsAndLeavesARunningInstanceAlone() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare — reordonare");
        String id = templateId(template);
        ResponseEntity<String> started = browser.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Rulare\",\"processOwnerId\":\"%s\"}".formatted(id, company.maria()));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String run = json.readTree(started.getBody()).get("id").asText();
        List<String> runOrderBefore = instanceTitlesInOrder(run);

        ResponseEntity<String> reordered = browser.exchange(
                HttpMethod.PATCH,
                "/api/process-templates/" + id,
                "{\"overview\":\"Cum integrăm un coleg nou\",\"steps\":[" + keeping(template, 2) + ","
                        + keeping(template, 1) + "," + keeping(template, 0) + "]}");

        assertThat(reordered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(templateTitlesInOrder(id))
                .containsExactly("Evaluare la o lună", "Prima zi", "Pregătește echipamentul");
        assertThat(instanceTitlesInOrder(run)).containsExactlyElementsOf(runOrderBefore);
    }

    private List<String> templateTitlesInOrder(String templateId) {
        return jdbc.queryForList(
                """
                select w.title
                from step_definition s
                join task_template w on w.id = s.task_template_id
                where s.template_id = ?::uuid
                order by s.position
                """,
                String.class,
                java.util.UUID.fromString(templateId));
    }

    private List<String> instanceTitlesInOrder(String instanceId) {
        return jdbc.queryForList(
                "select title from instance_step where instance_id = ?::uuid order by position",
                String.class,
                java.util.UUID.fromString(instanceId));
    }

    private String keeping(JsonNode template, int position) {
        JsonNode step = template.get("steps").get(position);
        return "{\"id\":\"%s\",\"taskTemplateId\":\"%s\",\"expectedDurationHours\":null}"
                .formatted(step.get("id").asText(), step.get("taskTemplateId").asText());
    }

    private void registerNothingMore() {
        assertThat(company.maria()).isNotNull();
    }
}
