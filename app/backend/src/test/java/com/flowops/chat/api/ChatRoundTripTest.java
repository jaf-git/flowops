package com.flowops.chat.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("CHAT-SEND-MESSAGE-01")
class ChatRoundTripTest extends CompanyScenarioTest {
    private String startConversationWith(RoundTripClient who, UUID person) throws Exception {
        ResponseEntity<String> started = who.post("/api/conversations", "{\"personId\":\"%s\"}".formatted(person));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("id").asText();
    }

    @Test
    void everybodyReachesTheirOwnRail() throws Exception {
        buildTheCompany();

        assertThat(signedInBrowser("andrei@atelier.ro")
                        .get("/api/conversations")
                        .getStatusCode())
                .as("CHAT_PARTICIPATE is granted to every role, so an employee reaches this")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void anEmployeeMayMessageSomebodyOutsideTheirOwnBranch() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        assertThat(startConversationWith(andrei, company.elena())).isNotBlank();
    }

    @Test
    void thereIsOnlyEverOneConversationBetweenTwoPeople() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");

        String first = startConversationWith(andrei, company.elena());
        String again = startConversationWith(andrei, company.elena());
        String fromTheOtherSide = startConversationWith(elena, company.andrei());

        assertThat(again).isEqualTo(first);
        assertThat(fromTheOtherSide)
                .as("the pair is normalised, so whoever opens it finds the same row")
                .isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from conversation where kind = 'DIRECT'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void aMessageReachesTheOtherParticipantsThread() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        String conversation = startConversationWith(andrei, company.elena());

