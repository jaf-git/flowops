package com.flowops.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-VIEW-MY-TRACK-01")
class TheChainOfMyOwnWorkRoundTripTest extends CompanyScenarioTest {
    private static final List<String> THE_WHOLE_LINE =
            List.of("bracketId", "conversationId", "address", "workType", "state", "closeKind", "nodes");

    private static final List<String> THE_WHOLE_CIRCLE = List.of("nodeId", "kind", "messageId");

    private static final String PROJECT = "Campanie de vara";

    private static final String HIS_SECOND_SENTENCE = "am pus si varianta pentru instagram";

    private static final String HIS_DELIVERY_SENTENCE = "gata, uite rezultatul";

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

    private JsonNode marked(
            RoundTripClient who, String message, String job, UUID performer, String workType, String verb)
            throws Exception {
        ResponseEntity<String> pressed = who.post(
                "/api/discovery/work",
                """
                {"messageId":"%s","jobId":"%s","verb":"%s","performerId":"%s","workType":"%s","joining":null}
                """
                        .formatted(message, job, verb, performer, workType));
        assertThat(pressed.getStatusCode())
                .as("the fixture has to be built through the door, or it proves nothing about the rows")
                .isEqualTo(HttpStatus.CREATED);
        return json.readTree(pressed.getBody());
    }

