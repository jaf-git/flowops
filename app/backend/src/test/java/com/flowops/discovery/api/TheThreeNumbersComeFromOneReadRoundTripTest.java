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

@Tag("DISCOVERY-VIEW-MY-WORK-COUNTS-01")
class TheThreeNumbersComeFromOneReadRoundTripTest extends CompanyScenarioTest {
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
        ResponseEntity<String> opened =
                who.post("/api/discovery/jobs", "{\"messageId\":\"%s\",\"name\":\"%s\"}".formatted(messageId, name));
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

    private void waits(RoundTripClient who, String bracket, String onBracket) {
        ResponseEntity<String> declared = who.post(
                "/api/discovery/brackets/" + bracket + "/wait",
                """
                {"kind":"COLLEAGUE","onBracketId":"%s","reason":"astept sa termine","expectedBy":null}
                """
                        .formatted(onBracket));
        assertThat(declared.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private JsonNode numbersFor(RoundTripClient who) throws Exception {
        ResponseEntity<String> read = who.get("/api/discovery/graph/my-work-counts");
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(read.getBody());
    }

    private long liveBracketsOf(UUID person) {
        return jdbc.queryForObject(
                "select count(*) from work_bracket where performer_ref = ? and state in ('OPEN','WAITING')",
                Long.class,
                person);
    }

    private long boundariesHeldBy(UUID person) {
        return jdbc.queryForObject(
                "select count(*) from work_bracket where performer_ref = ? and is_boundary = true", Long.class, person);
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

    @Test
    void theThreeNumbersDescribeTheViewersOwnWorkAndNobodyElses() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        String room = groupCalled(browser, "Campanie Aurora");
        joins(andrei, room);
        joins(elena, room);

        String job = openJob(andrei, said(andrei, room, "Aurora Coffee vrea un rebranding"), "Rebranding Aurora");

        String posts = started(browser, room, job, company.andrei(), "SOCIAL_POSTS");
        String video = started(browser, room, job, company.andrei(), "VIDEO_EDIT");
        String audit = started(browser, room, job, company.andrei(), "AUDIT_PASS");
        String photos = started(browser, room, job, company.andrei(), "PHOTO_SET");
        String archive = started(browser, room, job, company.andrei(), "ARCHIVE_PASS");

        String layout = started(browser, room, job, company.elena(), "DESIGN_LAYOUT");
        String copy = started(browser, room, job, company.elena(), "COPY_DECK");
        String print = started(browser, room, job, company.elena(), "PRINT_RUN");

        waits(elena, layout, posts);
        waits(elena, copy, video);
        waits(andrei, video, layout);

        delivered(andrei, room, photos);
        delivered(andrei, room, archive);
        delivered(elena, room, print);

        assertThat(jdbc.update(
                        "update work_bracket set closed_at = closed_at - interval '14 days' where id = ?::uuid",
                        archive))
                .as("there must actually be a delivery outside the week, or the boundary below is untested")
                .isEqualTo(1);

        assertThat(liveBracketsOf(company.andrei()))
                .as("three pieces of work and the engagement's container, so the count below is not over nothing")
                .isEqualTo(4L);
        assertThat(boundariesHeldBy(company.andrei()))
                .as("R4a.1 - the container is his, and it is what `openWork` must refuse to count")
                .isEqualTo(1L);
        assertThat(openWaitsAimedAt(company.andrei()))
                .as("two open waits point at his work, so `waitingOnYou` is counted over a real population")
                .isEqualTo(2L);

        JsonNode his = numbersFor(andrei);
        JsonNode hers = numbersFor(elena);

        assertThat(his.get("waitingOnYou").asInt())
                .as("two waits, not two waiters - R12.1 permits the figure precisely because no person is an axis")
                .isEqualTo(2);
        assertThat(his.get("openWork").asInt())
                .as("the posts, the video and the audit. NOT the engagement's boundary, which is a container")
                .isEqualTo(3);
        assertThat(his.get("deliveredThisWeek").asInt())
                .as("the photo set this morning; the archive pass was a fortnight ago and is a different week")
                .isEqualTo(1);

        assertThat(hers.get("waitingOnYou").asInt())
                .as("only Andrei's video waits on her layout")
                .isEqualTo(1);
        assertThat(hers.get("openWork").asInt())
                .as("the layout and the copy deck - she opened no engagement, so she holds no boundary")
                .isEqualTo(2);
        assertThat(hers.get("deliveredThisWeek").asInt())
                .as("the print run, and nothing of Andrei's")
                .isEqualTo(1);

        assertThat(hers.toString())
                .as("R12.1 - the response has three numbers and nowhere at all to put a name")
                .doesNotContain("Andrei")
                .doesNotContain("Elena");
    }

    @Test
    void theEngagementsOwnBoundaryIsAContainerAndNeverCountsAsOpenWork() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String room = groupCalled(browser, "Aurora, pe scurt");
        joins(andrei, room);

        String job = openJob(andrei, said(andrei, room, "Aurora vrea un rebranding"), "Rebranding Aurora");
        started(browser, room, job, company.andrei(), "SOCIAL_POSTS");

        assertThat(boundariesHeldBy(company.andrei()))
                .as("the container exists and is his, which is the whole premise of this test")
                .isEqualTo(1L);
        assertThat(liveBracketsOf(company.andrei()))
                .as("two live rows, and only one of them is work")
                .isEqualTo(2L);

        assertThat(numbersFor(andrei).get("openWork").asInt())
                .as("R4a.1 - the container is not work, so the one thing he is actually doing is the answer")
                .isEqualTo(1);
    }

    @Test
    void theNumbersAreRefusedToACallerWhoseRoleNoLongerHoldsWorkNodeMark() throws Exception {
        buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(andrei.get("/api/discovery/graph/my-work-counts").getStatusCode())
                .as("the door is open while his role holds the grant")
                .isEqualTo(HttpStatus.OK);

        assertThat(nobodyMayMarkWorkAnyMore())
                .as("V65's three grants must actually have gone, or the 403 below would mean something else")
                .isEqualTo(3);
        try {
            ResponseEntity<String> refused = andrei.get("/api/discovery/graph/my-work-counts");

            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(json.readTree(refused.getBody()).get("code").asText())
                    .as("a refusal that reads as a fault teaches the person to retry something that cannot work")
                    .isEqualTo("NOT_PERMITTED");
        } finally {
            everybodyMayMarkWorkAgain();
        }
    }
}
