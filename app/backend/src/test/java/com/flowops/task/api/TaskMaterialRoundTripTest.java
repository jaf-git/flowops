package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASK-LINK-01")
@Tag("TASK-CHECKLIST-01")
class TaskMaterialRoundTripTest extends TaskScenarioTest {
    private static final String BRIEF = "https://drive.atelier.ro/brief-q3.pdf";

    @Test
    void theAssignerAttachesTheBriefAndTheAssigneeSeesIt() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String task = json.readTree(ionut.post("/api/tasks", body(company.andrei(), tomorrow()))
                        .getBody())
                .get("id")
                .asText();

        int transitionsBefore = rowsFor("task_state_transition", task);
        int phasesBefore = rowsFor("task_phase_timer", task);

        ResponseEntity<String> attached = send(
                ionut,
                HttpMethod.POST,
                "/api/tasks/" + task + "/links",
                Map.of("url", BRIEF, "label", "Brief for Q3", "role", "INPUT"));

        assertThat(attached.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode material = json.readTree(signedInBrowser("andrei@atelier.ro")
                .get("/api/tasks/" + task + "/material")
                .getBody());
        assertThat(material.get("links")).hasSize(1);
        assertThat(material.get("links").get(0).get("url").asText()).isEqualTo(BRIEF);
        assertThat(material.get("links").get(0).get("role").asText()).isEqualTo("INPUT");
        assertThat(material.get("links").get(0).get("displayText").asText()).isEqualTo("Brief for Q3");

        assertThat(jdbc.queryForObject("select state from task where id = ?::uuid", String.class, task))
                .as("attaching is not a transition")
                .isEqualTo("CREATED");
        assertThat(rowsFor("task_amendment", task))
                .as("an amendment carries an old value, and a link that did not exist has none")
                .isZero();
        assertThat(rowsFor("task_state_transition", task))
                .as("no state moved, so nothing recorded one moving")
                .isEqualTo(transitionsBefore);
        assertThat(rowsFor("task_phase_timer", task))
                .as("attaching neither closed the open phase nor opened another")
                .isEqualTo(phasesBefore);
    }

    private int rowsFor(String table, String task) {
        Integer count =
                jdbc.queryForObject("select count(*) from " + table + " where task_id = ?::uuid", Integer.class, task);
        return count == null ? 0 : count;
    }

    @Test
    void aJavascriptAddressIsRefusedThroughTheApiAndNothingIsStored() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        ResponseEntity<String> refused = send(
                browser,
                HttpMethod.POST,
                "/api/tasks/" + task + "/links",
                Map.of("url", "javascript:alert(document.cookie)", "role", "REFERENCE"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("LINK_SCHEME_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("select count(*) from task_link", Integer.class))
                .as("nothing is stored, so nothing can be rendered later")
                .isZero();
    }

    @Test
    void anUnlabelledLinkShowsItsHost() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        send(
                browser,
                HttpMethod.POST,
                "/api/tasks/" + task + "/links",
                Map.of("url", "https://drive.atelier.ro/q3/review.xlsx", "role", "OUTPUT"));

        JsonNode links = json.readTree(
                        browser.get("/api/tasks/" + task + "/material").getBody())
                .get("links");

        assertThat(links.get(0).get("displayText").asText()).isEqualTo("drive.atelier.ro");
    }

    @Test
    void aLinkBelongingToAnotherTaskCannotBeDetachedThroughThisOne() throws Exception {
        Company company = buildTheCompany();
        String mine = createTaskFor(company.andrei());
        String other = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String linkOnTheOther = json.readTree(send(
                                andrei,
                                HttpMethod.POST,
                                "/api/tasks/" + other + "/links",
                                Map.of("url", BRIEF, "role", "INPUT"))
                        .getBody())
                .get("id")
                .asText();

        ResponseEntity<String> refused = andrei.delete("/api/tasks/" + mine + "/links/" + linkOnTheOther);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TASK_NOT_FOUND");
        assertThat(json.readTree(andrei.get("/api/tasks/" + other + "/material").getBody())
                        .get("links"))
                .as("still attached to the task it was attached to")
                .hasSize(1);
    }

    @Test
    void materialIsNotVisibleToSomebodyWithNoPartInTheWork() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        ResponseEntity<String> refused = signedInBrowser("elena@atelier.ro").get("/api/tasks/" + task + "/material");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_THE_ASSIGNEE");
    }

    @Test
    void theAssignerMayWriteAStepButOnlyTheAssigneeMayTickIt() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String task = json.readTree(ionut.post("/api/tasks", body(company.andrei(), tomorrow()))
                        .getBody())
                .get("id")
                .asText();

