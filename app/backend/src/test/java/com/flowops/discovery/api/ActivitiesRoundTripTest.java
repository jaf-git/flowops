package com.flowops.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-MANAGE-ACTIVITIES-01")
@Tag("DISCOVERY-MARK-WORK-01")
class ActivitiesRoundTripTest extends CompanyScenarioTest {
    private ResponseEntity<String> name(RoundTripClient who, String activity) {
        return who.post("/api/discovery/activities", "{\"name\":\"%s\"}".formatted(activity));
    }

    private JsonNode activities(RoundTripClient who) throws Exception {
        ResponseEntity<String> listed = who.get("/api/discovery/activities");
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(listed.getBody());
    }

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

    private ResponseEntity<String> mark(
            RoundTripClient who, String messageId, String jobId, UUID performer, String activityId) {
        return who.post(
                "/api/discovery/work",
                """
                {"messageId":"%s","jobId":"%s","verb":"CREATE","performerId":"%s","workType":null,
                 "activityId":%s,"joining":null}
                """
                        .formatted(
                                messageId, jobId, performer, activityId == null ? "null" : "\"" + activityId + "\""));
    }

    @Test
    void anActivityIsNamedOnceAndEverySpellingOfItFindsTheSameOne() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        ResponseEntity<String> first = name(maria, "Write the caption");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = json.readTree(first.getBody()).get("id").asText();
        assertThat(json.readTree(first.getBody()).get("slug").asText()).isEqualTo("write-the-caption");