        ResponseEntity<String> sent = andrei.post(
                "/api/conversations/" + conversation + "/messages",
                "{\"body\":\"Trebuie să sunăm furnizorul până joi\"}");
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode thread = json.readTree(
                elena.get("/api/conversations/" + conversation + "/messages").getBody());
        assertThat(thread.get("messages").get(0).get("body").asText())
                .isEqualTo("Trebuie să sunăm furnizorul până joi");
        assertThat(thread.get("messages").get(0).get("seq").asLong())
                .as("the sequence is the stream cursor and comes from the trigger, never from the application")
                .isGreaterThan(0L);
    }

    @Test
    void aConversationYouAreNotInIsIndistinguishableFromOneThatDoesNotExist() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String theirs = startConversationWith(andrei, company.elena());

        ResponseEntity<String> notMine = ionut.get("/api/conversations/" + theirs + "/messages");
        ResponseEntity<String> doesNotExist = ionut.get("/api/conversations/" + UUID.randomUUID() + "/messages");

        assertThat(notMine.getStatusCode()).isEqualTo(doesNotExist.getStatusCode());
        assertThat(notMine.getBody())
                .as("same status, same code, same message — or the difference is the enumeration")
                .isEqualTo(doesNotExist.getBody());
        assertThat(notMine.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void noRoleReadsAConversationItIsNotIn() throws Exception {
        Company company = buildTheCompany();
        String theirs = startConversationWith(signedInBrowser("andrei@atelier.ro"), company.elena());

        assertThat(signedInBrowser("ioana@atelier.ro")
                        .get("/api/conversations/" + theirs + "/messages")
                        .getStatusCode())
                .as("Ioana is Andrei's manager and that grants her nothing here")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(browser.get("/api/conversations/" + theirs + "/messages").getStatusCode())
                .as("nor the owner — DECISION-CHAT-PRIVACY-01 refuses an administrative read outright")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aBlankMessageIsRefusedAndNothingIsStored() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = startConversationWith(andrei, company.elena());

        ResponseEntity<String> refused =
                andrei.post("/api/conversations/" + conversation + "/messages", "{\"body\":\"   \"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("MESSAGE_EMPTY");
        assertThat(jdbc.queryForObject("select count(*) from message", Long.class))
                .isZero();
    }

    @Test
    void anOverlongMessageIsRefusedAndNamesTheLimit() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = startConversationWith(andrei, company.elena());

        ResponseEntity<String> refused = andrei.post(
                "/api/conversations/" + conversation + "/messages", "{\"body\":\"%s\"}".formatted("a".repeat(4001)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode error = json.readTree(refused.getBody());
        assertThat(error.get("code").asText()).isEqualTo("MESSAGE_TOO_LONG");
        assertThat(error.get("details").get(0).get("rule").asText()).isEqualTo("4000");
    }

    @Test
    void theEventCarriesNoMessageBody() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = startConversationWith(andrei, company.elena());
        andrei.post("/api/conversations/" + conversation + "/messages", "{\"body\":\"ceva confidențial\"}");

        assertThat(jdbc.queryForObject("select count(*) from chat_event where action = 'MESSAGE_SENT'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForList(
                        "select column_name from information_schema.columns where table_name = 'chat_event'",
                        String.class))
                .as("no column on this table can hold what was said")
                .doesNotContain("body");
    }

    @Test
    void theReadMarkerMovesForwardOnly() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        String conversation = startConversationWith(andrei, company.elena());

        String first = json.readTree(
                        andrei.post("/api/conversations/" + conversation + "/messages", "{\"body\":\"prima\"}")
                                .getBody())
                .get("id")
                .asText();
        String second = json.readTree(
                        andrei.post("/api/conversations/" + conversation + "/messages", "{\"body\":\"a doua\"}")
                                .getBody())
                .get("id")
                .asText();

        elena.post("/api/conversations/" + conversation + "/read", "{\"throughMessageId\":\"%s\"}".formatted(second));
        elena.post("/api/conversations/" + conversation + "/read", "{\"throughMessageId\":\"%s\"}".formatted(first));

        JsonNode rail = json.readTree(elena.get("/api/conversations").getBody());
        assertThat(rail.get("conversations").get(0).get("unreadCount").asLong())
                .as("the marker stayed at the newer message, so nothing became unread again")
                .isZero();
    }

    private static java.time.Instant tomorrow() {
        return java.time.Instant.now().plus(java.time.Duration.ofDays(1));
    }

    private String sayInAConversationWith(RoundTripClient who, UUID counterpart, String body) throws Exception {
        String conversation = startConversationWith(who, counterpart);
        String message = json.readTree(who.post(
                                "/api/conversations/" + conversation + "/messages", "{\"body\":\"%s\"}".formatted(body))
                        .getBody())
                .get("id")
                .asText();
        return conversation + "/messages/" + message;
    }

    @Test
    void aMessageBecomesATaskCarryingWhatWasSaid() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String path = sayInAConversationWith(andrei, company.elena(), "Trebuie sa sunam furnizorul");

        ResponseEntity<String> converted = andrei.post(
                "/api/conversations/" + path + "/convert",
                """
                {"title":"Trebuie sa sunam furnizorul","description":"Trebuie sa sunam furnizorul",
                 "assigneeId":"%s","deadline":"%s","priority":"NORMAL"}"""
                        .formatted(company.andrei(), tomorrow()));

        assertThat(converted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String task = json.readTree(converted.getBody()).get("taskId").asText();

        assertThat(jdbc.queryForObject("select title from task where id = ?::uuid", String.class, task))
                .isEqualTo("Trebuie sa sunam furnizorul");
        assertThat(jdbc.queryForObject("select process_instance_id from task where id = ?::uuid", String.class, task))
                .as("an ad-hoc task belongs to no run — DECISION-ADHOC-QUEUE-01 is satisfied by building nothing")
                .isNull();
        assertThat(jdbc.queryForObject("select converted_task_id from message", String.class))
                .isEqualTo(task);
    }

    @Test
    void aMessageBecomesAtMostOneTask() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String path = sayInAConversationWith(andrei, company.elena(), "Ceva de facut");
        String body =
                """
                {"title":"Ceva de facut","description":"Ceva de facut","assigneeId":"%s",
                 "deadline":"%s","priority":"NORMAL"}"""
                        .formatted(company.andrei(), tomorrow());

        assertThat(andrei.post("/api/conversations/" + path + "/convert", body).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> again = andrei.post("/api/conversations/" + path + "/convert", body);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(again.getBody()).get("code").asText()).isEqualTo("MESSAGE_ALREADY_CONVERTED");
        assertThat(jdbc.queryForObject("select count(*) from task", Long.class)).isEqualTo(1L);
    }

    @Test
    void tasksRefusalPassesThroughWithItsOwnCodeAndNothingIsWritten() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String path = sayInAConversationWith(andrei, company.elena(), "Ceva");

        ResponseEntity<String> refused = andrei.post(
                "/api/conversations/" + path + "/convert",
                """
                {"title":"Ceva","description":"Ceva","assigneeId":"%s","deadline":"%s","priority":"NORMAL"}"""
                        .formatted(company.elena(), tomorrow()));

        assertThat(json.readTree(refused.getBody()).get("code").asText())
                .as("Elena reports to Maria; an employee may not direct her, and TASK is what says so")
                .isEqualTo("ASSIGNEE_OUT_OF_SCOPE");
        assertThat(jdbc.queryForObject("select count(*) from task", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select converted_task_id from message", String.class))
                .as("extension 7a: a half-conversion cannot exist, so the message is still convertible")
                .isNull();
    }

    @Test
    void aMessageConvertsWithoutADeadline() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String path = sayInAConversationWith(andrei, company.elena(), "Ceva fara termen");

        ResponseEntity<String> converted = andrei.post(
                "/api/conversations/" + path + "/convert",
                """
                {"title":"Ceva fara termen","description":"Ceva fara termen",
                 "assigneeId":"%s","priority":"NORMAL"}"""
                        .formatted(company.andrei()));

        assertThat(converted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("select deadline from task", java.sql.Timestamp.class))
                .as("absent stays absent — the assignee sets it after accepting")
                .isNull();
    }

    @Test
    void aManagerWhoCannotSeeTheConversationOpensAnOrdinaryTask() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String path = sayInAConversationWith(andrei, company.elena(), "Trebuie sa sunam furnizorul");
        String task = json.readTree(andrei.post(
                                "/api/conversations/" + path + "/convert",
                                """
                                {"title":"Trebuie sa sunam furnizorul","description":"Trebuie sa sunam furnizorul",
                                 "assigneeId":"%s","deadline":"%s","priority":"NORMAL"}"""
                                        .formatted(company.andrei(), tomorrow()))
                        .getBody())
                .get("taskId")
                .asText();

        ResponseEntity<String> seenByTheManager =
                signedInBrowser("ioana@atelier.ro").get("/api/tasks/" + task);

        assertThat(seenByTheManager.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seenByTheManager.getBody())
                .as("the text is there in full, because it was copied rather than pointed at")
                .contains("Trebuie sa sunam furnizorul");
        assertThat(seenByTheManager.getBody().toLowerCase())
                .as("and nothing on the task mentions a conversation or a message")
                .doesNotContain("conversation")
                .doesNotContain("messageid");
    }

    @Test
    void somebodyWhoSteersNoRunIsOfferedNoProcessAtAll() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String path = sayInAConversationWith(andrei, company.elena(), "Trebuie sa sunam furnizorul");

        JsonNode context = json.readTree(
                andrei.get("/api/conversations/" + path + "/conversion-context").getBody());

        assertThat(context.get("instances")).isNotNull();
        assertThat(context.get("instances").size())
                .as("an employee steers nothing, so the ad-hoc path is the only one")
                .isZero();
    }

    @Test
    void aMessageBecomesATaskInsideARunTheActorSteers() throws Exception {
        Company company = buildTheCompany();
        String run = aRunSteeredByTheOwner(company);
        RoundTripClient owner = browser;
        String path = sayInAConversationWith(owner, company.andrei(), "Trebuie sa sunam furnizorul");

        JsonNode context = json.readTree(
                owner.get("/api/conversations/" + path + "/conversion-context").getBody());
        assertThat(context.get("instances").size())
                .as("the owner steers this run, so it is on the picker")
                .isGreaterThan(0);

        ResponseEntity<String> converted = owner.post(
                "/api/conversations/" + path + "/convert",
                """
                {"title":"Trebuie sa sunam furnizorul","description":"Trebuie sa sunam furnizorul",
                 "assigneeId":"%s","deadline":"%s","priority":"NORMAL","instanceId":"%s"}"""
                        .formatted(company.andrei(), tomorrow(), run));

        assertThat(converted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String task = json.readTree(converted.getBody()).get("taskId").asText();
        assertThat(jdbc.queryForObject("select process_instance_id from task where id = ?::uuid", String.class, task))
                .as("it went through PROCESS's door, so the work sits in the run rather than beside it")
                .isEqualTo(run);
        assertThat(jdbc.queryForObject("select converted_task_id from message", String.class))
                .isEqualTo(task);
    }

    @Test
    void aRefusalFromProcessLeavesTheMessageConvertible() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient owner = browser;
        String path = sayInAConversationWith(owner, company.andrei(), "Trebuie sa sunam furnizorul");

        ResponseEntity<String> refused = owner.post(
                "/api/conversations/" + path + "/convert",
                """
                {"title":"Trebuie sa sunam furnizorul","description":"Trebuie sa sunam furnizorul",
                 "assigneeId":"%s","deadline":"%s","priority":"NORMAL","instanceId":"%s"}"""
                        .formatted(company.andrei(), tomorrow(), UUID.randomUUID()));

        assertThat(refused.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(jdbc.queryForObject("select converted_task_id from message", String.class))
                .as("nothing was written, so the sentence can still become work")
                .isNull();
    }

    private String aRunSteeredByTheOwner(Company company) throws Exception {
        String template = json.readTree(browser.post(
                                "/api/process-templates",
                                """
                                {"name":"Comanda mobilier","overview":null,
                                 "steps":[{"taskTemplateId":"%s","expectedDurationHours":2}]}"""
                                        .formatted(work("Masuratori")))
                        .getBody())
                .get("id")
                .asText();

        return json.readTree(browser.post(
                                "/api/process-instances",
                                """
                                {"templateId":"%s","name":"Comanda mobilier","processOwnerId":"%s"}"""
                                        .formatted(template, company.maria()))
                        .getBody())
                .get("id")
                .asText();
    }

    @Test
    void aParticipantCanWalkFromTheTaskBackToWhatWasSaid() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String path = sayInAConversationWith(andrei, company.elena(), "Trebuie sa sunam furnizorul");
        String task = convertToATaskFor(andrei, path, company.andrei());

        ResponseEntity<String> origin = andrei.get("/api/conversations/origin/" + task);

        assertThat(origin.getStatusCode()).isEqualTo(HttpStatus.OK);
        var found = json.readTree(origin.getBody());
        assertThat(path)
                .as("the answer names the very message the task was made from")
                .isEqualTo(found.get("conversationId").asText() + "/messages/"
                        + found.get("messageId").asText());
    }

    @Test
    void theOtherParticipantCanWalkItToo() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String path = sayInAConversationWith(andrei, company.elena(), "Trebuie sa sunam furnizorul");
        String task = convertToATaskFor(andrei, path, company.andrei());

        assertThat(signedInBrowser("elena@atelier.ro")
                        .get("/api/conversations/origin/" + task)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void aManagerCannotTellAConversationSheIsNotInFromNoConversationAtAll() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String path = sayInAConversationWith(andrei, company.elena(), "Trebuie sa sunam furnizorul");
        String fromAMessage = convertToATaskFor(andrei, path, company.andrei());

        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        ResponseEntity<String> hidden = ioana.get("/api/conversations/origin/" + fromAMessage);
        ResponseEntity<String> neverInAConversation = ioana.get("/api/conversations/origin/" + UUID.randomUUID());

        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden.getStatusCode()).isEqualTo(neverInAConversation.getStatusCode());
        assertThat(hidden.getBody())
                .as("same body, byte for byte — anything else is a side channel")
                .isEqualTo(neverInAConversation.getBody());
    }

    private String convertToATaskFor(RoundTripClient who, String path, UUID assignee) throws Exception {
        return json.readTree(who.post(
                                "/api/conversations/" + path + "/convert",
                                """
                                {"title":"Trebuie sa sunam furnizorul","description":"Trebuie sa sunam furnizorul",
                                 "assigneeId":"%s","deadline":"%s","priority":"NORMAL"}"""
                                        .formatted(assignee, tomorrow()))
                        .getBody())
                .get("taskId")
                .asText();
    }

    @Test
    void bringingSomebodyIntoAGroupPutsThemOnTheRosterAndSaysSoInTheRoom() throws Exception {
        Company company = buildTheCompany();
        String room = json.readTree(browser.post("/api/conversations/group", "{\"name\":\"Campanie Aurora\"}")
                        .getBody())
                .get("id")
                .asText();

        ResponseEntity<String> added = browser.post(
                "/api/conversations/" + room + "/participants", "{\"personId\":\"%s\"}".formatted(company.andrei()));

        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode roster = json.readTree(
                browser.get("/api/conversations/" + room + "/participants").getBody());
        assertThat(roster.toString())
                .as("the people you can name for work you are discussing are the people in the room")
                .contains(company.andrei().toString());

        assertThat(jdbc.queryForObject(
                        "select count(*) from message where conversation_id = ?::uuid and body like '%added%'",
                        Long.class, room))
                .as("an addition is never silent")
                .isEqualTo(1L);

        assertThat(signedInBrowser("andrei@atelier.ro")
                        .get("/api/conversations/" + room + "/messages")
                        .getStatusCode())
                .as("he is a participant now, so the thread is his to read")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void addingSomebodyTwiceSaysOneThingOnceAndTellsTheRoomOnce() throws Exception {
        Company company = buildTheCompany();
        String room = json.readTree(browser.post("/api/conversations/group", "{\"name\":\"Campanie Aurora\"}")
                        .getBody())
                .get("id")
                .asText();
        String body = "{\"personId\":\"%s\"}".formatted(company.andrei());
        assertThat(browser.post("/api/conversations/" + room + "/participants", body)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(browser.post("/api/conversations/" + room + "/participants", body)
                        .getStatusCode())
                .as("a refusal on the second press would teach somebody the control is unreliable")
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject(
                        "select count(*) from conversation_participant where conversation_id = ?::uuid",
                        Long.class,
                        room))
                .as("Maria and Andrei, and Andrei once")
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from message where conversation_id = ?::uuid and body like '%added%'",
                        Long.class, room))
                .as("one addition, one line")
                .isEqualTo(1L);
    }

    @Test
    void aDirectTakesNoThirdPersonAndARoomYouAreNotInIsIndistinguishableFromNone() throws Exception {
        Company company = buildTheCompany();
        String direct = startConversationWith(browser, company.andrei());

        ResponseEntity<String> refused = browser.post(
                "/api/conversations/" + direct + "/participants", "{\"personId\":\"%s\"}".formatted(company.elena()));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        ResponseEntity<String> hidden = ioana.get("/api/conversations/" + direct + "/participants");
        ResponseEntity<String> neverExisted = ioana.get("/api/conversations/" + UUID.randomUUID() + "/participants");

        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden.getBody())
                .as("same body, byte for byte — anything else is a side channel")
                .isEqualTo(neverExisted.getBody());
    }

    @Test
    void noRouteReturnsAnAggregateAboutAnybody() throws Exception {
        buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        assertThat(andrei.get("/api/presence").getStatusCode())
                .as("presence is derived and never stored; there is deliberately no endpoint listing it")
                .isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
        assertThat(json.readTree(andrei.get("/api/conversations").getBody()).toString())
                .as("no per-person count, duration or ordering appears on the rail")
                .doesNotContain("responseTime", "messageCount", "activity");
    }

    private int nobodyMayParticipateAnyMore() {
        return jdbc.update("delete from auth_role_permission where permission_name = 'CHAT_PARTICIPATE'");
    }

    private void everybodyMayParticipateAgain() {
        jdbc.update(
                """
                insert into auth_role_permission (role_name, permission_name)
                select role_name, 'CHAT_PARTICIPATE' from (values ('OWNER'), ('MANAGER'), ('EMPLOYEE')) as r(role_name)
                where not exists (
                    select 1 from auth_role_permission held
                    where held.role_name = r.role_name and held.permission_name = 'CHAT_PARTICIPATE')
                """);
    }

    @Test
    void bothRosterRoutesRefuseACallerWhoseRoleNoLongerHoldsChatParticipate() throws Exception {
        Company company = buildTheCompany();
        String room = json.readTree(browser.post("/api/conversations/group", "{\"name\":\"Campanie Aurora\"}")
                        .getBody())
                .get("id")
                .asText();
        long onTheRosterBefore = jdbc.queryForObject(
                "select count(*) from conversation_participant where conversation_id = ?::uuid", Long.class, room);
        assertThat(onTheRosterBefore)
                .as("Maria is in her own room, so an unchanged roster below is evidence rather than a vacuum")
                .isEqualTo(1L);

        assertThat(nobodyMayParticipateAnyMore())
                .as("V37's three grants must actually have gone, or a 403 below would mean something else")
                .isEqualTo(3);
        try {
            ResponseEntity<String> read = browser.get("/api/conversations/" + room + "/participants");
            ResponseEntity<String> written = browser.post(
                    "/api/conversations/" + room + "/participants",
                    "{\"personId\":\"%s\"}".formatted(company.andrei()));

            assertThat(read.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(written.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(json.readTree(read.getBody()).get("code").asText())
                    .as("a refusal that reads as a fault teaches the person to retry something that cannot work")
                    .isEqualTo("NOT_PERMITTED");
            assertThat(json.readTree(written.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");

            assertThat(jdbc.queryForObject(
                            "select count(*) from conversation_participant where conversation_id = ?::uuid",
                            Long.class,
                            room))
                    .as("a request that never passed the door added nobody")
                    .isEqualTo(onTheRosterBefore);
            assertThat(jdbc.queryForObject(
                            "select count(*) from message where conversation_id = ?::uuid", Long.class, room))
                    .as("and told the room nothing")
                    .isZero();
        } finally {
            everybodyMayParticipateAgain();
        }
    }

    @Test
    void anAdditionThatNamesNobodyIsRefusedAndTheRoomIsToldNothing() throws Exception {
        buildTheCompany();
        String room = json.readTree(browser.post("/api/conversations/group", "{\"name\":\"Campanie Aurora\"}")
                        .getBody())
                .get("id")
                .asText();
        long onTheRosterBefore = jdbc.queryForObject(
                "select count(*) from conversation_participant where conversation_id = ?::uuid", Long.class, room);
        assertThat(onTheRosterBefore).isEqualTo(1L);

        ResponseEntity<String> namesNobody = browser.post("/api/conversations/" + room + "/participants", "{}");
        ResponseEntity<String> namesSomethingElse =
                browser.post("/api/conversations/" + room + "/participants", "{\"personId\":\"Andrei Munteanu\"}");

        assertThat(namesNobody.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(namesNobody.getBody()).get("code").asText()).isEqualTo("REQUEST_INVALID");
        assertThat(json.readTree(namesNobody.getBody()).get("details").toString())
                .as("field by field, so a form can mark exactly what to fix")
                .contains("personId");

        assertThat(namesSomethingElse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(namesSomethingElse.getBody()).get("code").asText())
                .as("400 rather than 500 - a body the caller sent is not the server breaking")
                .isEqualTo("REQUEST_MALFORMED");

        assertThat(jdbc.queryForObject(
                        "select count(*) from conversation_participant where conversation_id = ?::uuid",
                        Long.class,
                        room))
                .isEqualTo(onTheRosterBefore);
        assertThat(jdbc.queryForObject(
                        "select count(*) from message where conversation_id = ?::uuid", Long.class, room))
                .as("an addition that did not happen is not announced")
                .isZero();
    }
}
