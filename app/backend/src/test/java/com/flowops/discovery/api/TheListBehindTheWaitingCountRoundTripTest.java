package com.flowops.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-VIEW-MY-WAITS-01")
class TheListBehindTheWaitingCountRoundTripTest extends CompanyScenarioTest {
    private static final List<String> THE_WHOLE_ROW = List.of(
            "waitId",
            "kind",
            "onBracketId",
            "onAddress",
            "waitingBracketId",
            "waitingAddress",
            "waitingPerformerName",
            "conversationId",
            "declaredAt");

    private static final String PROJECT = "Campanie de vara";

    private static final String HER_OWN_WORDS = "astept sa termine montajul";

    private String groupCalled(RoundTripClient who, String name) throws Exception {
        ResponseEntity<String> started = who.post("/api/conversations/group", "{\"name\":\"%s\"}".formatted(name));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("id").asText();
    }

    private void joins(RoundTripClient who, String conversation) {
        assertThat(who.post("/api/conversations/" + conversation + "/join", "").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    private String said(RoundTripClient who, String conversation, String body) throws Exception {
        ResponseEntity<String> sent =
                who.post("/api/conversations/" + conversation + "/messages", "{\"body\":\"%s\"}".formatted(body));
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(sent.getBody()).get("id").asText();
    }

    private String openJob(RoundTripClient who, String messageId, String name) throws Exception {
        ResponseEntity<String> opened = who.post(
                "/api/discovery/jobs",
                """
                {"messageId":"%s","name":"%s","projectLabel":"%s"}
                """
                        .formatted(messageId, name, PROJECT));
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(opened.getBody()).get("jobId").asText();
    }

    private String started(RoundTripClient who, String conversation, String job, UUID performer, String workType)
            throws Exception {
        String message = said(who, conversation, "hai sa incepem " + workType.toLowerCase());
        ResponseEntity<String> pressed = who.post(
                "/api/discovery/work",
                """
                {"messageId":"%s","jobId":"%s","verb":"CREATE","performerId":"%s","workType":"%s","joining":null}
                """
                        .formatted(message, job, performer, workType));
        assertThat(pressed.getStatusCode())
                .as("the fixture has to be built through the door, or it proves nothing about the rows")
                .isEqualTo(HttpStatus.CREATED);
        return json.readTree(pressed.getBody()).get("bracketId").asText();
    }

    private void delivered(RoundTripClient who, String conversation, String bracket) throws Exception {
        String message = said(who, conversation, "gata, uite rezultatul");
        ResponseEntity<String> done =
                who.post("/api/discovery/work/" + message + "/deliver", "{\"bracketId\":\"%s\"}".formatted(bracket));
        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String waits(RoundTripClient who, String bracket, String onBracket) throws Exception {
        ResponseEntity<String> declared = who.post(
                "/api/discovery/brackets/" + bracket + "/wait",
                """
                {"kind":"COLLEAGUE","onBracketId":"%s","reason":"%s","expectedBy":null}
                """
                        .formatted(onBracket, HER_OWN_WORDS));
        assertThat(declared.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(declared.getBody()).get("waitId").asText();
    }

    private JsonNode waitsFor(RoundTripClient who) throws Exception {
        ResponseEntity<String> read = who.get("/api/discovery/graph/my-waits");
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(read.getBody());
    }

    private int waitingOnYouFor(RoundTripClient who) throws Exception {
        ResponseEntity<String> read = who.get("/api/discovery/graph/my-work-counts");
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(read.getBody()).get("waitingOnYou").asInt();
    }

    private long openWaitsAimedAt(UUID person) {
        return jdbc.queryForObject(
                """
                select count(*) from work_node_wait w
                join work_bracket target on target.id = w.on_bracket_id
                where target.performer_ref = ? and w.satisfied_at is null and w.cancelled_at is null
                """,
                Long.class,
                person);
    }

    private long settledWaitsAimedAt(UUID person) {
        return jdbc.queryForObject(
                """
                select count(*) from work_node_wait w
                join work_bracket target on target.id = w.on_bracket_id
                where target.performer_ref = ? and w.satisfied_at is not null
                """,
                Long.class,
                person);
    }

    private String conversationOf(String bracket) {
        return jdbc.queryForObject(
                "select conversation_id::text from work_bracket where id = ?::uuid", String.class, bracket);
    }

    private void declaredThreeDaysEarlier(String waitId) {
        assertThat(jdbc.update(
                        "update work_node_wait set opened_at = opened_at - interval '3 days' where id = ?::uuid",
                        waitId))
                .as("the backdating must actually land, or the ordering assertion below tests insertion order")
                .isEqualTo(1);
    }

    private static List<String> fieldNamesOf(JsonNode row) {
        List<String> names = new ArrayList<>();
        row.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> theWorkBeingWaitedOn(JsonNode list) {
        List<String> awaited = new ArrayList<>();
        for (JsonNode row : list) {
            awaited.add(row.get("onBracketId").asText());
        }
        return awaited;
    }

    private static JsonNode rowAbout(JsonNode list, String onBracketId) {
        for (JsonNode row : list) {
            if (row.get("onBracketId").asText().equals(onBracketId)) {
                return row;
            }
        }
        throw new AssertionError("no row about bracket " + onBracketId + " in " + list);
    }

    private int nobodyMayMarkWorkAnyMore() {
        return jdbc.update("delete from auth_role_permission where permission_name = 'WORK_NODE_MARK'");
    }

    private void everybodyMayMarkWorkAgain() {
        jdbc.update(
                """
                insert into auth_role_permission (role_name, permission_name)
                select role_name, 'WORK_NODE_MARK' from (values ('OWNER'), ('MANAGER'), ('EMPLOYEE')) as roles(role_name)
                where not exists (
                    select 1 from auth_role_permission held
                    where held.role_name = roles.role_name and held.permission_name = 'WORK_NODE_MARK')
                """);
    }

    private record TheWork(
            String job,
            String productionRoom,
            String planningRoom,
            String posts,
            String video,
            String audit,
            String layout,
            String copyDeck,
            String pitchDeck,
            String printRun,
            String layoutWaitsOnPosts,
            String copyWaitsOnVideo) {}

    private TheWork theCampaign(Company company, RoundTripClient andrei, RoundTripClient elena, RoundTripClient ioana)
            throws Exception {
        String production = groupCalled(browser, "Aurora · productie");
        String planning = groupCalled(browser, "Aurora · planificare");
        for (RoundTripClient who : List.of(andrei, elena, ioana)) {
            joins(who, production);
            joins(who, planning);
        }

        String job = openJob(andrei, said(andrei, production, "Aurora Coffee vrea o campanie de vara"), "Campanie");

        String posts = started(browser, production, job, company.andrei(), "SOCIAL_POSTS");
        String video = started(browser, production, job, company.andrei(), "VIDEO_EDIT");
        String audit = started(browser, production, job, company.andrei(), "AUDIT_PASS");

        String layout = started(browser, planning, job, company.elena(), "DESIGN_LAYOUT");
        String copyDeck = started(browser, planning, job, company.elena(), "COPY_DECK");
        String pitchDeck = started(browser, planning, job, company.elena(), "PITCH_DECK");
        String printRun = started(browser, planning, job, company.ioana(), "PRINT_RUN");

        String layoutWaitsOnPosts = waits(elena, layout, posts);
        String copyWaitsOnVideo = waits(elena, copyDeck, video);

        waits(andrei, video, layout);
        waits(ioana, printRun, layout);

        waits(elena, pitchDeck, audit);
        delivered(andrei, production, audit);

        declaredThreeDaysEarlier(copyWaitsOnVideo);

        return new TheWork(
                job,
                production,
                planning,
                posts,
                video,
                audit,
                layout,
                copyDeck,
                pitchDeck,
                printRun,
                layoutWaitsOnPosts,
                copyWaitsOnVideo);
    }

    @Test
    void theListNamesExactlyTheWaitsTheTileCounts() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        TheWork work = theCampaign(company, andrei, elena, ioana);

        assertThat(openWaitsAimedAt(company.andrei()))
                .as("two open waits point at his work, so nothing below is asserted over an empty fixture")
                .isEqualTo(2L);
        assertThat(openWaitsAimedAt(company.elena()))
                .as("somebody else's waits must exist too, or the scoping clause could be deleted unnoticed")
                .isEqualTo(2L);
        assertThat(settledWaitsAimedAt(company.andrei()))
                .as("R7.6 - the audit was delivered, so there is a satisfied row for the clause to exclude")
                .isEqualTo(1L);

        JsonNode his = waitsFor(andrei);
        int hisTile = waitingOnYouFor(andrei);

        assertThat(his.size())
                .as("the list and the count are one predicate: a panel of three under a tile of two is the "
                        + "defect this endpoint exists to make impossible")
                .isEqualTo(hisTile);
        assertThat(hisTile)
                .as("Elena's layout and her copy deck, and nothing his own wait or Ioana's contributes")
                .isEqualTo(2);

        JsonNode oldest = his.get(0);
        assertThat(oldest.get("waitId").asText())
                .as("oldest first - the copy deck has been blocked since Tuesday, the layout since today")
                .isEqualTo(work.copyWaitsOnVideo());
        assertThat(his.get(1).get("waitId").asText()).isEqualTo(work.layoutWaitsOnPosts());
        assertThat(Instant.parse(oldest.get("declaredAt").asText()))
                .as("declaredAt is what the ordering is over, so it must actually ascend")
                .isBefore(Instant.parse(his.get(1).get("declaredAt").asText()));

        assertThat(fieldNamesOf(oldest))
                .as("R13.1 and R12.5 - the address, the kind and the moment are structure; a reason is "
                        + "somebody's sentence and a duration is a figure whose phase nobody named")
                .containsExactlyElementsOf(THE_WHOLE_ROW);

        assertThat(oldest.get("kind").asText()).isEqualTo("COLLEAGUE");
        assertThat(oldest.get("onBracketId").asText())
                .as("the awaited end - HIS work, which is what somebody is blocked on")
                .isEqualTo(work.video());
        assertThat(oldest.get("onAddress").asText())
                .as("`project › WORK_TYPE`, the same wording the mark strip and the work tab use")
                .isEqualTo(PROJECT + " › VIDEO_EDIT");
        assertThat(oldest.get("waitingBracketId").asText()).isEqualTo(work.copyDeck());
        assertThat(oldest.get("waitingAddress").asText()).isEqualTo(PROJECT + " › COPY_DECK");
        assertThat(oldest.get("waitingPerformerName").asText())
                .as("R12.1 - attribution, once, so he knows who is held up. No figure is keyed to it")
                .isEqualTo("Elena Dobre");
        assertThat(oldest.get("conversationId").asText())
                .as("the room holding the work he must deliver, not the room she declared the wait in")
                .isEqualTo(conversationOf(work.video()))
                .isEqualTo(work.productionRoom())
                .isNotEqualTo(work.planningRoom());

        JsonNode aboutThePosts = rowAbout(his, work.posts());
        assertThat(aboutThePosts.get("waitingBracketId").asText()).isEqualTo(work.layout());
        assertThat(aboutThePosts.get("onAddress").asText()).isEqualTo(PROJECT + " › SOCIAL_POSTS");

        assertThat(theWorkBeingWaitedOn(his))
                .as("every row's awaited end is HIS work. His own wait on Elena's layout is the other end "
                        + "of the same table - a different figure and a different tile - so the layout is "
                        + "never what a row here is waiting ON, though it is legitimately what is held up")
                .containsOnly(work.posts(), work.video());
        assertThat(his.toString())
                .as("Ioana's print run is a colleague's blockage on a colleague's work and is none of his "
                        + "business; the pitch deck's wait on his audit pass was answered this morning")
                .doesNotContain(work.printRun())
                .doesNotContain(work.pitchDeck())
                .doesNotContain(work.audit());
        assertThat(his.toString())
                .as("R13.1 - the reason is free text from a conversation the viewer may not be in")
                .doesNotContain(HER_OWN_WORDS)
                .doesNotContain("Ioana");
    }

    @Test
    void aColleaguesWaitsAreNotOnYourListAndCannotBeAskedFor() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");
        TheWork work = theCampaign(company, andrei, elena, ioana);

        JsonNode hers = waitsFor(elena);

        assertThat(hers.size())
                .as("her list agrees with her tile too - the agreement is a property of the pair, not of one row")
                .isEqualTo(waitingOnYouFor(elena));
        assertThat(hers.size())
                .as("Andrei's video and Ioana's print run, both blocked on her layout")
                .isEqualTo(2);

        assertThat(rowAbout(hers, work.layout()).get("onAddress").asText()).isEqualTo(PROJECT + " › DESIGN_LAYOUT");
        assertThat(hers.toString())
                .as("both of the people she is holding up are named, once each, as attribution")
                .contains("Andrei Munteanu")
                .contains("Ioana Radu");
        assertThat(theWorkBeingWaitedOn(hers))
                .as("every row's awaited end is HER work, and the layout is the only piece of it anybody "
                        + "is blocked on")
                .containsOnly(work.layout());
        assertThat(hers.toString())
                .as("what SHE is waiting for is Andrei's problem: her copy deck waits on his video, and "
                        + "neither end of that wait belongs on a list of what is wanted FROM her")
                .doesNotContain(work.posts())
                .doesNotContain(work.copyDeck());

        ResponseEntity<String> aimedAtAColleague =
                elena.get("/api/discovery/graph/my-waits?performerId=" + company.andrei());
        assertThat(aimedAtAColleague.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(aimedAtAColleague.getBody()))
                .as("R12.1 - there is no parameter that could point this read at somebody else, so naming "
                        + "one changes nothing at all")
                .isEqualTo(hers);
    }

    @Test
    void theListIsRefusedToACallerWhoseRoleNoLongerHoldsWorkNodeMark() throws Exception {
        buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(andrei.get("/api/discovery/graph/my-waits").getStatusCode())
                .as("the door is open while his role holds the grant")
                .isEqualTo(HttpStatus.OK);

        assertThat(nobodyMayMarkWorkAnyMore())
                .as("V65's three grants must actually have gone, or the 403 below would mean something else")
                .isEqualTo(3);
        try {
            ResponseEntity<String> refused = andrei.get("/api/discovery/graph/my-waits");

            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(json.readTree(refused.getBody()).get("code").asText())
                    .as("a refusal that reads as a fault teaches the person to retry something that cannot work")
                    .isEqualTo("NOT_PERMITTED");
        } finally {
            everybodyMayMarkWorkAgain();
        }
    }
}
