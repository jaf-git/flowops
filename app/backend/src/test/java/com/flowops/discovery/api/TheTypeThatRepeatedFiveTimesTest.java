package com.flowops.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-PROPOSE-TYPE-01")
@Tag("DISCOVERY-NAME-TYPE-01")
class TheTypeThatRepeatedFiveTimesTest extends CompanyScenarioTest {
    @BeforeEach
    void emptyTheCatalogueTheSharedResetCannotReach() {
        jdbc.execute("truncate track_type cascade");
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

    private void produced(RoundTripClient who, String nodeId, String outputType) {
        assertThat(who.post(
                                "/api/discovery/nodes/" + nodeId + "/output",
                                "{\"outputType\":\"%s\"}".formatted(outputType))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private void aPieceOfWorkAskedForAndDelivered(
            RoundTripClient andrei, UUID andreiId, String job, String conversation, String output) throws Exception {
        String asked = said(browser, conversation, "Andrei, poti sa faci asta pana joi?");
        JsonNode request = mark(browser, asked, job, "REQUEST", andreiId);

        String finished = said(andrei, conversation, "Gata, e in drive");
        JsonNode completion = mark(browser, finished, job, "COMPLETION", null);
        produced(andrei, completion.get("nodeId").asText(), output);

        ResponseEntity<String> ended =
                browser.post("/api/discovery/tracks/" + request.get("trackId").asText() + "/end", null);
        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(ended.getBody()).get("completeness").asText())
                .as("only a thread that qualifies is fingerprinted, and only a fingerprinted thread can cluster")
                .isEqualTo("COMPLETE");
    }

    private String anEngagementWithAndrei(UUID andreiId, String conversation) throws Exception {
        String brief = said(browser, conversation, "Aurora Coffee vrea materiale pentru vara");
        return openJob(browser, brief, "Aurora Coffee — materiale de vara");
    }

    private JsonNode typesAsSeenByTheOwner() throws Exception {
        ResponseEntity<String> queue = browser.get("/api/discovery/types");
        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(queue.getBody());
    }

    private JsonNode theOneTypeIn(JsonNode queue) {
        assertThat(queue)
                .as("one shape repeated, so exactly one row — a second would mean the clusterer split it")
                .hasSize(1);
        return queue.get(0);
    }

    private ResponseEntity<String> nameIt(String typeId, String name) {
        return browser.post("/api/discovery/types/" + typeId + "/name", "{\"name\":\"%s\"}".formatted(name));
    }

    @Test
    void fiveCompletedThreadsOfAShapeProposeATypeAndFourDoNot() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String job = anEngagementWithAndrei(company.andrei(), conversation);

        for (int afternoon = 1; afternoon <= 4; afternoon++) {
            aPieceOfWorkAskedForAndDelivered(andrei, company.andrei(), job, conversation, "TEXT");
        }

        JsonNode afterFour = theOneTypeIn(typesAsSeenByTheOwner());
        assertThat(afterFour.get("status").asText())
                .as("four is a shape worth tracking and not a question worth asking")
                .isEqualTo("CANDIDATE");
        assertThat(afterFour.get("occurrenceCount").asInt())
                .as("counter C2 counts completed THREADS — never nodes, and there were eight of those")
                .isEqualTo(4);

        aPieceOfWorkAskedForAndDelivered(andrei, company.andrei(), job, conversation, "TEXT");

        JsonNode afterFive = theOneTypeIn(typesAsSeenByTheOwner());
        assertThat(afterFive.get("status").asText())
                .as("the fifth completed thread is what turns a shape into a question for the owner")
                .isEqualTo("PROPOSED");
        assertThat(afterFive.get("occurrenceCount").asInt()).isEqualTo(5);
        assertThat(afterFive.get("name").isNull())
                .as("a proposal has no name; the name is the question")
                .isTrue();
        assertThat(afterFive.get("fromRoleName").asText())
                .as("a type is a hand-over between two ROLES, and the row says which")
                .isEqualTo(AGENCY_OWNER);
        assertThat(afterFive.get("toRoleName").asText()).isEqualTo(CONTENT_WRITER);
        assertThat(afterFive.get("terminalOutputType").asText())
                .as("what a thread of this shape ends by producing — the strongest component, bought for one tap")
                .isEqualTo("TEXT");
    }

    @Test
    void namingATypeASecondTimeIsRefused() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String job = anEngagementWithAndrei(company.andrei(), conversation);
        for (int afternoon = 1; afternoon <= 5; afternoon++) {
            aPieceOfWorkAskedForAndDelivered(andrei, company.andrei(), job, conversation, "TEXT");
        }
        String typeId = theOneTypeIn(typesAsSeenByTheOwner()).get("typeId").asText();

        assertThat(nameIt(typeId, "Content production").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> again = nameIt(typeId, "Content production");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(again.getBody()).get("code").asText()).isEqualTo("TYPE_NAME_TAKEN");

        ResponseEntity<String> shouting = nameIt(typeId, "content PRODUCTION");
        assertThat(shouting.getStatusCode())
                .as("Content Production and content production are one word to everybody except a database")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(shouting.getBody()).get("code").asText()).isEqualTo("TYPE_NAME_TAKEN");

