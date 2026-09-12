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

@Tag("DISCOVERY-OPEN-JOB-01")
@Tag("DISCOVERY-MARK-MESSAGE-01")
class DiscoveryCaptureRoundTripTest extends CompanyScenarioTest {
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

    private ResponseEntity<String> mark(
            RoundTripClient who, String messageId, String jobId, String direction, UUID performer) {
        String body = performer == null
                ? "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":null}"
                        .formatted(messageId, jobId, direction)
                : "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":\"%s\"}"
                        .formatted(messageId, jobId, direction, performer);
        return who.post("/api/discovery/nodes", body);
    }

    @Test
    void openingAnEngagementWritesTheJobItsFirstUnitOfWorkAndTheEvidence() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String message = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");

        JsonNode opened = openJob(browser, message, "Rebranding Aurora Coffee");

        String job = opened.get("jobId").asText();
        String node = opened.get("nodeId").asText();
        assertThat(opened.hasNonNull("nodeId")).isTrue();
        assertThat(opened.hasNonNull("jobId")).isTrue();
        assertThat(opened.hasNonNull("trackId"))
                .as("self-directed work still belongs to a thread, and the response carries which one")
                .isTrue();
        String track = opened.get("trackId").asText();

        assertThat(jdbc.queryForObject("select name from job where id = ?::uuid", String.class, job))
                .isEqualTo("Rebranding Aurora Coffee");
        assertThat(jdbc.queryForObject("select opened_by from job where id = ?::uuid", String.class, job))
                .as("the engagement belongs to the person who opened it")
                .isEqualTo(company.maria().toString());

        assertThat(jdbc.queryForObject("select job_id from work_node where id = ?::uuid", String.class, node))
                .isEqualTo(job);
        assertThat(jdbc.queryForObject("select kind from work_node where id = ?::uuid", String.class, node))
                .as("the message carries the start boundary, which nothing else in the system can supply")
                .isEqualTo("JOB_START");
        assertThat(jdbc.queryForObject("select direction from work_node where id = ?::uuid", String.class, node))
                .as("pasting a brief is not a request made of another person")
                .isEqualTo("STANDALONE");
        assertThat(jdbc.queryForObject("select track_id from work_node where id = ?::uuid", String.class, node))
                .isEqualTo(track);
        assertThat(jdbc.queryForObject("select text from work_node where id = ?::uuid", String.class, node))
                .as("the words are copied at creation, so the message can be deleted without losing the work")
                .isEqualTo("Aurora Coffee vrea un rebranding complet");

        assertThat(jdbc.queryForObject("select job_id from track where id = ?::uuid", String.class, track))
                .isEqualTo(job);

        assertThat(jdbc.queryForObject(
                        "select message_id from work_node_evidence where work_node_id = ?::uuid", String.class, node))
                .as("a node with no evidence row is a unit of work nothing can explain afterwards")
                .isEqualTo(message);
        assertThat(jdbc.queryForObject(
                        "select origin from work_node_evidence where work_node_id = ?::uuid", String.class, node))
                .isEqualTo("PRIMARY");
    }

    @Test
    void markingArequestInfersAThreadThatIsNotTheOneTheEngagementOpenedWith() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        JsonNode opened = openJob(browser, brief, "Rebranding Aurora Coffee");
        String job = opened.get("jobId").asText();
        String jobStartTrack = opened.get("trackId").asText();

        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        ResponseEntity<String> marked = mark(browser, asked, job, "REQUEST", company.andrei());

        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json.readTree(marked.getBody());
        assertThat(body.hasNonNull("trackId"))
                .as("a thread was inferred, and nobody was asked which one")
                .isTrue();
        assertThat(body.get("jobId").asText()).isEqualTo(job);
        assertThat(body.get("trackId").asText())
                .as("work Maria kept and work she asked of Andrei are different threads")
                .isNotEqualTo(jobStartTrack);
        assertThat(jdbc.queryForObject("select count(*) from track where job_id = ?::uuid", Long.class, job))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                        "select performer_id from work_node where id = ?::uuid",
                        String.class,
                        body.get("nodeId").asText()))
                .isEqualTo(company.andrei().toString());
    }

    @Test
    void twoPiecesOfWorkBetweenTheSamePairInOneEngagementBelongTogether() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();

        String first = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        String second = said(browser, conversation, "Si variantele de logo, tot pentru joi");
        JsonNode moodboard = json.readTree(
                mark(browser, first, job, "REQUEST", company.andrei()).getBody());
        ResponseEntity<String> logos = mark(browser, second, job, "REQUEST", company.andrei());

        assertThat(logos.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode variants = json.readTree(logos.getBody());
        assertThat(variants.get("trackId").asText())
                .as("threading is inferred, and two pieces of work by the same pair belong together")
                .isEqualTo(moodboard.get("trackId").asText());

        assertThat(jdbc.queryForObject(
                        "select count(distinct track_id) from work_node where id in (?::uuid, ?::uuid)",
                        Long.class,
                        moodboard.get("nodeId").asText(),
                        variants.get("nodeId").asText()))
                .as("the rows agree with the responses, which is what the clusterer will read")
                .isEqualTo(1L);
    }

    @Test
    void aStatusQuestionIsCapturedAndOpensNoThread() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();
        long threadsBefore = jdbc.queryForObject("select count(*) from track", Long.class);

        String question = said(browser, conversation, "Unde e design-ul pentru Aurora?");
        ResponseEntity<String> marked = mark(browser, question, job, "QUERY", null);

        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json.readTree(marked.getBody());
        assertThat(body.hasNonNull("trackId"))
                .as("invariant I11 — a status question never opens or joins a thread")
                .isFalse();

        String node = body.get("nodeId").asText();
        assertThat(jdbc.queryForObject("select state from work_node where id = ?::uuid", String.class, node))
                .as("the query branch is entered at the mark and is one-way")
                .isEqualTo("QUERY");
        assertThat(jdbc.queryForObject("select track_id from work_node where id = ?::uuid", String.class, node))
                .isNull();
        assertThat(jdbc.queryForObject("select count(*) from track", Long.class))
                .as("and it opened none either")
                .isEqualTo(threadsBefore);
    }

    @Test
    void aMessageTheCallerCannotSeeIsRefusedRatherThanFaulted() throws Exception {
        Company company = buildTheCompany();
        String hers = conversationBetween(browser, company.andrei());
        String brief = said(browser, hers, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String theirs = conversationBetween(andrei, company.elena());
        String notForMaria = said(andrei, theirs, "Elena, ai apucat sa te uiti peste oferta?");
        long nodesBefore = jdbc.queryForObject("select count(*) from work_node", Long.class);

        ResponseEntity<String> hidden = mark(browser, notForMaria, job, "REQUEST", company.elena());
        ResponseEntity<String> neverExisted = mark(browser, UUID.randomUUID().toString(), job, "REQUEST", null);

        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(hidden.getBody()).get("code").asText()).isEqualTo("MESSAGE_NOT_MARKABLE");
        assertThat(neverExisted.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(neverExisted.getBody()).get("code").asText()).isEqualTo("MESSAGE_NOT_MARKABLE");
        assertThat(hidden.getBody())
                .as("same status, same code, same words — or the difference is the enumeration")
                .isEqualTo(neverExisted.getBody());

        assertThat(jdbc.queryForObject("select count(*) from work_node", Long.class))
                .as("nothing was captured from a sentence Maria may not read")
                .isEqualTo(nodesBefore);
    }

    @Test
    void twoMarksOnOneSentenceLeaveBothRowsReadingAsASplit() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();

        String assignment = said(browser, conversation, "Andrei - textele. Elena - layout-ul.");
        assertThat(mark(browser, assignment, job, "REQUEST", company.andrei()).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(jdbc.queryForList(
                        "select origin from work_node_evidence where message_id = ?::uuid", String.class, assignment))
                .as("one mark on a sentence is the ordinary case, and nothing about it is a split yet")
                .containsExactly("PRIMARY");

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        ResponseEntity<String> elenasHalf = mark(andrei, assignment, job, "REQUEST", company.elena());

        assertThat(elenasHalf.getStatusCode())
                .as("marking a sentence somebody else already marked is reconciled, never refused")
                .isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForList(
                        "select origin from work_node_evidence where message_id = ?::uuid", String.class, assignment))
                .as("both rows, not only the second - reading either one has to tell you it fanned out")
                .containsExactly("SPLIT", "SPLIT");

        assertThat(jdbc.queryForList(
                        "select origin from work_node_evidence where message_id = ?::uuid", String.class, brief))
                .as("a sentence nobody else marked is untouched by what happened to another one")
                .containsExactly("PRIMARY");
    }

    @Test
    void anUnknownEngagementIsRefusedRatherThanInvented() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");

        ResponseEntity<String> refused =
                mark(browser, asked, UUID.randomUUID().toString(), "REQUEST", company.andrei());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode error = json.readTree(refused.getBody());
        assertThat(error.get("code").asText()).isEqualTo("UNKNOWN_JOB");
        assertThat(error.get("details").get(0).get("field").asText()).isEqualTo("jobId");
        assertThat(jdbc.queryForObject("select count(*) from job", Long.class))
                .as("no engagement was conjured to hold the work")
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from work_node", Long.class))
                .isZero();
    }
}