        ResponseEntity<String> again = name(maria, "  write the CAPTION ");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(again.getBody()).get("id").asText())
                .as("a second spelling returning a second identifier would divide one step's history in two, "
                        + "which is the whole failure the picker exists to prevent")
                .isEqualTo(id);

        assertThat(jdbc.queryForObject("select count(*) from activity", Long.class))
                .isEqualTo(1);
    }

    @Test
    void aNameThatNormalisesToNothingIsRefusedRatherThanStored() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        assertThat(name(maria, "+++").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("select count(*) from activity", Long.class))
                .as("a refused name leaves nothing behind - an activity with an empty slug would collide with "
                        + "the next one and silently merge two steps")
                .isZero();
    }

    @Test
    void amarkCarriesTheActivityAndTheUseIsRecordedAgainstIt() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        String activityId = json.readTree(name(maria, "Write the caption").getBody())
                .get("id")
                .asText();

        String room = conversationBetween(maria, company.andrei());
        String opening = said(maria, room, "summer menu for Aurora starts today");
        String jobId = openJob(maria, opening, "Summer menu");
        String did = said(maria, room, "caption written with the hashtags and the call to action");

        ResponseEntity<String> marked = mark(maria, did, jobId, company.andrei(), activityId);
        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode placed = json.readTree(marked.getBody());
        assertThat(placed.get("activity").asText()).isEqualTo("Write the caption");

        String nodeId = placed.get("nodeId").asText();

        assertThat(jdbc.queryForObject(
                        "select activity_id::text from work_node where id = ?::uuid", String.class, nodeId))
                .as("the node itself carries it, because the step key reads the node and never joins to a usage row")
                .isEqualTo(activityId);

        assertThat(jdbc.queryForObject(
                        "select count(*) from activity_usage where work_node_id = ?::uuid", Long.class, nodeId))
                .as("one row per use is what makes a scoped suggestion possible")
                .isEqualTo(1);

        assertThat(jdbc.queryForObject(
                        "select performer_role_id is not null from activity_usage where work_node_id = ?::uuid",
                        Boolean.class,
                        nodeId))
                .as("the usage row takes the department from the node rather than being told it, so the two "
                        + "can never disagree")
                .isTrue();

        assertThat(jdbc.queryForObject("select times_used from activity where id = ?::uuid", Integer.class, activityId))
                .isEqualTo(1);

        // The mark response is not the screen anybody looks at afterwards. The engagement graph is, and it
        // read six columns without this one for a whole slice - the field was written, stored, keyed on, and
        // invisible to every person the feature was built for.
        JsonNode graph =
                json.readTree(maria.get("/api/discovery/graph/job/" + jobId).getBody());
        JsonNode onTheGraph = StreamSupport.stream(graph.get("nodes").spliterator(), false)
                .filter(node -> nodeId.equals(node.get("nodeId").asText()))
                .findFirst()
                .orElseThrow();

        assertThat(onTheGraph.get("activity").asText())
                .as("the graph carries the name rather than the id, because a person reads the graph")
                .isEqualTo("Write the caption");
    }

    @Test
    void aMarkThatNamesNoActivityIsStillAMark() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        String room = conversationBetween(maria, company.elena());
        String opening = said(maria, room, "autumn shoot for Aurora starts today");
        String jobId = openJob(maria, opening, "Autumn shoot");
        String did = said(maria, room, "twelve frames shot and the best ten picked out");

        ResponseEntity<String> marked = mark(maria, did, jobId, company.elena(), null);

        assertThat(marked.getStatusCode())
                .as("nullable is the safety property: a workspace that has never named an activity marks work "
                        + "exactly as it did before the column existed")
                .isEqualTo(HttpStatus.CREATED);

        JsonNode placed = json.readTree(marked.getBody());
        assertThat(placed.get("activity").isNull()).isTrue();
        assertThat(jdbc.queryForObject(
                        "select activity_id from work_node where id = ?::uuid",
                        String.class,
                        placed.get("nodeId").asText()))
                .isNull();
    }

    @Test
    void anActivityNobodyNamedIsRefusedAndTheMarkIsNotHalfWritten() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        String room = conversationBetween(maria, company.andrei());
        String opening = said(maria, room, "winter campaign for Aurora starts today");
        String jobId = openJob(maria, opening, "Winter campaign");
        String did = said(maria, room, "the winter campaign copy is drafted and ready to read");

        ResponseEntity<String> marked =
                mark(maria, did, jobId, company.andrei(), UUID.randomUUID().toString());

        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForObject(
                        "select count(*) from work_node_evidence where message_id = ?::uuid", Long.class, did))
                .as("the whole mark is one transaction, so an unknown activity leaves no node behind")
                .isZero();
    }

    @Test
    void theListSaysWhichDepartmentsUseAnActivity() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        String activityId =
                json.readTree(name(maria, "Final review").getBody()).get("id").asText();

        String room = conversationBetween(maria, company.andrei());
        String opening = said(maria, room, "spring menu for Aurora starts today");
        String jobId = openJob(maria, opening, "Spring menu");
        String did = said(maria, room, "read it through and the copy is approved to go out");
        assertThat(mark(maria, did, jobId, company.andrei(), activityId).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        JsonNode listed = activities(maria);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("timesUsed").asInt()).isEqualTo(1);
        assertThat(listed.get(0).get("departments").get(0).asText()).isEqualTo(CONTENT_WRITER);
        assertThat(listed.get(0).get("tooGenericToBeOneThing").asBoolean())
                .as("one department is not a spread; three is the point at which \"review\" has stopped naming "
                        + "one thing")
                .isFalse();
    }

    @Test
    void twoNamesForOnePieceOfWorkBecomeOneAndEveryNodeMovesAcross() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        String caption = json.readTree(name(maria, "Write the caption").getBody())
                .get("id")
                .asText();
        String copy =
                json.readTree(name(maria, "Post copy").getBody()).get("id").asText();

        String room = conversationBetween(maria, company.andrei());
        String opening = said(maria, room, "summer menu for Aurora starts today");
        String jobId = openJob(maria, opening, "Summer menu");
        String did = said(maria, room, "the words under the picture are written and ready");

        String node = json.readTree(
                        mark(maria, did, jobId, company.andrei(), copy).getBody())
                .get("nodeId")
                .asText();

        ResponseEntity<String> merged =
                maria.post("/api/discovery/activities/" + copy + "/merge", "{\"intoId\":\"%s\"}".formatted(caption));

        assertThat(merged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(merged.getBody()).get("id").asText())
                .as("the surviving activity comes back, because the caller has to know which name won")
                .isEqualTo(caption);

        assertThat(jdbc.queryForObject(
                        "select activity_id::text from work_node where id = ?::uuid", String.class, node))
                .as("the node moves, or the step key it produces stays split across two names and the merge "
                        + "changed nothing that matters")
                .isEqualTo(caption);

        assertThat(jdbc.queryForObject(
                        "select activity_id::text from activity_usage where work_node_id = ?::uuid",
                        String.class,
                        node))
                .isEqualTo(caption);

        assertThat(jdbc.queryForObject(
                        "select merged_into_id::text from activity where id = ?::uuid", String.class, copy))
                .as("the losing name is kept with a pointer rather than deleted; the pointer is the record "
                        + "of what a person decided")
                .isEqualTo(caption);

        assertThat(activities(maria)).hasSize(1);
    }

    @Test
    void aMergeIntoItselfIsRefusedAndNothingMoves() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        String caption = json.readTree(name(maria, "Write the caption").getBody())
                .get("id")
                .asText();

        ResponseEntity<String> refused =
                maria.post("/api/discovery/activities/" + caption + "/merge", "{\"intoId\":\"%s\"}".formatted(caption));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("ACTIVITY_MERGE_REFUSED");
        assertThat(jdbc.queryForObject("select status from activity where id = ?::uuid", String.class, caption))
                .isEqualTo("ACTIVE");
    }

    @Test
    void retiringTakesANameOutOfTheListAndLeavesTheWorkExactlyWhereItWas() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        String stuff = json.readTree(name(maria, "Stuff").getBody()).get("id").asText();

        String room = conversationBetween(maria, company.andrei());
        String opening = said(maria, room, "autumn menu for Aurora starts today");
        String jobId = openJob(maria, opening, "Autumn menu");
        String did = said(maria, room, "did the thing that was asked for on the menu");

        String node = json.readTree(
                        mark(maria, did, jobId, company.andrei(), stuff).getBody())
                .get("nodeId")
                .asText();

        assertThat(maria.post("/api/discovery/activities/" + stuff + "/retire", "{}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(activities(maria)).as("nobody can pick it again").isEmpty();

        assertThat(jdbc.queryForObject(
                        "select activity_id::text from work_node where id = ?::uuid", String.class, node))
                .as("a step that has been discovered stays the step it was; retiring a name is not a rewrite "
                        + "of the graph behind it")
                .isEqualTo(stuff);
    }

    private String approvedTemplateCalled(RoundTripClient who, String title) throws Exception {
        ResponseEntity<String> written = who.post(
                "/api/task-templates",
                """
                {"title":"%s","description":"Twelve posts of copy.","type":"CONTENT","priority":"NORMAL",
                 "checklist":["Written","Read back"],"submitForApproval":true}
                """
                        .formatted(title));
        assertThat(written.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String id = json.readTree(written.getBody()).get("id").asText();

        // Whether writing it also approves it depends on who wrote it, so this asks for approval only
        // where the template is actually waiting - approving twice is refused by design.
        if (!"APPROVED"
                .equals(jdbc.queryForObject("select status from task_template where id = ?::uuid", String.class, id))) {
            ResponseEntity<String> approved = who.post(
                    "/api/task-templates/" + id + "/approval",
                    """
                    {"title":"%s","description":"Twelve posts of copy.","type":"CONTENT","priority":"NORMAL",
                     "checklist":["Written","Read back"],"submitForApproval":false}
                    """
                            .formatted(title));
            assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        assertThat(jdbc.queryForObject("select status from task_template where id = ?::uuid", String.class, id))
                .as("the finding is about APPROVED templates, so the fixture has to reach that state")
                .isEqualTo("APPROVED");
        return id;
    }

    @Test
    void aMergeThatStrandsAnApprovedTemplateSaysSo() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        String caption = json.readTree(name(maria, "Write the caption").getBody())
                .get("id")
                .asText();
        String copy =
                json.readTree(name(maria, "Post copy").getBody()).get("id").asText();

        String room = conversationBetween(maria, company.andrei());
        String opening = said(maria, room, "spring menu for Aurora starts today");
        String jobId = openJob(maria, opening, "Spring menu");
        String did = said(maria, room, "the words under the picture are written and ready");
        assertThat(mark(maria, did, jobId, company.andrei(), copy).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // An approved template named after the activity about to be absorbed, written and approved
        // through the product's own endpoints, and an analysis for the finding to attach to. Both are
        // the ordinary state of a workspace that has been running for a while.
        String template = approvedTemplateCalled(maria, "Post copy");
        assertThat(maria.post("/api/analysis/runs", "{}").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> merged =
                maria.post("/api/discovery/activities/" + copy + "/merge", "{\"intoId\":\"%s\"}".formatted(caption));
        assertThat(merged.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject("select status from task_template where id = ?::uuid", String.class, template))
                .as("nothing was retired: deciding two words mean one thing is not evidence that a template "
                        + "somebody approved has stopped describing real work")
                .isEqualTo("APPROVED");

        JsonNode queue = json.readTree(maria.get("/api/analysis/findings").getBody());
        JsonNode raised = null;
        for (JsonNode group : queue.get("groups")) {
            for (JsonNode one : group.get("items")) {
                if ("templates_left_by_a_merge".equals(one.path("kind").asText())) {
                    raised = one;
                }
            }
        }

        assertThat(raised)
                .as("the person who has just merged is the one who can judge the templates, so the finding "
                        + "is raised at merge time rather than on the next scheduled run")
                .isNotNull();
        assertThat(raised.get("headline").asText()).contains("Post copy").contains("Write the caption");
        assertThat(raised.get("action").asText()).isEqualTo("Review them");
        assertThat(raised.get("reach").asInt()).isEqualTo(1);

        JsonNode evidence = json.readTree(
                maria.get("/api/analysis/findings/" + raised.get("id").asText() + "/evidence")
                        .getBody());
        assertThat(evidence.toString())
                .as("the templates are the evidence, so a reader opens the finding and sees which ones")
                .contains(template);
    }

    @Test
    void aMergeThatStrandsNothingRaisesNothing() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        String caption = json.readTree(name(maria, "Write the caption").getBody())
                .get("id")
                .asText();
        String copy =
                json.readTree(name(maria, "Post copy").getBody()).get("id").asText();

        assertThat(maria.post("/api/analysis/runs", "{}").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(maria.post("/api/discovery/activities/" + copy + "/merge", "{\"intoId\":\"%s\"}".formatted(caption))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject(
                        "select count(*) from analysis_finding where detector = 'S2_VOCABULARY' "
                                + "and headline like '%still name%'",
                        Long.class))
                .as("a library with nothing stranded produces no finding, rather than an empty one")
                .isZero();
    }

    @Test
    void thePreviewSaysWhoAlreadyDidWorkOfThisKindInThisEngagement() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        String caption = json.readTree(name(maria, "Write the caption").getBody())
                .get("id")
                .asText();

        String room = conversationBetween(maria, company.andrei());
        String opening = said(maria, room, "winter menu for Aurora starts today");
        String jobId = openJob(maria, opening, "Winter menu");
        String first = said(maria, room, "first caption is written and ready to read");

        assertThat(mark(maria, first, jobId, company.andrei(), caption).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> preview =
                maria.get("/api/discovery/brackets/preview?jobId=%s&conversationId=%s&performerId=%s"
                        .formatted(jobId, room, company.andrei()));

        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode echo = json.readTree(preview.getBody()).get("earlierWorkHere");
        assertThat(echo.isNull())
                .as("the prompt fires at the moment of ambiguity, and this engagement already holds work of "
                        + "this kind")
                .isFalse();
        assertThat(echo.get("activities").get(0).asText())
                .as("what is already named here is what makes \"what is different about this one\" answerable")
                .isEqualTo("Write the caption");
        assertThat(echo.get("people")).hasSize(1);
    }
}
