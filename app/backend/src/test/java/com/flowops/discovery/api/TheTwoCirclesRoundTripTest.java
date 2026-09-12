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

@Tag("DISCOVERY-MARK-WORK-01")
@Tag("DISCOVERY-DELIVER-WORK-01")
class TheTwoCirclesRoundTripTest extends CompanyScenarioTest {
    private String conversationBetween(RoundTripClient who, UUID person) throws Exception {
        ResponseEntity<String> started = who.post("/api/conversations", "{\"personId\":\"%s\"}".formatted(person));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("id").asText();
    }

    private String groupCalled(RoundTripClient who, String name) throws Exception {
        ResponseEntity<String> started = who.post("/api/conversations/group", "{\"name\":\"%s\"}".formatted(name));
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

    private ResponseEntity<String> press(
            RoundTripClient who, String messageId, String jobId, String verb, UUID performer, String joining) {
        String body =
                """
                {"messageId":"%s","jobId":"%s","verb":"%s","performerId":%s,"workType":null,"joining":%s}
                """
                        .formatted(
                                messageId,
                                jobId,
                                verb,
                                performer == null ? "null" : "\"" + performer + "\"",
                                joining == null ? "null" : "\"" + joining + "\"");
        return who.post("/api/discovery/work", body);
    }

    private ResponseEntity<String> deliverable(RoundTripClient who, String messageId) {
        return who.get("/api/discovery/work/" + messageId + "/deliverable");
    }

    private ResponseEntity<String> deliver(RoundTripClient who, String messageId) {
        return who.post("/api/discovery/work/" + messageId + "/deliver", "{\"bracketId\":null}");
    }

    private ResponseEntity<String> preview(RoundTripClient who, String jobId, String conversation, UUID performer) {
        return who.get("/api/discovery/brackets/preview?jobId=%s&conversationId=%s&performerId=%s"
                .formatted(jobId, conversation, performer));
    }

    private long bracketsIn(String jobId) {
        return jdbc.queryForObject(
                "select count(*) from work_bracket where job_id = ?::uuid and is_boundary = false", Long.class, jobId);
    }

    private long unplacedNodesIn(String jobId) {
        return jdbc.queryForObject(
                "select count(*) from work_node where job_id = ?::uuid and bracket_id is null", Long.class, jobId);
    }

    private long nodesIn(String jobId) {
        return jdbc.queryForObject("select count(*) from work_node where job_id = ?::uuid", Long.class, jobId);
    }

    private long evidenceFor(String messageId) {
        return jdbc.queryForObject(
                "select count(*) from work_node_evidence where message_id = ?::uuid", Long.class, messageId);
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
    void onePressWritesTheNodeAndPlacesItSoNothingIsEverLeftUnplaced() throws Exception {
        Company company = buildTheCompany();
        String room = conversationBetween(browser, company.andrei());
        String brief = said(browser, room, "Aurora Coffee vrea un rebranding complet");
        String job = openJob(browser, brief, "Rebranding Aurora Coffee");
        String asked = said(browser, room, "Andrei, poti sa scrii postarile pentru Aurora?");

        ResponseEntity<String> pressed = press(browser, asked, job, "CREATE", company.andrei(), null);

        assertThat(pressed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode placed = json.readTree(pressed.getBody());
        assertThat(placed.get("joined").asBoolean())
                .as("nothing of Andrei's was open at that address, so this opened work")
                .isFalse();
        assertThat(placed.hasNonNull("bracketId"))
                .as("the one call answers with where the work went, which the first of two never could")
                .isTrue();

        assertThat(jdbc.queryForObject(
                        "select bracket_id::text from work_node where id = ?::uuid",
                        String.class,
                        placed.get("nodeId").asText()))
                .as("the node and its placement are one transaction, so the row is placed the moment it exists")
                .isEqualTo(placed.get("bracketId").asText());
        assertThat(unplacedNodesIn(job))
                .as("finding #10 - there is no longer a state in which work can be lost")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from work_node_evidence where work_node_id = ?::uuid",
                        Long.class,
                        placed.get("nodeId").asText()))
                .as("the evidence is written in the same press - a node nothing can explain is worse than none")
                .isEqualTo(1L);
    }

    @Test
    void addingToWorkThatIsAlreadyOpenJoinsItRatherThanOpeningASecond() throws Exception {
        Company company = buildTheCompany();
        String room = conversationBetween(browser, company.andrei());
        String job = openJob(browser, said(browser, room, "Aurora vrea un rebranding"), "Rebranding Aurora");
        assertThat(press(browser, said(browser, room, "Andrei, postarile?"), job, "CREATE", company.andrei(), null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> again =
                press(browser, said(browser, room, "si inca doua postari"), job, "ADD", company.andrei(), null);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(again.getBody()).get("joined").asBoolean())
                .as("joining is the default and opening is the exception")
                .isTrue();
        assertThat(bracketsIn(job))
                .as("one piece of work, however many times it is talked about")
                .isEqualTo(1L);
    }

    @Test
    void creatingWhereWorkIsAlreadyOpenIsRefusedAndNamesWhatIsInTheWay() throws Exception {
        Company company = buildTheCompany();
        String room = conversationBetween(browser, company.andrei());
        String job = openJob(browser, said(browser, room, "Aurora vrea un rebranding"), "Rebranding Aurora");
        assertThat(press(browser, said(browser, room, "Andrei, postarile?"), job, "CREATE", company.andrei(), null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> refused =
                press(browser, said(browser, room, "inca ceva"), job, "CREATE", company.andrei(), null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("THAT_WORK_IS_ALREADY_OPEN");
        assertThat(bracketsIn(job)).as("the refusal wrote no bracket").isEqualTo(1L);
        assertThat(unplacedNodesIn(job))
                .as("and it wrote no node either - the whole press is one transaction, so a refusal rolls it back")
                .isZero();
    }

    @Test
    void thePreviewStopsOfferingCreateTheMomentWorkIsOpenAtThatAddress() throws Exception {
        Company company = buildTheCompany();
        String room = conversationBetween(browser, company.andrei());
        String job = openJob(browser, said(browser, room, "Aurora vrea un rebranding"), "Rebranding Aurora");

        JsonNode before =
                json.readTree(preview(browser, job, room, company.andrei()).getBody());
        assertThat(before.get("verbs").toString()).contains("CREATE").doesNotContain("ADD");

        assertThat(press(browser, said(browser, room, "Andrei, postarile?"), job, "CREATE", company.andrei(), null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        JsonNode after =
                json.readTree(preview(browser, job, room, company.andrei()).getBody());

        assertThat(after.get("joins").asBoolean()).isTrue();
        assertThat(after.get("verbs").toString())
                .as("R1.1 would refuse a second bracket here, so the strip must not offer one")
                .contains("ADD")
                .doesNotContain("CREATE");
    }

    @Test
    void joiningSomebodysWorkOpensYourOwnBracketAndStoresThePress() throws Exception {
        Company company = buildTheCompany();
        String room = groupCalled(browser, "Campanie Aurora");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        assertThat(elena.post("/api/conversations/" + room + "/join", "").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        String job = openJob(browser, said(browser, room, "Aurora vrea un rebranding"), "Rebranding Aurora");
        JsonNode andreis = json.readTree(
                press(browser, said(browser, room, "Andrei, postarile?"), job, "CREATE", company.andrei(), null)
                        .getBody());

        ResponseEntity<String> joined = press(
                elena,
                said(elena, room, "ma bag si eu pe postari"),
                job,
                "JOIN",
                null,
                andreis.get("bracketId").asText());

        assertThat(joined.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode elenas = json.readTree(joined.getBody());
        assertThat(elenas.get("bracketId").asText())
                .as("R19.5 - a bracket never has two performers, so joining opens her own")
                .isNotEqualTo(andreis.get("bracketId").asText());
        assertThat(elenas.get("workType").asText())
                .as("the work type is inherited, which is what makes the two legible as one piece of work")
                .isEqualTo(andreis.get("workType").asText());
        assertThat(elenas.get("joinedWith").asText())
                .isEqualTo(andreis.get("bracketId").asText());

        assertThat(jdbc.queryForObject(
                        """
                        select count(*) from bracket_join_intent
                        where joiner_bracket_id = ?::uuid and joined_bracket_id = ?::uuid
                        """,
                        Long.class,
                        elenas.get("bracketId").asText(),
                        andreis.get("bracketId").asText()))
                .as("the button is the only reliable signal, so the button is what is stored")
                .isEqualTo(1L);
    }

    @Test
    void theRedCircleDeliversTheOneThingTheHolderMayCloseAndNeverTheBoundary() throws Exception {
        Company company = buildTheCompany();
        String room = conversationBetween(browser, company.andrei());
        String job = openJob(browser, said(browser, room, "Aurora vrea un rebranding"), "Rebranding Aurora");
        JsonNode work = json.readTree(
                press(browser, said(browser, room, "Andrei, postarile?"), job, "CREATE", company.andrei(), null)
                        .getBody());

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String delivery = said(andrei, room, "gata, postarile sunt aici");

        JsonNode hers = json.readTree(deliverable(browser, delivery).getBody());
        assertThat(hers.get("mine"))
                .as("R4a.1 - she holds the boundary and it is not on offer")
                .isEmpty();
        assertThat(hers.get("heldByOthers").toString())
                .as("the refusal names who holds the work rather than restating a rule")
                .contains("Andrei");

        ResponseEntity<String> delivered = deliver(andrei, delivery);

        assertThat(delivered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(delivered.getBody()).get("bracketId").asText())
                .isEqualTo(work.get("bracketId").asText());
        assertThat(jdbc.queryForObject(
                        "select close_kind || ':' || output_kind || ':' || output_value from work_bracket"
                                + " where id = ?::uuid",
                        String.class,
                        work.get("bracketId").asText()))
                .as("DELIVERED, and this message is the output")
                .isEqualTo("DELIVERED:MESSAGE_REF:" + delivery);
    }

    @Test
    void deliveringWorkThatIsSomebodyElsesIsRefusedAndNamesWhoHoldsIt() throws Exception {
        Company company = buildTheCompany();
        String room = conversationBetween(browser, company.andrei());
        String job = openJob(browser, said(browser, room, "Aurora vrea un rebranding"), "Rebranding Aurora");
        assertThat(press(browser, said(browser, room, "Andrei, postarile?"), job, "CREATE", company.andrei(), null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> refused = deliver(browser, said(browser, room, "e gata?"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode error = json.readTree(refused.getBody());
        assertThat(error.get("code").asText()).isEqualTo("NOTHING_OF_YOURS_IS_OPEN_HERE");
        assertThat(error.get("message").asText())
                .as("R21.2 - open but not yours is refused naming who holds it")
                .contains("Andrei");
        assertThat(jdbc.queryForObject(
                        "select count(*) from work_bracket where job_id = ?::uuid and close_kind is not null",
                        Long.class,
                        job))
                .as("a refusal closes nothing")
                .isZero();
    }

    @Test
    void whatTheRedCircleOffersAlreadyKnowsWhoItWouldUnblock() throws Exception {
        Company company = buildTheCompany();
        String room = groupCalled(browser, "Campanie Aurora");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(elena.post("/api/conversations/" + room + "/join", "").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(andrei.post("/api/conversations/" + room + "/join", "").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        String job = openJob(browser, said(browser, room, "Aurora vrea un rebranding"), "Rebranding Aurora");
        JsonNode andreis = json.readTree(
                press(browser, said(browser, room, "Andrei, postarile?"), job, "CREATE", company.andrei(), null)
                        .getBody());
        JsonNode elenas = json.readTree(
                press(browser, said(browser, room, "Elena, layout-ul?"), job, "CREATE", company.elena(), null)
                        .getBody());

        assertThat(elena.post(
                                "/api/discovery/brackets/"
                                        + elenas.get("bracketId").asText() + "/wait",
                                """
                                {"kind":"COLLEAGUE","onBracketId":"%s","reason":"astept postarile",
                                 "expectedBy":null}
                                """
                                        .formatted(andreis.get("bracketId").asText()))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode his = json.readTree(
                deliverable(andrei, said(andrei, room, "gata postarile")).getBody());

        assertThat(his.get("mine")).hasSize(1);
        assertThat(his.get("mine").get(0).get("waitingHolderNames").toString())
                .as("R21.2 - the confirmation says 'this unblocks Elena' rather than showing a count")
                .contains("Elena");
    }

    @Test
    void allThreeCirclesRefuseACallerWhoseRoleNoLongerHoldsWorkNodeMark() throws Exception {
        Company company = buildTheCompany();
        String room = conversationBetween(browser, company.andrei());
        String job = openJob(browser, said(browser, room, "Aurora vrea un rebranding"), "Rebranding Aurora");
        String asked = said(browser, room, "Andrei, postarile?");
        long nodesBefore = nodesIn(job);
        assertThat(nodesBefore)
                .as("the engagement's own opening node is here, so what follows is not a sweep over nothing")
                .isEqualTo(1L);

        assertThat(nobodyMayMarkWorkAnyMore())
                .as("V65's three grants must actually have gone, or a 403 below would mean something else")
                .isEqualTo(3);
        try {
            ResponseEntity<String> pressed = press(browser, asked, job, "CREATE", company.andrei(), null);
            ResponseEntity<String> offered = deliverable(browser, asked);
            ResponseEntity<String> delivered = deliver(browser, asked);

            assertThat(pressed.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(offered.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(delivered.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(json.readTree(pressed.getBody()).get("code").asText())
                    .as("a refusal that reads as a fault teaches the person to retry something that cannot work")
                    .isEqualTo("NOT_PERMITTED");
            assertThat(json.readTree(offered.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
            assertThat(json.readTree(delivered.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");

            assertThat(nodesIn(job))
                    .as("a press that never passed the door wrote nothing behind it")
                    .isEqualTo(nodesBefore);
            assertThat(evidenceFor(asked)).isZero();
        } finally {
            everybodyMayMarkWorkAgain();
        }
    }

    @Test
    void theGreyCircleRefusesABodyItCannotUseAndWritesNothingEitherWay() throws Exception {
        Company company = buildTheCompany();
        String room = conversationBetween(browser, company.andrei());
        String job = openJob(browser, said(browser, room, "Aurora vrea un rebranding"), "Rebranding Aurora");
        String asked = said(browser, room, "Andrei, postarile?");
        long nodesBefore = nodesIn(job);
        assertThat(nodesBefore)
                .as("there is a node to be lost, so an unchanged count afterwards is evidence rather than a vacuum")
                .isEqualTo(1L);

        ResponseEntity<String> namesNoMessage =
                browser.post("/api/discovery/work", "{\"jobId\":\"%s\",\"verb\":\"CREATE\"}".formatted(job));
        ResponseEntity<String> notJsonAtAll = browser.post("/api/discovery/work", "{\"messageId\":");

        assertThat(namesNoMessage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(namesNoMessage.getBody()).get("code").asText()).isEqualTo("REQUEST_INVALID");
        assertThat(json.readTree(namesNoMessage.getBody()).get("details").toString())
                .as("field by field, so a page can mark exactly what to fix")
                .contains("messageId");

        assertThat(notJsonAtAll.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(notJsonAtAll.getBody()).get("code").asText())
                .as("400 rather than 500 - a body the caller sent is not the server breaking")
                .isEqualTo("REQUEST_MALFORMED");

        assertThat(nodesIn(job)).isEqualTo(nodesBefore);
        assertThat(evidenceFor(asked)).isZero();
    }

    @Test
    void theRedCircleRefusesAnIdentifierThatIsNotOneAndClosesNothingWhileDoingIt() throws Exception {
        Company company = buildTheCompany();
        String room = conversationBetween(browser, company.andrei());
        String job = openJob(browser, said(browser, room, "Aurora vrea un rebranding"), "Rebranding Aurora");
        String asked = said(browser, room, "Andrei, postarile?");
        assertThat(press(browser, asked, job, "CREATE", company.andrei(), null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(bracketsIn(job))
                .as("there is live work here to be wrongly closed, which is what makes the count below mean anything")
                .isEqualTo(1L);

        ResponseEntity<String> notAnIdentifier = browser.get("/api/discovery/work/not-a-uuid/deliverable");
        ResponseEntity<String> notABracket =
                browser.post("/api/discovery/work/" + asked + "/deliver", "{\"bracketId\":\"not-a-uuid\"}");

        assertThat(notAnIdentifier.getStatusCode())
                .as("400 rather than 500 - the caller sent that value and the caller can fix it")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(notAnIdentifier.getBody()).get("code").asText())
                .as("DISCOVERY's own advice claims the conversion's cause, so it is REFUSED rather than "
                        + "REQUEST_INVALID - see this method's note")
                .isEqualTo("REFUSED");
        assertThat(notABracket.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(notABracket.getBody()).get("code").asText()).isEqualTo("REQUEST_MALFORMED");

        assertThat(jdbc.queryForObject(
                        "select count(*) from work_bracket where job_id = ?::uuid and close_kind is not null",
                        Long.class,
                        job))
                .as("a refusal closes nothing")
                .isZero();
    }

    @Test
    void aRefusedPressRollsItsNodeBackRatherThanLeavingOneBehindWithNoBracket() throws Exception {
        Company company = buildTheCompany();
        String room = conversationBetween(browser, company.andrei());
        String job = openJob(browser, said(browser, room, "Aurora vrea un rebranding"), "Rebranding Aurora");
        assertThat(press(browser, said(browser, room, "Andrei, postarile?"), job, "CREATE", company.andrei(), null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        long nodesBefore = nodesIn(job);
        assertThat(nodesBefore)
                .as("the opening node and Andrei's posts, so the comparison below is against something")
                .isEqualTo(2L);

        String collides = said(browser, room, "si inca ceva despre postari");
        ResponseEntity<String> refused = press(browser, collides, job, "CREATE", company.andrei(), null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("THAT_WORK_IS_ALREADY_OPEN");
        assertThat(evidenceFor(collides))
                .as("the node written before the refusal is gone, not merely unplaced")
                .isZero();
        assertThat(nodesIn(job))
                .as("the engagement holds exactly what it held before the press")
                .isEqualTo(nodesBefore);
        assertThat(unplacedNodesIn(job)).isZero();
        assertThat(bracketsIn(job))
                .as("and no second bracket opened at that address")
                .isEqualTo(1L);
    }
}
