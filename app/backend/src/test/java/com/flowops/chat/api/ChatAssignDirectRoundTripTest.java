package com.flowops.chat.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("CHAT-ASSIGN-DIRECT-01")
class ChatAssignDirectRoundTripTest extends CompanyScenarioTest {
    private static java.time.Instant tomorrow() {
        return java.time.Instant.now().plus(java.time.Duration.ofDays(1));
    }

    private String conversationBetween(RoundTripClient who, UUID person) throws Exception {
        ResponseEntity<String> started = who.post("/api/conversations", "{\"personId\":\"%s\"}".formatted(person));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("id").asText();
    }

    private String aTemplate() throws Exception {
        return json.readTree(browser.post(
                                "/api/process-templates",
                                """
                                {"name":"Comanda mobilier","overview":"De la masuratori la livrare",
                                 "steps":[{"taskTemplateId":"%s","expectedDurationHours":2},
                                          {"taskTemplateId":"%s","expectedDurationHours":3}]}"""
                                        .formatted(work("Masuratori"), work("Oferta")))
                        .getBody())
                .get("id")
                .asText();
    }

    private JsonNode threadOf(RoundTripClient who, String conversation) throws Exception {
        return json.readTree(who.get("/api/conversations/" + conversation + "/messages")
                        .getBody())
                .get("messages");
    }

