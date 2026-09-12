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

@Tag("DISCOVERY-VIEW-CANVAS-01")
class TheCanvasDrawsThreadsAndNeverPeopleTest extends CompanyScenarioTest {
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

    private JsonNode openJob(RoundTripClient who, String messageId, String name) throws Exception {
        ResponseEntity<String> opened =
                who.post("/api/discovery/jobs", "{\"messageId\":\"%s\",\"name\":\"%s\"}".formatted(messageId, name));
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(opened.getBody());
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

    private ResponseEntity<String> canvasOf(RoundTripClient who, String jobId) {
        return who.get("/api/discovery/canvas?jobId=" + jobId);
    }

    private record TwoThreads(
            String jobId,
            String openingThread,
            String contentThread,
            String designThread,
            String moodboardNode,
            String contentConversation,
            String moodboardMessage) {}

    private TwoThreads anEngagementWithTwoThreads(Company company) throws Exception {
        String withAndrei = conversationBetween(browser, company.andrei());
        String withElena = conversationBetween(browser, company.elena());
        String brief = said(browser, withAndrei, "Aurora Coffee vrea un rebranding complet");
        JsonNode opened = openJob(browser, brief, "Rebranding Aurora Coffee");
        String job = opened.get("jobId").asText();

        String askedAndrei = said(browser, withAndrei, "Andrei, poti sa faci moodboard-ul pana joi?");
        JsonNode moodboard = mark(browser, askedAndrei, job, "REQUEST", company.andrei());

        String askedElena = said(browser, withElena, "Elena, ne trebuie si artwork-ul pentru Aurora");
        JsonNode artwork = mark(browser, askedElena, job, "REQUEST", company.elena());

        return new TwoThreads(
                job,
                opened.get("trackId").asText(),
                moodboard.get("trackId").asText(),
                artwork.get("trackId").asText(),
                moodboard.get("nodeId").asText(),
                withAndrei,
                askedAndrei);
    }

    @Test
    void anEngagementWithTwoThreadsDrawsOneLanePerThreadLabelledByRolePairs() throws Exception {
        Company company = buildTheCompany();
        TwoThreads work = anEngagementWithTwoThreads(company);

        ResponseEntity<String> drawn = canvasOf(browser, work.jobId());

        assertThat(drawn.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode canvas = json.readTree(drawn.getBody());
        assertThat(canvas.get("jobId").asText()).isEqualTo(work.jobId());
        assertThat(canvas.get("jobName").asText()).isEqualTo("Rebranding Aurora Coffee");

        JsonNode lanes = canvas.get("lanes");
        List<String> laneIds = new ArrayList<>();
        lanes.forEach(lane -> laneIds.add(lane.get("trackId").asText()));
        assertThat(laneIds)
                .as("one lane per thread, and exactly the threads this engagement produced")
                .containsExactlyInAnyOrder(work.openingThread(), work.contentThread(), work.designThread());

        JsonNode content = laneWith(lanes, work.contentThread());
        assertThat(content.get("fromRoleName").asText())
                .as("lanes are role pairs; the header names what people do, never who they are")
                .isEqualTo(AGENCY_OWNER);
        assertThat(content.get("toRoleName").asText()).isEqualTo(CONTENT_WRITER);
        assertThat(content.get("weaklyKeyed").asBoolean())
                .as("both parties have stated jobs, so this thread is keyed on the intended rung")
                .isFalse();

        JsonNode design = laneWith(lanes, work.designThread());
        assertThat(design.get("toRoleName").asText()).isEqualTo(DESIGNER);
    }

    @Test
    void theSerialisedCanvasContainsNoPersonIdentifierAndNoPersonName() throws Exception {
        Company company = buildTheCompany();
        TwoThreads work = anEngagementWithTwoThreads(company);

        String body = canvasOf(browser, work.jobId()).getBody();

        assertThat(body)
                .as("the work is drawn — this is a canvas with content, not an empty response passing by default")
                .contains("moodboard-ul")
                .contains(CONTENT_WRITER)
                .contains(DESIGNER);

        for (UUID person :
                List.of(company.maria(), company.ionut(), company.ioana(), company.andrei(), company.elena())) {
            assertThat(body)
                    .as("no person identifier reaches the canvas — invariant I5, enforced in the query")
                    .doesNotContain(person.toString());
        }
        for (String name : List.of("Maria Ionescu", "Ionuț Petrescu", "Ioana Radu", "Andrei Munteanu", "Elena Dobre")) {
            assertThat(body)
                    .as("nor does a person's name — a lane labelled with one is a performance dashboard")
                    .doesNotContain(name);
        }
        for (String address : List.of("maria@atelier.ro", "andrei@atelier.ro", "elena@atelier.ro")) {
            assertThat(body).doesNotContain(address);
        }
    }

    @Test
    void aCardCarriesItsPhasesAsRowsAndTheResponseHasNoTotalField() throws Exception {
        Company company = buildTheCompany();
        TwoThreads work = anEngagementWithTwoThreads(company);
        twoFinishedPhasesOn(work.moodboardNode());

        JsonNode canvas = json.readTree(canvasOf(browser, work.jobId()).getBody());
        JsonNode card = cardWith(laneWith(canvas.get("lanes"), work.contentThread()), work.moodboardNode());

        JsonNode phases = card.get("phases");
        assertThat(phases.size())
                .as("two stretches, two rows — never one number")
                .isEqualTo(2);
        assertThat(phases.get(0).get("phase").asText()).isEqualTo("WORK");
        assertThat(phases.get(0).get("ms").asLong()).isEqualTo(7_200_000L);
        assertThat(phases.get(1).get("phase").asText())
                .as("the client's silence is a phase of its own, which is what makes I4 computable")
                .isEqualTo("EXTERNAL_WAIT");
        assertThat(phases.get(1).get("ms").asLong()).isEqualTo(3_600_000L);

        assertThat(fieldsOf(card))
                .as("invariant I9: a duration is never presented without its phase, so there is no total")
                .containsExactlyInAnyOrder(
                        "nodeId",
                        "title",
                        "kind",
                        "direction",
                        "outputType",
                        "templated",
                        "phases",
                        "conversationId",
                        "messageId");
        assertThat(fieldsOf(phases.get(0))).containsExactlyInAnyOrder("phase", "ms");
        assertThat(canvasOf(browser, work.jobId()).getBody())
                .as("and no field anywhere on the response is named like one")
                .doesNotContain("totalMs")
                .doesNotContain("totalDuration");
    }

    @Test
    void aCardCarriesTheConversationAndMessageOfItsPrimaryEvidenceAndNeitherNamesAPerson() throws Exception {
        Company company = buildTheCompany();
        TwoThreads work = anEngagementWithTwoThreads(company);

        JsonNode canvas = json.readTree(canvasOf(browser, work.jobId()).getBody());
        JsonNode card = cardWith(laneWith(canvas.get("lanes"), work.contentThread()), work.moodboardNode());

        assertThat(card.get("title").asText())
                .as("the words are still copied onto the card — the address is beside them, not instead of them")
                .isEqualTo("Andrei, poti sa faci moodboard-ul pana joi?");
        assertThat(card.get("messageId").asText())
                .as("the sentence somebody clicked, reached through the node's PRIMARY evidence")
                .isEqualTo(work.moodboardMessage());
        assertThat(card.get("conversationId").asText())
                .as("and the thread it was said in, so the card has somewhere to send a reader")
                .isEqualTo(work.contentConversation());

        for (UUID person :
                List.of(company.maria(), company.ionut(), company.ioana(), company.andrei(), company.elena())) {
            assertThat(List.of(
                            card.get("conversationId").asText(),
                            card.get("messageId").asText()))
                    .as("an identifier is not a figure keyed to a person, and neither of these is a person")
                    .doesNotContain(person.toString());
        }
    }

    @Test
    void aUnitOfWorkWithNoPrimaryEvidenceIsStillDrawnAndCarriesNeitherIdentifier() throws Exception {
        Company company = buildTheCompany();
        TwoThreads work = anEngagementWithTwoThreads(company);

        jdbc.update("delete from work_node_evidence where work_node_id = ?::uuid", work.moodboardNode());

        JsonNode canvas = json.readTree(canvasOf(browser, work.jobId()).getBody());
        JsonNode card = cardWith(laneWith(canvas.get("lanes"), work.contentThread()), work.moodboardNode());

        assertThat(card.get("title").asText())
                .as("the card is still on the canvas — the words were copied, not read back through the message")
                .isEqualTo("Andrei, poti sa faci moodboard-ul pana joi?");
        assertThat(card.hasNonNull("conversationId")).isFalse();
        assertThat(card.hasNonNull("messageId")).isFalse();
    }

    @Test
    void aThreadFromAnotherEngagementNeverAppearsOnThisCanvas() throws Exception {
        Company company = buildTheCompany();
        TwoThreads aurora = anEngagementWithTwoThreads(company);

        String withAndrei = conversationBetween(browser, company.andrei());
        String otherBrief = said(browser, withAndrei, "Northwind vor o campanie de toamna");
        String otherJob =
                openJob(browser, otherBrief, "Campanie Northwind").get("jobId").asText();
        String otherAsk = said(browser, withAndrei, "Andrei, textele pentru Northwind pana vineri?");
        JsonNode otherWork = mark(browser, otherAsk, otherJob, "REQUEST", company.andrei());
        String otherThread = otherWork.get("trackId").asText();

        assertThat(otherThread)
                .as("a different engagement is a different thread even for the same pair of people")
                .isNotEqualTo(aurora.contentThread());

        JsonNode canvas = json.readTree(canvasOf(browser, aurora.jobId()).getBody());

        List<String> laneIds = new ArrayList<>();
        List<String> nodeIds = new ArrayList<>();
        canvas.get("lanes").forEach(lane -> {
            laneIds.add(lane.get("trackId").asText());
            lane.get("cards").forEach(card -> nodeIds.add(card.get("nodeId").asText()));
        });

        assertThat(laneIds)
                .as("I2: no lane crosses an engagement")
                .doesNotContain(otherThread)
                .containsExactlyInAnyOrder(aurora.openingThread(), aurora.contentThread(), aurora.designThread());
        assertThat(nodeIds)
                .as("I1 and I2 together: nor does a card wander in from the other client's work")
                .doesNotContain(otherWork.get("nodeId").asText());
        assertThat(canvasOf(browser, aurora.jobId()).getBody()).doesNotContain("Northwind");
    }

    @Test
    void aClosedLaneCarriesItsFrozenCompletenessAndAnOpenOneCarriesNone() throws Exception {
        Company company = buildTheCompany();
        TwoThreads work = anEngagementWithTwoThreads(company);

        assertThat(browser.post("/api/discovery/tracks/" + work.contentThread() + "/end", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode lanes =
                json.readTree(canvasOf(browser, work.jobId()).getBody()).get("lanes");

        JsonNode ended = laneWith(lanes, work.contentThread());
        assertThat(ended.get("state").asText()).isEqualTo("CLOSED");
        assertThat(ended.get("closeReason").asText()).isEqualTo("TERMINAL_OUTPUT");
        assertThat(ended.get("completeness").asText())
                .as("a thread holding only its opening node is a beginning and nothing else")
                .isEqualTo("START_ONLY");

        JsonNode running = laneWith(lanes, work.designThread());
        assertThat(running.get("state").asText()).isNotEqualTo("CLOSED");
        assertThat(running.hasNonNull("completeness"))
                .as("frozen at close and never before it — a running thread has no capture quality yet")
                .isFalse();
        assertThat(running.hasNonNull("closeReason")).isFalse();
    }

    @Test
    void anEmployeeIsRefusedTheCanvas() throws Exception {
        Company company = buildTheCompany();
        TwoThreads work = anEngagementWithTwoThreads(company);
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> refused = canvasOf(andrei, work.jobId());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText())
                .as("pinned to the code as well as the status: a refusal thrown past the advice answers 500")
                .isEqualTo("NOT_PERMITTED");

        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        assertThat(canvasOf(ionut, work.jobId()).getStatusCode())
                .as("and a manager is not refused — the split is between roles, not a blanket closure")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void anUnknownEngagementIsRefusedWhileOneNobodyHasWorkedInYetIsDrawnAsItStands() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        JsonNode opened = openJob(browser, brief, "Rebranding Aurora Coffee");
        String job = opened.get("jobId").asText();

        ResponseEntity<String> refused = canvasOf(browser, UUID.randomUUID().toString());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode error = json.readTree(refused.getBody());
        assertThat(error.get("code").asText()).isEqualTo("UNKNOWN_JOB");
        assertThat(error.get("details").get(0).get("field").asText()).isEqualTo("jobId");

        JsonNode fresh = json.readTree(canvasOf(browser, job).getBody());
        assertThat(fresh.get("jobName").asText()).isEqualTo("Rebranding Aurora Coffee");
        assertThat(fresh.get("lanes").size())
                .as("only the opening boundary has happened, so only its lane is drawn")
                .isEqualTo(1);
        JsonNode boundary = fresh.get("lanes").get(0);
        assertThat(boundary.get("trackId").asText())
                .isEqualTo(opened.get("trackId").asText());
        assertThat(boundary.get("cards").get(0).get("kind").asText()).isEqualTo("JOB_START");
    }

    private void twoFinishedPhasesOn(String nodeId) {
        jdbc.update(
                """
                insert into node_phase_row (id, work_node_id, phase, started_at, ended_at, waiting_on)
                values (?, ?::uuid, 'WORK', now() - interval '4 hours', now() - interval '2 hours', null)
                """,
                UUID.randomUUID(),
                nodeId);
        jdbc.update(
                """
                insert into node_phase_row (id, work_node_id, phase, started_at, ended_at, waiting_on)
                values (?, ?::uuid, 'EXTERNAL_WAIT', now() - interval '2 hours', now() - interval '1 hour', 'CLIENT')
                """,
                UUID.randomUUID(),
                nodeId);
    }

    private JsonNode laneWith(JsonNode lanes, String trackId) {
        for (JsonNode lane : lanes) {
            if (lane.get("trackId").asText().equals(trackId)) {
                return lane;
            }
        }
        throw new AssertionError("no lane for thread " + trackId);
    }

    private JsonNode cardWith(JsonNode lane, String nodeId) {
        for (JsonNode card : lane.get("cards")) {
            if (card.get("nodeId").asText().equals(nodeId)) {
                return card;
            }
        }
        throw new AssertionError("no card for unit of work " + nodeId);
    }

    private List<String> fieldsOf(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
