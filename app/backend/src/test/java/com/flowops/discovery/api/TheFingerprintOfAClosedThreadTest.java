package com.flowops.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.model.Fingerprint;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-END-THREAD-01")
class TheFingerprintOfAClosedThreadTest extends CompanyScenarioTest {
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

    private String canonicalOf(String nodeId) {
        return jdbc.queryForObject("select fingerprint from work_node where id = ?::uuid", String.class, nodeId);
    }

    private Fingerprint fingerprintOf(String nodeId) {
        return jdbc.queryForObject(
                """
                select fingerprint_performer_role_id, fingerprint_median_work_ms, fingerprint_output_type,
                       fingerprint_preceding_role_id, fingerprint_preceding_direction,
                       fingerprint_following_role_id, fingerprint_position_in_track
                from work_node where id = ?::uuid
                """,
                (row, index) -> {
                    Object median = row.getObject("fingerprint_median_work_ms");
                    String output = row.getString("fingerprint_output_type");
                    String preceding = row.getString("fingerprint_preceding_direction");
                    return Fingerprint.rehydrated(
                            row.getObject("fingerprint_performer_role_id", UUID.class),
                            median == null ? null : Duration.ofMillis(((Number) median).longValue()),
                            output == null ? null : OutputType.valueOf(output),
                            row.getObject("fingerprint_preceding_role_id", UUID.class),
                            preceding == null ? null : Direction.valueOf(preceding),
                            row.getObject("fingerprint_following_role_id", UUID.class),
                            row.getInt("fingerprint_position_in_track"));
                },
                nodeId);
    }

    private Fingerprint withoutItsClock(Fingerprint print) {
        return Fingerprint.rehydrated(
                print.performerRoleId(),
                null,
                print.outputType(),
                print.precedingRoleId(),
                print.precedingDirection(),
                print.followingRoleId(),
                print.positionInTrack());
    }

    private record ThreadOfWork(String trackId, String requestNode, String completionNode) {}

    private ThreadOfWork mariaAsksAndrei(RoundTripClient andrei, UUID andreiId, String jobName, String output)
            throws Exception {
        String conversation = conversationBetween(browser, andreiId);
        String brief = said(browser, conversation, "Aurora Coffee vrea " + jobName);
        String job = openJob(browser, brief, jobName).get("jobId").asText();

        String asked = said(browser, conversation, "Andrei, poti sa faci asta pana joi?");
        JsonNode request = mark(browser, asked, job, "REQUEST", andreiId);

        String finished = said(andrei, conversation, "Gata, e in drive");
        JsonNode completion = mark(browser, finished, job, "COMPLETION", null);
        produced(andrei, completion.get("nodeId").asText(), output);

        return new ThreadOfWork(
                request.get("trackId").asText(),
                request.get("nodeId").asText(),
                completion.get("nodeId").asText());
    }

    @Test
    void aClosedQualifyingThreadFingerprintsEveryUnitOfWorkInIt() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        UUID contentWriter = statedJobOf(company.andrei());
        ThreadOfWork work = mariaAsksAndrei(andrei, company.andrei(), "un moodboard", "TEXT");

