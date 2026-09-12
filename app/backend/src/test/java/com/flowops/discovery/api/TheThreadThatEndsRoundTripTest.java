package com.flowops.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-END-THREAD-01")
@Tag("DISCOVERY-PAIR-NODES-01")
@Tag("DISCOVERY-ASSIGN-ORPHAN-01")
class TheThreadThatEndsRoundTripTest extends CompanyScenarioTest {
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

    private JsonNode produced(RoundTripClient who, String nodeId, String outputType) throws Exception {
        ResponseEntity<String> recorded =
                who.post("/api/discovery/nodes/" + nodeId + "/output", "{\"outputType\":\"%s\"}".formatted(outputType));
        assertThat(recorded.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(recorded.getBody());
    }

    private ResponseEntity<String> endThread(RoundTripClient who, String trackId) {
        return who.post("/api/discovery/tracks/" + trackId + "/end", null);
    }

    @Test
    void aRequestAndItsCompletionPairAtFourOfFourAndTheThreadClosesComplete() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();

        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        JsonNode request = mark(browser, asked, job, "REQUEST", company.andrei());
        String thread = request.get("trackId").asText();

        String finished = said(andrei, conversation, "Gata moodboard-ul, e in drive");
        JsonNode completion = mark(browser, finished, job, "COMPLETION", null);
        assertThat(completion.get("trackId").asText())
                .as("a completion is keyed on its performer, so it joins the thread of the request it answers")
                .isEqualTo(thread);

        JsonNode recorded = produced(andrei, completion.get("nodeId").asText(), "TEXT");

        assertThat(recorded.hasNonNull("pairedWith"))
                .as("four of four links silently, and the person is asked nothing")
                .isTrue();
        assertThat(recorded.get("pairedWith").asText())
                .as("and it is the request it answers, not merely some request")
                .isEqualTo(request.get("nodeId").asText());

        ResponseEntity<String> ended = endThread(browser, thread);

        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(ended.getBody());
        assertThat(body.get("completeness").asText()).isEqualTo("COMPLETE");
        assertThat(body.get("closeReason").asText()).isEqualTo("TERMINAL_OUTPUT");

        assertThat(jdbc.queryForObject("select state from track where id = ?::uuid", String.class, thread))
                .isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("select close_reason from track where id = ?::uuid", String.class, thread))
                .isEqualTo("TERMINAL_OUTPUT");
        assertThat(jdbc.queryForObject("select completeness from track where id = ?::uuid", String.class, thread))
                .as("the row is what the clusterer reads, and it agrees with what Maria was shown")
                .isEqualTo("COMPLETE");
        assertThat(jdbc.queryForObject("select closed_at from track where id = ?::uuid", Timestamp.class, thread))
                .isNotNull();
    }

    @Test
    void aCompletionThatNamedNoOutputIsLeftUnpairedAndTheThreadClosesPartial() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();

        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        String thread = mark(browser, asked, job, "REQUEST", company.andrei())
                .get("trackId")
                .asText();

        String replied = said(andrei, conversation, "Da, ma ocup");
        JsonNode completion = mark(browser, replied, job, "COMPLETION", null);
        JsonNode recorded = produced(andrei, completion.get("nodeId").asText(), "NONE");

        assertThat(recorded.hasNonNull("pairedWith"))
                .as("three of four asks rather than links, and asking begins with not linking")
                .isFalse();

        ResponseEntity<String> ended = endThread(browser, thread);

        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(ended.getBody()).get("completeness").asText())
                .as("something happened in the thread and its ending was never recorded")
                .isEqualTo("PARTIAL");
        assertThat(jdbc.queryForObject("select completeness from track where id = ?::uuid", String.class, thread))
                .isEqualTo("PARTIAL");
    }

    @Test
    void aCompletionByADifferentPerformerDoesNotPairWithSomebodyElsesRequest() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        String withAndrei = conversationBetween(browser, company.andrei());
        String withElena = conversationBetween(browser, company.elena());
        String brief = said(browser, withAndrei, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();

        String asked = said(browser, withAndrei, "Andrei, poti sa faci moodboard-ul pana joi?");
        String andreisThread = mark(browser, asked, job, "REQUEST", company.andrei())
                .get("trackId")
                .asText();

        String elenaFinished = said(elena, withElena, "Am terminat");
        JsonNode elenasCompletion = mark(browser, elenaFinished, job, "COMPLETION", null);
        JsonNode recorded = produced(elena, elenasCompletion.get("nodeId").asText(), "DESIGN");

        assertThat(elenasCompletion.get("trackId").asText())
                .as("Elena's work is Elena's thread; it never joins the one Andrei was asked in")
                .isNotEqualTo(andreisThread);
        assertThat(recorded.hasNonNull("pairedWith"))
                .as("there is no open request in Elena's own thread, and Maria's is behind a wall")
                .isFalse();

        ResponseEntity<String> ended = endThread(browser, andreisThread);

        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(ended.getBody()).get("completeness").asText())
                .as("a request nobody answered is a beginning and nothing else")
                .isEqualTo("START_ONLY");
        assertThat(jdbc.queryForObject(
                        "select completeness from track where id = ?::uuid", String.class, andreisThread))
                .isEqualTo("START_ONLY");
    }

    @Test
    void theCompletionPairsWithTheMostRecentOpenRequestAndNotTheOlderOne() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();

        String first = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        String second = said(browser, conversation, "Si variantele de logo, tot pentru joi");
        JsonNode moodboard = mark(browser, first, job, "REQUEST", company.andrei());
        JsonNode variants = mark(browser, second, job, "REQUEST", company.andrei());
        assertThat(variants.get("trackId").asText())
                .as("two pieces of work by the same pair belong together, which is what makes this case exist")
                .isEqualTo(moodboard.get("trackId").asText());

        String finished = said(andrei, conversation, "Variantele de logo sunt gata");
        JsonNode completion = mark(browser, finished, job, "COMPLETION", null);
        JsonNode recorded = produced(andrei, completion.get("nodeId").asText(), "DESIGN");

        assertThat(recorded.get("pairedWith").asText())
                .as("four of four beats three of four, and three of four is not paired at all")
                .isEqualTo(variants.get("nodeId").asText());
        assertThat(recorded.get("pairedWith").asText())
                .isNotEqualTo(moodboard.get("nodeId").asText());
    }

    @Test
    void endingAThreadTwiceIsRefusedAndTheFrozenCompletenessStands() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();
        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        String thread = mark(browser, asked, job, "REQUEST", company.andrei())
                .get("trackId")
                .asText();

        assertThat(json.readTree(endThread(browser, thread).getBody())
                        .get("completeness")
                        .asText())
                .as("a thread holding only its opening node is a beginning and nothing else")
                .isEqualTo("START_ONLY");
        Timestamp closedAt =
                jdbc.queryForObject("select closed_at from track where id = ?::uuid", Timestamp.class, thread);

        ResponseEntity<String> again = endThread(browser, thread);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(again.getBody()).get("code").asText()).isEqualTo("TRACK_ALREADY_CLOSED");
        assertThat(jdbc.queryForObject("select completeness from track where id = ?::uuid", String.class, thread))
                .as("frozen at the close and never recomputed — invariant I12")
                .isEqualTo("START_ONLY");
        assertThat(jdbc.queryForObject("select closed_at from track where id = ?::uuid", Timestamp.class, thread))
                .as("and the thread ended once, at one moment, because ordering is inferred from closure")
                .isEqualTo(closedAt);
    }

    @Test
    void anUnknownThreadIsRefusedRatherThanConjured() throws Exception {
        buildTheCompany();
        Long threadsBefore = jdbc.queryForObject("select count(*) from track", Long.class);

        ResponseEntity<String> refused = endThread(browser, UUID.randomUUID().toString());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode error = json.readTree(refused.getBody());
        assertThat(error.get("code").asText()).isEqualTo("UNKNOWN_TRACK");
        assertThat(error.get("details").get(0).get("field").asText()).isEqualTo("trackId");
        assertThat(jdbc.queryForObject("select count(*) from track", Long.class))
                .as("no thread was conjured to be closed")
                .isEqualTo(threadsBefore);
    }

    @Test
    void anOrphanAppearsInTheQueueAndLeavesItWhenPlaced() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();
        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        String thread = mark(browser, asked, job, "REQUEST", company.andrei())
                .get("trackId")
                .asText();

        String stray = said(browser, conversation, "Si inca ceva pentru Aurora, nu stiu inca al cui e");
        String strandedNode =
                mark(browser, stray, job, "STANDALONE", null).get("nodeId").asText();

        assertThat(jdbc.update("update work_node set track_id = null where id = ?::uuid", strandedNode))
                .isEqualTo(1);

        ResponseEntity<String> queue = browser.get("/api/discovery/orphans");

        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode rows = json.readTree(queue.getBody());
        assertThat(rows.size()).isEqualTo(1);
        JsonNode row = rows.get(0);
        assertThat(row.get("nodeId").asText()).isEqualTo(strandedNode);
        assertThat(row.get("jobId").asText()).isEqualTo(job);
        assertThat(row.get("jobName").asText()).isEqualTo("Rebranding Aurora Coffee");
        assertThat(row.get("saidBy").asText())
                .as("whose sentence it was, which is what makes it placeable at all")
                .isEqualTo(company.maria().toString());

        List<String> fields = new ArrayList<>();
        row.fieldNames().forEachRemaining(fields::add);
        assertThat(fields)
                .as("invariant I5: no count, no duration and no average keyed to a person — asserted by absence")
                .containsExactlyInAnyOrder("nodeId", "jobId", "jobName", "text", "direction", "markedAt", "saidBy");

        ResponseEntity<String> placed = browser.patch(
                "/api/discovery/nodes/" + strandedNode + "/track", "{\"trackId\":\"%s\"}".formatted(thread));

        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select track_id from work_node where id = ?::uuid", String.class, strandedNode))
                .isEqualTo(thread);
        assertThat(json.readTree(browser.get("/api/discovery/orphans").getBody())
                        .size())
                .as("a queue that never shrinks is a queue people stop opening")
                .isZero();

        ResponseEntity<String> twice = browser.patch(
                "/api/discovery/nodes/" + strandedNode + "/track", "{\"trackId\":\"%s\"}".formatted(thread));
        assertThat(twice.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(twice.getBody()).get("code").asText()).isEqualTo("NODE_IS_NOT_ORPHAN");
    }

    @Test
    void anEmployeeIsRefusedTheOrphanQueueAndItsWriteHalfAlike() throws Exception {
        buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> readingIt = andrei.get("/api/discovery/orphans");
        ResponseEntity<String> writingIt = andrei.patch(
                "/api/discovery/nodes/" + UUID.randomUUID() + "/track",
                "{\"trackId\":\"%s\"}".formatted(UUID.randomUUID()));

        assertThat(readingIt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(readingIt.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
        assertThat(writingIt.getStatusCode())
                .as("the write half is the manager's too, and it is refused before the node is even looked for")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(writingIt.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
    }
}