        JsonNode named = theOneTypeIn(typesAsSeenByTheOwner());
        assertThat(named.get("status").asText()).isEqualTo("NAMED");
        assertThat(named.get("name").asText())
                .as("the first answer stands; a refused second attempt changes nothing")
                .isEqualTo("Content production");
    }

    @Test
    void aDismissedProposalDoesNotComeBack() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String job = anEngagementWithAndrei(company.andrei(), conversation);
        for (int afternoon = 1; afternoon <= 5; afternoon++) {
            aPieceOfWorkAskedForAndDelivered(andrei, company.andrei(), job, conversation, "TEXT");
        }
        String typeId = theOneTypeIn(typesAsSeenByTheOwner()).get("typeId").asText();

        assertThat(browser.post("/api/discovery/types/" + typeId + "/dismiss", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(typesAsSeenByTheOwner())
                .as("a queue that showed what the owner had dismissed would teach them that dismissing does nothing")
                .isEmpty();
        assertThat(typesAsSeenByTheOwner())
                .as("and a second sweep over the same five threads must not open a second row for the shape")
                .isEmpty();

        assertThat(jdbc.queryForObject("select count(*) from track_type", Long.class))
                .as("one row, still — the dismissal is the record, and nothing re-proposed the shape beside it")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("select status from track_type", String.class))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select count(*) from track where track_type_id = ?::uuid", Long.class, typeId))
                .as("the threads keep pointing at the answered shape, which is what makes the answer permanent")
                .isEqualTo(5L);
    }

    @Test
    void theDigestCarriesAtMostThreeDecisionsWhenMoreQualify() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String job = anEngagementWithAndrei(company.andrei(), conversation);
        for (int afternoon = 1; afternoon <= 5; afternoon++) {
            aPieceOfWorkAskedForAndDelivered(andrei, company.andrei(), job, conversation, "TEXT");
        }

        JsonNode digest = theWeek();

        assertThat(digest.get("proposals").asInt())
                .as("one shape reached the floor")
                .isEqualTo(1);
        assertThat(rolesThatMarkedNothing())
                .as("three stated jobs produced no evidence at all, so four things qualify in total")
                .isEqualTo(3L);

        JsonNode decisions = digest.get("decisions");
        assertThat(decisions)
                .as("three a week, maximum — the fourth waits, and it waits silently")
                .hasSize(3);
        assertThat(decisions.get(0).get("kind").asText())
                .as("ranked by value consumed, so the work the company actually spent its week on comes first")
                .isEqualTo("CONFIRM_A_NAME");
        assertThat(decisions.get(0).get("subject").asText())
                .as("a proposal has no name yet, so it is described as the hand-over it is")
                .isEqualTo(AGENCY_OWNER + " → " + CONTENT_WRITER);
        assertThat(decisions.get(0).get("occurrenceCount").asInt()).isEqualTo(5);

        for (JsonNode decision : decisions) {
            if (decision.get("kind").asText().equals("A_ROLE_STOPPED_CLICKING")) {
                assertThat(decision.get("subject").asText())
                        .as("keyed to the role, and the role is one of the words the company uses for a job")
                        .isIn(ACCOUNT_MANAGER, EDITOR, DESIGNER);
                assertThat(decision.get("typeId").isNull())
                        .as("a row about a role points at no type; offering one would answer a different question")
                        .isTrue();
            }
        }

        assertThat(digest.has("unread"))
                .as("no growing indicator, under any name — the contract carries no field a screen could render as one")
                .isFalse();
        assertThat(digest.has("total")).isFalse();
    }

    @Test
    void theWeeklyDigestNamesNobodyAndIdentifiesNobody() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String job = anEngagementWithAndrei(company.andrei(), conversation);
        for (int afternoon = 1; afternoon <= 5; afternoon++) {
            aPieceOfWorkAskedForAndDelivered(andrei, company.andrei(), job, conversation, "TEXT");
        }

        ResponseEntity<String> weekly = browser.get("/api/discovery/digest");
        assertThat(weekly.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = weekly.getBody();
        assertThat(body).isNotBlank();

        for (UUID person :
                List.of(company.maria(), company.ionut(), company.ioana(), company.andrei(), company.elena())) {
            assertThat(body)
                    .as("no person identifier reaches this response, in any field, present or future")
                    .doesNotContain(person.toString());
        }

        for (String displayName : jdbc.queryForList("select display_name from auth_user", String.class)) {
            assertThat(body)
                    .as("and no person's name either — %s did the work, and the week describes it by role", displayName)
                    .doesNotContain(displayName);
        }
    }

    private JsonNode theWeek() throws Exception {
        ResponseEntity<String> weekly = browser.get("/api/discovery/digest");
        assertThat(weekly.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(weekly.getBody());
    }

    private Long rolesThatMarkedNothing() {
        return jdbc.queryForObject(
                """
                select count(*) from (
                    select r.id
                    from functional_role r
                             join workspace_membership m on m.functional_role_id = r.id and m.status = 'ACTIVE'
                             left join work_node w on w.creator_id = m.user_id
                    group by r.id
                    having count(w.id) = 0
                ) quiet
                """,
                Long.class);
    }
}