        ResponseEntity<String> ended = endThread(browser, work.trackId());

        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(ended.getBody()).get("completeness").asText())
                .as("only a qualifying thread is fingerprinted, so the close has to have produced one")
                .isEqualTo("COMPLETE");

        Fingerprint request = fingerprintOf(work.requestNode());
        assertThat(request.positionInTrack())
                .as("position is counted from one, inside its own thread and never inside the engagement")
                .isEqualTo(1);
        assertThat(request.performerRoleId())
                .as("the role of whoever does it — never the person, invariant I5")
                .isEqualTo(contentWriter);
        assertThat(request.precedingRoleId())
                .as("nothing came before the first unit of work in a thread")
                .isNull();
        assertThat(request.precedingDirection())
                .as("and half a hand-over is refused: absent role, absent direction")
                .isNull();
        assertThat(request.followingRoleId())
                .as("a component that does not exist until the thread has stopped receiving work")
                .isEqualTo(contentWriter);
        assertThat(request.outputType())
                .as("a request produced nothing itself; the completion answering it did")
                .isNull();

        Fingerprint completion = fingerprintOf(work.completionNode());
        assertThat(completion.positionInTrack()).isEqualTo(2);
        assertThat(completion.performerRoleId()).isEqualTo(contentWriter);
        assertThat(completion.outputType())
                .as("the strongest of the six, and bought for one tap")
                .isEqualTo(OutputType.TEXT);
        assertThat(completion.precedingRoleId()).isEqualTo(contentWriter);
        assertThat(completion.precedingDirection())
                .as("which way the hand-over pointed, which is what stops a duration being invented backwards")
                .isEqualTo(Direction.REQUEST);
        assertThat(completion.followingRoleId())
                .as("nothing followed it; the thread ended there")
                .isNull();
        assertThat(completion.medianWorkPhase())
                .as("the work segment was sealed by the Done tap, so there is a median to take")
                .isNotNull();

        assertThat(canonicalOf(work.completionNode()))
                .as("the canonical form is what the cluster index is built on, and it names every component")
                .contains("out=TEXT")
                .contains("pos=2")
                .contains("prev=" + contentWriter + ":REQUEST");
    }

    @Test
    void theSentenceThatOpenedTheEngagementGetsNoFingerprintAndTakesNoPosition() throws Exception {
        Company company = buildTheCompany();
        UUID agencyOwner = statedJobOf(company.maria());
        String conversation = conversationBetween(browser, company.andrei());

        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        JsonNode opened = openJob(browser, brief, "Rebranding Aurora Coffee");
        String job = opened.get("jobId").asText();
        String boundary = opened.get("nodeId").asText();
        String thread = opened.get("trackId").asText();

        String own = said(browser, conversation, "Ma uit peste materialele vechi inainte sa incepem");
        JsonNode mine = mark(browser, own, job, "STANDALONE", null);
        assertThat(mine.get("trackId").asText())
                .as("Maria's own work keys on her own role pair, which is the thread the boundary opened")
                .isEqualTo(thread);

        assertThat(endThread(browser, thread).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(canonicalOf(boundary))
                .as("a boundary is not an activity somebody performed, so there is nothing to fingerprint")
                .isNull();

        Fingerprint work = fingerprintOf(mine.get("nodeId").asText());
        assertThat(work.positionInTrack())
                .as("first in the thread — the excluded boundary does not take position 1 from it")
                .isEqualTo(1);
        assertThat(work.precedingRoleId())
                .as("and nothing handed this work over, because the thing before it is not work")
                .isNull();
        assertThat(work.performerRoleId()).isEqualTo(agencyOwner);
    }

    @Test
    void aThreadThatWasNeverMoreThanABeginningIsFingerprintedNotAtAll() throws Exception {
        Company company = buildTheCompany();
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job =
                openJob(browser, brief, "Rebranding Aurora Coffee").get("jobId").asText();
        String asked = said(browser, conversation, "Andrei, poti sa faci moodboard-ul pana joi?");
        JsonNode request = mark(browser, asked, job, "REQUEST", company.andrei());
        String thread = request.get("trackId").asText();

        ResponseEntity<String> ended = endThread(browser, thread);

        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(ended.getBody()).get("completeness").asText()).isEqualTo("START_ONLY");
        assertThat(canonicalOf(request.get("nodeId").asText()))
                .as("a half-captured thread must not become evidence, and the absence is the enforcement")
                .isNull();
        assertThat(jdbc.queryForObject(
                        "select count(*) from work_node where track_id = ?::uuid and fingerprint is not null",
                        Long.class,
                        thread))
                .isZero();
    }

    @Test
    void twoThreadsOfTheSameShapeMatchAndAThreadOfAnotherShapeDoesNot() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");

        ThreadOfWork aurora = mariaAsksAndrei(andrei, company.andrei(), "un moodboard", "TEXT");
        ThreadOfWork northwind = mariaAsksAndrei(andrei, company.andrei(), "un newsletter", "TEXT");

        String withElena = conversationBetween(browser, company.elena());
        String brief = said(browser, withElena, "Bistro Verde vrea materiale noi");
        String thirdJob =
                openJob(browser, brief, "Materiale Bistro Verde").get("jobId").asText();
        String askedElena = said(browser, withElena, "Elena, poti sa faci asta pana joi?");
        JsonNode elenasRequest = mark(browser, askedElena, thirdJob, "REQUEST", company.elena());
        String elenaFinished = said(elena, withElena, "Gata, e in drive");
        JsonNode elenasCompletion = mark(browser, elenaFinished, thirdJob, "COMPLETION", null);
        produced(elena, elenasCompletion.get("nodeId").asText(), "TEXT");

        assertThat(endThread(browser, aurora.trackId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(endThread(browser, northwind.trackId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(endThread(browser, elenasRequest.get("trackId").asText()).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(fingerprintOf(aurora.requestNode()).matches(fingerprintOf(northwind.requestNode())))
                .as("no shared word, no shared engagement, and the same work")
                .isTrue();
        assertThat(withoutItsClock(fingerprintOf(aurora.completionNode()))
                        .matches(withoutItsClock(fingerprintOf(northwind.completionNode()))))
                .as("and the same at the other end of the thread")
                .isTrue();

        assertThat(fingerprintOf(aurora.requestNode())
                        .matches(fingerprintOf(elenasRequest.get("nodeId").asText())))
                .as("a designer's thread is not a content writer's, whatever the sentences say")
                .isFalse();
        assertThat(canonicalOf(aurora.requestNode()))
                .as("the canonical forms differ too, which is what the cluster index will see")
                .isNotEqualTo(canonicalOf(elenasRequest.get("nodeId").asText()));
    }
}