    private void delivered(RoundTripClient who, String conversation, String bracket) throws Exception {
        String message = said(who, conversation, HIS_DELIVERY_SENTENCE);
        ResponseEntity<String> done =
                who.post("/api/discovery/work/" + message + "/deliver", "{\"bracketId\":\"%s\"}".formatted(bracket));
        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String handedOver(RoundTripClient who, String bracket, UUID successor, String announcement)
            throws Exception {
        ResponseEntity<String> moved = who.post(
                "/api/discovery/brackets/" + bracket + "/hand-over",
                """
                {"newPerformerId":"%s","messageId":"%s","causedByDeactivation":false}
                """
                        .formatted(successor, announcement));
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(moved.getBody()).get("successorBracketId").asText();
    }

    private JsonNode trackFor(RoundTripClient who) throws Exception {
        ResponseEntity<String> read = who.get("/api/discovery/graph/my-track");
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(read.getBody());
    }

    private long bracketsPerformedBy(UUID person, boolean boundary) {
        return jdbc.queryForObject(
                "select count(*) from work_bracket where performer_ref = ? and is_boundary = ?",
                Long.class,
                person,
                boundary);
    }

    private String boundaryOf(String job) {
        return jdbc.queryForObject(
                "select id::text from work_bracket where job_id = ?::uuid and is_boundary = true", String.class, job);
    }

    private String conversationOf(String bracket) {
        return jdbc.queryForObject(
                "select conversation_id::text from work_bracket where id = ?::uuid", String.class, bracket);
    }

    private String closedByNodeOf(String bracket) {
        return jdbc.queryForObject(
                "select closed_by_node::text from work_bracket where id = ?::uuid", String.class, bracket);
    }

    private String pairedNodeOf(String node) {
        return jdbc.queryForObject("select paired_node_id::text from work_node where id = ?::uuid", String.class, node);
    }

    private List<String> nodesOldestFirst(String bracket) {
        return jdbc.queryForList(
                "select id::text from work_node where bracket_id = ?::uuid order by created_at, id",
                String.class,
                bracket);
    }

    private static List<String> fieldNamesOf(JsonNode row) {
        List<String> names = new ArrayList<>();
        row.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> bracketsIn(JsonNode lines) {
        List<String> ids = new ArrayList<>();
        for (JsonNode line : lines) {
            ids.add(line.get("bracketId").asText());
        }
        return ids;
    }

    private static JsonNode lineAbout(JsonNode lines, String bracketId) {
        for (JsonNode line : lines) {
            if (line.get("bracketId").asText().equals(bracketId)) {
                return line;
            }
        }
        throw new AssertionError("no line about bracket " + bracketId + " in " + lines);
    }

    private static List<String> kindsOf(JsonNode line) {
        List<String> kinds = new ArrayList<>();
        for (JsonNode node : line.get("nodes")) {
            kinds.add(node.get("kind").asText());
        }
        return kinds;
    }

    private static List<String> nodeIdsOf(JsonNode line) {
        List<String> ids = new ArrayList<>();
        for (JsonNode node : line.get("nodes")) {
            ids.add(node.get("nodeId").asText());
        }
        return ids;
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
            String room,
            String boundary,
            String posts,
            String postsOpening,
            String postsSecondStep,
            String video,
            String videoOpening,
            String audit,
            String elenaLayout,
            String elenaSuccessor) {}

    private TheWork theCampaign(Company company, RoundTripClient andrei, RoundTripClient elena) throws Exception {
        String room = groupCalled(browser, "Aurora · productie");
        joins(andrei, room);
        joins(elena, room);

        String job = openJob(andrei, said(andrei, room, "Aurora Coffee vrea o campanie de vara"), "Campanie");

        String postsOpening = said(andrei, room, "incep postarile pentru campanie");
        String posts = marked(browser, postsOpening, job, company.andrei(), "SOCIAL_POSTS", "CREATE")
                .get("bracketId")
                .asText();
        String postsSecondStep = said(andrei, room, HIS_SECOND_SENTENCE);
        marked(browser, postsSecondStep, job, company.andrei(), "SOCIAL_POSTS", "ADD");
        delivered(andrei, room, posts);

        String videoOpening = said(andrei, room, "iau montajul pe mine");
        String video = marked(browser, videoOpening, job, company.andrei(), "VIDEO_EDIT", "CREATE")
                .get("bracketId")
                .asText();
        String elenaSuccessor = handedOver(andrei, video, company.elena(), videoOpening);

        String audit = marked(
                        browser,
                        said(andrei, room, "mai fac o verificare inainte de livrare"),
                        job,
                        company.andrei(),
                        "AUDIT_PASS",
                        "CREATE")
                .get("bracketId")
                .asText();

        String elenaLayout = marked(
                        browser,
                        said(elena, room, "pregatesc layoutul"),
                        job,
                        company.elena(),
                        "DESIGN_LAYOUT",
                        "CREATE")
                .get("bracketId")
                .asText();

        return new TheWork(
                job,
                room,
                boundaryOf(job),
                posts,
                postsOpening,
                postsSecondStep,
                video,
                videoOpening,
                audit,
                elenaLayout,
                elenaSuccessor);
    }

    @Test
    void theChainReadsStartThenWorkThenEndInTheOrderItHappened() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        TheWork work = theCampaign(company, andrei, elena);

        assertThat(bracketsPerformedBy(company.andrei(), false))
                .as("three pieces of work are his, so nothing below is asserted over an empty fixture")
                .isEqualTo(3L);
        assertThat(bracketsPerformedBy(company.andrei(), true))
                .as("R4a.1 - his engagement's container exists, or the boundary clause could be deleted unnoticed")
                .isEqualTo(1L);
        assertThat(bracketsPerformedBy(company.elena(), false))
                .as("somebody else's work must exist too, or the scoping clause could be deleted unnoticed")
                .isEqualTo(2L);

        JsonNode his = trackFor(andrei);

        assertThat(bracketsIn(his))
                .as("his three pieces of work, and not the container he opened the engagement with")
                .containsExactlyInAnyOrder(work.posts(), work.video(), work.audit());

        JsonNode posts = lineAbout(his, work.posts());
        assertThat(fieldNamesOf(posts))
                .as("R12.1, R13.1 and R12.5 - no name, no sentence, no duration and no timestamp either")
                .containsExactlyElementsOf(THE_WHOLE_LINE);
        assertThat(posts.get("address").asText())
                .as("`project › WORK_TYPE`, the same wording the mark strip and the work tab use")
                .isEqualTo(PROJECT + " › SOCIAL_POSTS");
        assertThat(posts.get("workType").asText()).isEqualTo("SOCIAL_POSTS");
        assertThat(posts.get("state").asText()).isEqualTo("CLOSED");
        assertThat(posts.get("closeKind").asText()).isEqualTo("DELIVERED");
        assertThat(posts.get("conversationId").asText())
                .as("the room the work lives in, so a circle is a way back rather than a dot")
                .isEqualTo(conversationOf(work.posts()))
                .isEqualTo(work.room());

        assertThat(nodesOldestFirst(work.posts()))
                .as("the chain must actually have three nodes in the database, or the assertion below is vacuous")
                .hasSize(3);
        assertThat(kindsOf(posts))
                .as("R4.2 drawn literally - a START, the work in between, and the END that closed it, in "
                        + "that order. Reversed, the chain reads backwards and the page tells a person "
                        + "their work ended before it began")
                .containsExactly("START", "WORK", "END");
        assertThat(nodeIdsOf(posts))
                .as("oldest first, and the order is the database's rather than the planner's")
                .containsExactlyElementsOf(nodesOldestFirst(work.posts()));

        JsonNode start = posts.get("nodes").get(0);
        JsonNode middle = posts.get("nodes").get(1);
        JsonNode end = posts.get("nodes").get(2);

        assertThat(fieldNamesOf(start)).containsExactlyElementsOf(THE_WHOLE_CIRCLE);
        assertThat(start.get("messageId").asText())
                .as("a pointer the client resolves through a read that already checks who may see it")
                .isEqualTo(work.postsOpening());
        assertThat(middle.get("messageId").asText()).isEqualTo(work.postsSecondStep());

        assertThat(end.get("nodeId").asText())
                .as("the END is the node the bracket itself names as its ending")
                .isEqualTo(closedByNodeOf(work.posts()));
        assertThat(pairedNodeOf(start.get("nodeId").asText()))
                .as("R15.10 - the pairing work_node.paired_node_id was added for, and this is the first "
                        + "read that surfaces it: from the first circle, the last one is findable")
                .isEqualTo(end.get("nodeId").asText());

        JsonNode video = lineAbout(his, work.video());
        assertThat(video.get("state").asText()).isEqualTo("CLOSED");
        assertThat(video.get("closeKind").asText())
                .as("a handover is an ending and never a completion")
                .isEqualTo("HANDED_OVER");
        assertThat(kindsOf(video))
                .as("a handover writes no END node - nobody said anything, the work moved - so the stub he "
                        + "handed on is a START and nothing else")
                .containsExactly("START");

        JsonNode audit = lineAbout(his, work.audit());
        assertThat(audit.get("state").asText()).isEqualTo("OPEN");
        assertThat(audit.get("closeKind").isNull())
                .as("null is a real answer here - work that has not ended has no ending")
                .isTrue();

        assertThat(his.toString())
                .as("R4a.1 - the container is not work, and the page's whole subject is work somebody is "
                        + "carrying. Elena's layout and the successor she took on are hers")
                .doesNotContain(work.boundary())
                .doesNotContain(work.elenaLayout())
                .doesNotContain(work.elenaSuccessor());
        assertThat(his.toString())
                .as("R13.1 - a sentence belongs to its conversation. R12.1 - no name at all, because every "
                        + "line is already his and naming him would be the product telling him who he is")
                .doesNotContain(HIS_SECOND_SENTENCE)
                .doesNotContain(HIS_DELIVERY_SENTENCE)
                .doesNotContain("Andrei")
                .doesNotContain("Elena");
    }

    @Test
    void aColleaguesChainsAreNotOnYourTimelineAndCannotBeAskedFor() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        TheWork work = theCampaign(company, andrei, elena);

        JsonNode hers = trackFor(elena);

        assertThat(bracketsIn(hers))
                .as("her own layout, and the video edit she took over - both hers, neither his")
                .containsExactlyInAnyOrder(work.elenaLayout(), work.elenaSuccessor());
        assertThat(lineAbout(hers, work.elenaSuccessor()).get("workType").asText())
                .as("R14 - the type is inherited, never re-derived from the new person's role")
                .isEqualTo("VIDEO_EDIT");
        assertThat(hers.toString())
                .as("his three chains are his, and the container is nobody's")
                .doesNotContain(work.posts())
                .doesNotContain(work.video())
                .doesNotContain(work.audit())
                .doesNotContain(work.boundary());

        ResponseEntity<String> aimedAtAColleague =
                elena.get("/api/discovery/graph/my-track?performerId=" + company.andrei());
        assertThat(aimedAtAColleague.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(aimedAtAColleague.getBody()))
                .as("R12.1 - there is no parameter that could point this read at somebody else, so naming "
                        + "one changes nothing at all")
                .isEqualTo(hers);
    }

    @Test
    void theTimelineIsRefusedToACallerWhoseRoleNoLongerHoldsWorkNodeMark() throws Exception {
        buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(andrei.get("/api/discovery/graph/my-track").getStatusCode())
                .as("the door is open while his role holds the grant")
                .isEqualTo(HttpStatus.OK);

        assertThat(nobodyMayMarkWorkAnyMore())
                .as("V65's three grants must actually have gone, or the 403 below would mean something else")
                .isEqualTo(3);
        try {
            ResponseEntity<String> refused = andrei.get("/api/discovery/graph/my-track");

            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(json.readTree(refused.getBody()).get("code").asText())
                    .as("a refusal that reads as a fault teaches the person to retry something that cannot work")
                    .isEqualTo("NOT_PERMITTED");
        } finally {
            everybodyMayMarkWorkAgain();
        }
    }
}