    @Test
    void givingSomebodyATaskLeavesAMarkBothOfThemCanSee() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());

        ResponseEntity<String> created = ioana.post(
                "/api/conversations/" + conversation + "/tasks",
                """
                {"title":"Suna furnizorul de gresie","description":"Comanda din Bucuresti a intarziat",
                 "deadline":"%s","priority":"NORMAL"}"""
                        .formatted(tomorrow()));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String task = json.readTree(created.getBody()).get("taskId").asText();

        assertThat(jdbc.queryForObject("select assignee_user_id from task where id = ?::uuid", String.class, task))
                .as("the assignee is the conversation's counterpart, resolved server-side")
                .isEqualTo(company.andrei().toString());

        JsonNode hisThread = threadOf(andrei, conversation);
        assertThat(hisThread).hasSize(1);
        assertThat(hisThread.get(0).get("kind").asText()).isEqualTo("WORK_MARK");
        assertThat(hisThread.get(0).get("work").get("kind").asText()).isEqualTo("TASK");
        assertThat(hisThread.get(0).get("work").get("id").asText()).isEqualTo(task);
        assertThat(hisThread.get(0).get("authorId").asText())
                .as("the actor is who gave the work, never who received it")
                .isEqualTo(company.ioana().toString());
    }

    @Test
    void aMarkStoresNoSentenceInAnyLanguage() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());

        ioana.post(
                "/api/conversations/" + conversation + "/tasks",
                """
                {"title":"Suna furnizorul","description":null,"deadline":"%s","priority":"NORMAL"}"""
                        .formatted(tomorrow()));

        assertThat(jdbc.queryForObject("select body from message where kind = 'WORK_MARK'", String.class))
                .as("the sentence is assembled by each client, in its own language")
                .isNull();
        assertThat(jdbc.queryForObject("select count(*) from chat_event where action = 'WORK_ASSIGNED'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void aRefusedTaskLeavesNoMarkBehind() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());

        ResponseEntity<String> refused = ioana.post(
                "/api/conversations/" + conversation + "/tasks",
                """
                {"title":"Prea tarziu","description":null,"deadline":"2020-01-01T09:00:00Z","priority":"NORMAL"}""");

        assertThat(refused.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(json.readTree(refused.getBody()).get("code").asText())
                .as("TASK's own code, not re-worded by CHAT")
                .isEqualTo("DEADLINE_IN_THE_PAST");
        assertThat(jdbc.queryForObject("select count(*) from message", Long.class))
                .as("no mark, and no task")
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from task", Long.class)).isZero();
    }

    @Test
    void somebodyOutsideMySubtreeCannotBeGivenATaskEvenByCallingDirectly() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(andrei, company.elena());

        ResponseEntity<String> refused = andrei.post(
                "/api/conversations/" + conversation + "/tasks",
                """
                {"title":"Ceva","description":null,"deadline":"%s","priority":"NORMAL"}"""
                        .formatted(tomorrow()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText())
                .as("TASK's own code, reaching the caller unaltered")
                .isEqualTo("ASSIGNEE_OUT_OF_SCOPE");
        assertThat(jdbc.queryForObject("select count(*) from message", Long.class))
                .isZero();
    }

    @Test
    void anAssigneeInTheBodyIsNotAWayToReachSomebodyElse() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());

        ResponseEntity<String> created = ioana.post(
                "/api/conversations/" + conversation + "/tasks",
                """
                {"title":"Suna furnizorul","description":null,"deadline":"%s","priority":"NORMAL",
                 "assigneeId":"%s"}"""
                        .formatted(tomorrow(), company.ioana()));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String task = json.readTree(created.getBody()).get("taskId").asText();
        assertThat(jdbc.queryForObject("select assignee_user_id from task where id = ?::uuid", String.class, task))
                .as("the counterpart, not the identifier somebody put in the body")
                .isEqualTo(company.andrei().toString());
    }

    @Test
    void twoSubmitsMakeTwoTasksOnPurpose() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());
        String body =
                """
                {"title":"Suna furnizorul","description":null,"deadline":"%s","priority":"NORMAL"}"""
                        .formatted(tomorrow());

        ioana.post("/api/conversations/" + conversation + "/tasks", body);
        ioana.post("/api/conversations/" + conversation + "/tasks", body);

        assertThat(jdbc.queryForObject("select count(*) from task", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from message where kind = 'WORK_MARK'", Long.class))
                .isEqualTo(2L);
    }

    @Test
    void startingARunNamesTheCounterpartToSteerItAndAssignsNobody() throws Exception {
        Company company = buildTheCompany();
        String template = aTemplate();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());

        ResponseEntity<String> created = ioana.post(
                "/api/conversations/" + conversation + "/runs", "{\"templateId\":\"%s\"}".formatted(template));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String run = json.readTree(created.getBody()).get("instanceId").asText();

        assertThat(jdbc.queryForObject(
                        "select process_owner_user_id from process_instance where id = ?::uuid", String.class, run))
                .as("the counterpart steers it")
                .isEqualTo(company.andrei().toString());
        assertThat(jdbc.queryForObject(
                        "select count(*) from instance_step where instance_id = ?::uuid and task_id is not null",
                        Long.class,
                        run))
                .as("steering a run is not being given its work — steps are assigned as they become reachable")
                .isZero();
        assertThat(jdbc.queryForObject("select name from process_instance where id = ?::uuid", String.class, run))
                .as("the template's own name, because a conversation has none to lend")
                .isEqualTo("Comanda mobilier");
    }

    @Test
    void aRunLeavesAMarkOfItsOwnKind() throws Exception {
        Company company = buildTheCompany();
        String template = aTemplate();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());

        String run = json.readTree(ioana.post(
                                "/api/conversations/" + conversation + "/runs",
                                "{\"templateId\":\"%s\"}".formatted(template))
                        .getBody())
                .get("instanceId")
                .asText();

        JsonNode thread = threadOf(ioana, conversation);
        assertThat(thread.get(0).get("work").get("kind").asText()).isEqualTo("RUN");
        assertThat(thread.get(0).get("work").get("id").asText()).isEqualTo(run);
        assertThat(thread.get(0).hasNonNull("body")).isFalse();
    }

    @Test
    void aManagerMayHandARunToSomebodyTheyMayNotHandATask() throws Exception {
        Company company = buildTheCompany();
        aTemplate();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String conversation = conversationBetween(ioana, company.elena());

        JsonNode context = json.readTree(ioana.get("/api/conversations/" + conversation + "/assignment-context")
                .getBody());

        assertThat(context.get("counterpartId").asText())
                .isEqualTo(company.elena().toString());
        assertThat(context.get("mayAssignTask").asBoolean())
                .as("Elena is outside Ioana's subtree, so TASK says no")
                .isFalse();
        assertThat(context.get("mayStartRun").asBoolean())
                .as("steering is not subtree-scoped, so PROCESS says yes")
                .isTrue();
        assertThat(context.get("templates"))
                .as("the other half of the asymmetry: there is something to hand her")
                .isNotEmpty();

        ResponseEntity<String> started = ioana.post(
                "/api/conversations/" + conversation + "/runs",
                "{\"templateId\":\"%s\"}"
                        .formatted(json.readTree(
                                        ioana.get("/api/process-templates").getBody())
                                .get("templates")
                                .get(0)
                                .get("id")
                                .asText()));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void somebodyWhoMayNotStartRunsIsToldThatRatherThanShownAnEmptyLibrary() throws Exception {
        Company company = buildTheCompany();
        aTemplate();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(andrei, company.elena());

        JsonNode context = json.readTree(andrei.get("/api/conversations/" + conversation + "/assignment-context")
                .getBody());

        assertThat(context.get("mayStartRun").asBoolean())
                .as("an employee may not start runs, and the answer says which question it is answering")
                .isFalse();
        assertThat(context.get("templates")).isEmpty();

        ResponseEntity<String> refused = andrei.post(
                "/api/conversations/" + conversation + "/runs", "{\"templateId\":\"%s\"}".formatted(UUID.randomUUID()));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
    }

    private String aTeamChannelSteeredBy(UUID manager) {
        UUID conversation = UUID.randomUUID();
        UUID workspace = jdbc.queryForObject("select id from workspace limit 1", UUID.class);
        jdbc.update(
                """
                insert into conversation (id, workspace_id, kind, created_at, participant_lo, participant_hi,
                                          team_manager_id)
                values (?, ?, 'CHANNEL', now(), null, null, ?)""",
                conversation,
                workspace,
                manager);
        jdbc.update(
                "insert into conversation_participant (conversation_id, person_id, last_read_message_id)"
                        + " values (?, ?, null)",
                conversation,
                manager);
        return conversation.toString();
    }

    @Test
    void aChannelHasNoOneOtherPersonAndRefusesBothPaths() throws Exception {
        Company company = buildTheCompany();
        String template = aTemplate();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String channel = aTeamChannelSteeredBy(company.ioana());

        JsonNode context = json.readTree(ioana.get("/api/conversations/" + channel + "/assignment-context")
                .getBody());
        assertThat(context.hasNonNull("counterpartId")).isFalse();
        assertThat(context.get("mayAssignTask").asBoolean()).isFalse();
        assertThat(context.get("templates"))
                .as("a run needs somebody to steer it, and a channel names nobody")
                .isEmpty();

        ResponseEntity<String> refused = ioana.post(
                "/api/conversations/" + channel + "/tasks",
                """
                {"title":"Ceva","description":null,"deadline":"%s","priority":"NORMAL"}"""
                        .formatted(tomorrow()));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("CONVERSATION_NOT_DIRECT");

        ResponseEntity<String> run =
                ioana.post("/api/conversations/" + channel + "/runs", "{\"templateId\":\"%s\"}".formatted(template));
        assertThat(run.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(run.getBody()).get("code").asText()).isEqualTo("CONVERSATION_NOT_DIRECT");

        assertThat(jdbc.queryForObject("select count(*) from task", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from process_instance", Long.class))
                .isZero();
    }

    @Test
    void aConversationIAmNotInAnswersExactlyAsOneThatDoesNotExist() throws Exception {
        Company company = buildTheCompany();
        aTemplate();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        String theirs = conversationBetween(ioana, company.andrei());
        String nothing = UUID.randomUUID().toString();

        String task = """
                {"title":"Ceva","description":null,"deadline":"%s","priority":"NORMAL"}"""
                .formatted(tomorrow());
        String run = "{\"templateId\":\"%s\"}".formatted(UUID.randomUUID());

        record Pair(String what, ResponseEntity<String> notMine, ResponseEntity<String> unknown) {}
        List<Pair> probes = List.of(
                new Pair(
                        "assignment-context",
                        elena.get("/api/conversations/" + theirs + "/assignment-context"),
                        elena.get("/api/conversations/" + nothing + "/assignment-context")),
                new Pair(
                        "tasks",
                        elena.post("/api/conversations/" + theirs + "/tasks", task),
                        elena.post("/api/conversations/" + nothing + "/tasks", task)),
                new Pair(
                        "runs",
                        elena.post("/api/conversations/" + theirs + "/runs", run),
                        elena.post("/api/conversations/" + nothing + "/runs", run)));

        for (Pair probe : probes) {
            assertThat(probe.notMine().getStatusCode())
                    .as("%s: not-a-participant", probe.what())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(probe.unknown().getStatusCode())
                    .as("%s: unknown identifier", probe.what())
                    .isEqualTo(HttpStatus.NOT_FOUND);

            JsonNode mine = json.readTree(probe.notMine().getBody());
            JsonNode absent = json.readTree(probe.unknown().getBody());
            assertThat(mine.get("code").asText()).isEqualTo("CONVERSATION_NOT_FOUND");
            assertThat(absent.get("code").asText()).isEqualTo("CONVERSATION_NOT_FOUND");

            assertThat(mine.get("message").asText())
                    .as("%s: the same words, so nothing distinguishes them", probe.what())
                    .isEqualTo(absent.get("message").asText());
        }

        assertThat(jdbc.queryForObject("select count(*) from task", Long.class)).isZero();
    }

    @Test
    void aTemplateRetiredBeforeSubmitRefusesWithProcessesOwnWordsRatherThanAServerFault() throws Exception {
        Company company = buildTheCompany();
        String template = aTemplate();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());

        assertThat(browser.post("/api/process-templates/" + template + "/retirement", "{}")
                        .getStatusCode()
                        .is2xxSuccessful())
                .isTrue();

        ResponseEntity<String> refused = ioana.post(
                "/api/conversations/" + conversation + "/runs", "{\"templateId\":\"%s\"}".formatted(template));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText())
                .as("PROCESS's own token, chosen by PROCESS and passed through unaltered")
                .isEqualTo("TEMPLATE_IS_RETIRED");
        assertThat(jdbc.queryForObject("select count(*) from process_instance", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from message", Long.class))
                .as("and no mark, because the downstream call never succeeded")
                .isZero();
    }

    @Test
    void anUnknownTemplateRefusesAsNotFoundRatherThanAsAServerFault() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());

        ResponseEntity<String> refused = ioana.post(
                "/api/conversations/" + conversation + "/runs", "{\"templateId\":\"%s\"}".formatted(UUID.randomUUID()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TEMPLATE_NOT_FOUND");
    }

    @Test
    void anEmptyLibraryIsAnOrdinaryAnswer() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());

        JsonNode context = json.readTree(ioana.get("/api/conversations/" + conversation + "/assignment-context")
                .getBody());

        assertThat(context.get("templates")).isEmpty();
        assertThat(context.get("counterpartId").asText())
                .as("the counterpart is still resolved, so the header still knows who this is")
                .isEqualTo(company.andrei().toString());
    }

    @Test
    void aMarkCannotBecomeATask() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());
        ioana.post(
                "/api/conversations/" + conversation + "/tasks",
                """
                {"title":"Suna furnizorul","description":null,"deadline":"%s","priority":"NORMAL"}"""
                        .formatted(tomorrow()));
        String mark = jdbc.queryForObject("select id from message where kind = 'WORK_MARK'", String.class);

        ResponseEntity<String> context =
                ioana.get("/api/conversations/" + conversation + "/messages/" + mark + "/conversion-context");
        ResponseEntity<String> converted = ioana.post(
                "/api/conversations/" + conversation + "/messages/" + mark + "/convert",
                """
                {"title":"X","description":null,"assigneeId":"%s","deadline":"%s","priority":"NORMAL"}"""
                        .formatted(company.andrei(), tomorrow()));

        assertThat(context.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(context.getBody()).get("code").asText()).isEqualTo("CONVERSATION_NOT_FOUND");
        assertThat(converted.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(converted.getBody()).get("code").asText()).isEqualTo("CONVERSATION_NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from task", Long.class))
                .as("the one task the mark points at, and nothing conversion added")
                .isEqualTo(1L);
    }

    @Test
    void theRailPreviewsTheLastThingSaidAndStillCountsTheMark() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(ioana, company.andrei());

        ioana.post("/api/conversations/" + conversation + "/messages", "{\"body\":\"Poti sa suni la furnizor?\"}");
        ioana.post(
                "/api/conversations/" + conversation + "/tasks",
                """
                {"title":"Suna furnizorul","description":null,"deadline":"%s","priority":"NORMAL"}"""
                        .formatted(tomorrow()));

        JsonNode row = json.readTree(andrei.get("/api/conversations").getBody())
                .get("conversations")
                .valueStream()
                .filter(each -> conversation.equals(each.get("id").asText()))
                .findFirst()
                .orElseThrow();

        assertThat(row.get("lastMessagePreview").asText())
                .as("the newest thing that was said, because a mark has no words")
                .isEqualTo("Poti sa suni la furnizor?");
        assertThat(row.get("unreadCount").asInt())
                .as("the message and the mark both, because both are things Andrei has not seen")
                .isEqualTo(2);
    }
}