        ResponseEntity<String> written = send(
                ionut, HttpMethod.POST, "/api/tasks/" + task + "/checklist", Map.of("text", "Reconcile the ledger"));
        assertThat(written.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String item = json.readTree(written.getBody()).get("id").asText();

        ResponseEntity<String> refused =
                send(ionut, HttpMethod.POST, "/api/tasks/" + task + "/checklist/" + item, Map.of("done", true));
        assertThat(refused.getStatusCode())
                .as("the person who gave the work out cannot say it was done")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_THE_ASSIGNEE");

        ResponseEntity<String> ticked = send(
                signedInBrowser("andrei@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/checklist/" + item,
                Map.of("done", true));
        assertThat(ticked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(ticked.getBody()).get("done").asBoolean()).isTrue();
        assertThat(json.readTree(ticked.getBody()).get("doneAt").isNull())
                .as("a done step says when")
                .isFalse();
    }

    @Test
    void stepsComeBackInOrderAndARemovalLeavesTheRestWhereTheyWere() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        for (String text : new String[] {"Gather the invoices", "Reconcile the ledger", "File the return"}) {
            send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/checklist", Map.of("text", text));
        }

        JsonNode before = json.readTree(
                        andrei.get("/api/tasks/" + task + "/material").getBody())
                .get("checklist");
        assertThat(before).hasSize(3);
        assertThat(before.get(0).get("text").asText()).isEqualTo("Gather the invoices");
        assertThat(before.get(2).get("text").asText()).isEqualTo("File the return");

        String middle = before.get(1).get("id").asText();
        assertThat(andrei.delete("/api/tasks/" + task + "/checklist/" + middle).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode after = json.readTree(
                        andrei.get("/api/tasks/" + task + "/material").getBody())
                .get("checklist");
        assertThat(after).hasSize(2);
        assertThat(after.get(0).get("text").asText()).isEqualTo("Gather the invoices");
        assertThat(after.get(1).get("text").asText())
                .as("the last step stays last; positions are not closed up because nothing shows them")
                .isEqualTo("File the return");
    }

    @Test
    void untickingAStepClearsWhenItWasDone() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String item = json.readTree(send(
                                andrei,
                                HttpMethod.POST,
                                "/api/tasks/" + task + "/checklist",
                                Map.of("text", "Reconcile the ledger"))
                        .getBody())
                .get("id")
                .asText();

        send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/checklist/" + item, Map.of("done", true));
        ResponseEntity<String> unticked =
                send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/checklist/" + item, Map.of("done", false));

        assertThat(json.readTree(unticked.getBody()).get("done").asBoolean()).isFalse();
        assertThat(json.readTree(unticked.getBody()).get("doneAt").isNull()).isTrue();
    }

    @Test
    void aStepBelongingToAnotherTaskCannotBeReachedThroughThisOne() throws Exception {
        Company company = buildTheCompany();
        String mine = createTaskFor(company.andrei());
        String other = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String stepOnTheOther = json.readTree(send(
                                andrei,
                                HttpMethod.POST,
                                "/api/tasks/" + other + "/checklist",
                                Map.of("text", "File the return"))
                        .getBody())
                .get("id")
                .asText();

        ResponseEntity<String> ticked = send(
                andrei, HttpMethod.POST, "/api/tasks/" + mine + "/checklist/" + stepOnTheOther, Map.of("done", true));
        assertThat(ticked.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(ticked.getBody()).get("code").asText()).isEqualTo("TASK_NOT_FOUND");

        assertThat(andrei.delete("/api/tasks/" + mine + "/checklist/" + stepOnTheOther)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode untouched = json.readTree(
                        andrei.get("/api/tasks/" + other + "/material").getBody())
                .get("checklist");
        assertThat(untouched).as("still there, and still not done").hasSize(1);
        assertThat(untouched.get(0).get("done").asBoolean()).isFalse();
    }

    @Test
    void workFinishesWithEveryStepUnticked() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/checklist", Map.of("text", "Never going to do this"));

        andrei.post("/api/tasks/" + task + "/accept", "");
        andrei.post("/api/tasks/" + task + "/start", "");
        ResponseEntity<String> completed = send(
                andrei,
                HttpMethod.POST,
                "/api/tasks/" + task + "/complete",
                Map.of("note", "Done everything that mattered."));

        assertThat(completed.getStatusCode())
                .as("an unticked step is information, not a gate")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void aClosedTaskFreezesItsLinksAndItsSteps() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        andrei.post("/api/tasks/" + task + "/accept", "");
        andrei.post("/api/tasks/" + task + "/start", "");
        send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/complete", Map.of("note", "Finished."));
        send(browser, HttpMethod.POST, "/api/tasks/" + task + "/approve", Map.of("score", 4, "comment", "Good."));
        assertThat(send(browser, HttpMethod.POST, "/api/tasks/" + task + "/close", Map.of())
                        .getStatusCode())
                .as("the fixture has to actually close it, or this test proves nothing")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> link = send(
                andrei, HttpMethod.POST, "/api/tasks/" + task + "/links", Map.of("url", BRIEF, "role", "REFERENCE"));
        assertThat(link.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(link.getBody()).get("code").asText()).isEqualTo("TASK_IS_CLOSED");

        ResponseEntity<String> step =
                send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/checklist", Map.of("text", "One more thing"));
        assertThat(step.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(step.getBody()).get("code").asText()).isEqualTo("TASK_IS_CLOSED");

        assertThat(jdbc.queryForObject("select count(*) from task_link", Integer.class))
                .as("refused means nothing was written, not that the write was hidden")
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from task_checklist_item", Integer.class))
                .isZero();
    }

    private ResponseEntity<String> send(RoundTripClient client, HttpMethod method, String path, Map<String, ?> body)
            throws Exception {
        return client.exchange(method, path, json.writeValueAsString(body));
    }
}
