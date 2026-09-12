package com.flowops.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.discovery.domain.enums.KeyBasis;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-OPEN-JOB-01")
@Tag("DISCOVERY-MARK-MESSAGE-01")
class TheThreadKeySeparatesWorkThatOnlyLooksAlikeTest extends CompanyScenarioTest {
    private static final String ACCOUNT_MANAGER = "Account manager";
    private static final String CONTENT_WRITER = "Content writer";

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

    private String requestOf(String messageId, String jobId, UUID performer) throws Exception {
        ResponseEntity<String> marked = browser.post(
                "/api/discovery/nodes",
                "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"REQUEST\",\"performerId\":\"%s\"}"
                        .formatted(messageId, jobId, performer));
        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json.readTree(marked.getBody());
        assertThat(body.hasNonNull("trackId"))
                .as("a request always belongs to a thread")
                .isTrue();
        return body.get("trackId").asText();
    }

    private String columnOfTrack(String column, String track) {
        return jdbc.queryForObject("select " + column + " from track where id = ?::uuid", String.class, track);
    }

    @Test
    void aCompletionJoinsTheThreadOfTheRequestItAnswers() throws Exception {
        Company company = buildTheCompany();
        does(company.maria(), ACCOUNT_MANAGER);
        does(company.andrei(), CONTENT_WRITER);

        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job = openJob(browser, brief, "Rebranding Aurora Coffee");
        String asked = said(browser, conversation, "Andrei, imi scrii articolul de blog pentru Aurora?");
        String requestTrack = requestOf(asked, job, company.andrei());

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String reported = said(andrei, conversation, "Gata articolul, ti l-am trimis");
        ResponseEntity<String> marked = browser.post(
                "/api/discovery/nodes",
                "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"COMPLETION\",\"performerId\":null}"
                        .formatted(reported, job));

        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode completion = json.readTree(marked.getBody());
        assertThat(completion.get("trackId").asText())
                .as("a request and the completion that answers it are one piece of work, not two")
                .isEqualTo(requestTrack);

        assertThat(jdbc.queryForObject("select count(*) from track where job_id = ?::uuid", Long.class, job))
                .as("Maria's opening thread and the blog-post thread — and no third one for the answer")
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                        "select creator_id from work_node where id = ?::uuid",
                        String.class,
                        completion.get("nodeId").asText()))
                .as("the creator is who said it, not who clicked it — Maria marked Andrei's sentence")
                .isEqualTo(company.andrei().toString());
    }

    @Test
    void theIntendedRungIsReachedWhenBothPartiesHaveAStatedJob() throws Exception {
        Company company = buildTheCompany();
        UUID accountManager = does(company.maria(), ACCOUNT_MANAGER);
        UUID contentWriter = does(company.andrei(), CONTENT_WRITER);

        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job = openJob(browser, brief, "Rebranding Aurora Coffee");
        String asked = said(browser, conversation, "Andrei, imi scrii articolul de blog pentru Aurora?");

        String track = requestOf(asked, job, company.andrei());

        assertThat(columnOfTrack("key_basis", track))
                .as("the intended rung, not the rung of last resort")
                .isEqualTo(KeyBasis.ROLE_PAIR.name());
        assertThat(columnOfTrack("from_role_id", track)).as("who asked").isEqualTo(accountManager.toString());
        assertThat(columnOfTrack("to_role_id", track)).as("who does it").isEqualTo(contentWriter.toString());
        assertThat(columnOfTrack("from_role_id", track))
                .as("two different roles exchanged work, so this is a pair rather than solo work")
                .isNotEqualTo(columnOfTrack("to_role_id", track));
        assertThat(jdbc.queryForObject("select solo from track where id = ?::uuid", Boolean.class, track))
                .isFalse();
    }

    @Test
    void twoWritersForOneManagerDoNotShareAThread() throws Exception {
        Company company = buildTheCompany();
        does(company.maria(), ACCOUNT_MANAGER);
        does(company.andrei(), CONTENT_WRITER);
        does(company.elena(), CONTENT_WRITER);

        String withAndrei = conversationBetween(browser, company.andrei());
        String withElena = conversationBetween(browser, company.elena());
        String brief = said(browser, withAndrei, "Aurora Coffee vrea un rebranding complet");
        String job = openJob(browser, brief, "Rebranding Aurora Coffee");

        String blog = said(browser, withAndrei, "Andrei, imi scrii articolul de blog pentru Aurora?");
        String captions = said(browser, withElena, "Elena, imi faci textele de social pentru Aurora?");

        String andreisThread = requestOf(blog, job, company.andrei());
        String elenasThread = requestOf(captions, job, company.elena());

        assertThat(andreisThread)
                .as("two writers for one manager are two threads of work, never one")
                .isNotEqualTo(elenasThread);

        assertThat(columnOfTrack("from_role_id", andreisThread))
                .as("the same account manager asked for both")
                .isEqualTo(columnOfTrack("from_role_id", elenasThread));
        assertThat(columnOfTrack("to_role_id", andreisThread))
                .as("and both are content writers, so the pair alone cannot tell these apart")
                .isEqualTo(columnOfTrack("to_role_id", elenasThread));
        assertThat(columnOfTrack("performer_id", andreisThread))
                .as("the performer is the whole of the difference")
                .isEqualTo(company.andrei().toString())
                .isNotEqualTo(columnOfTrack("performer_id", elenasThread));
        assertThat(columnOfTrack("performer_id", elenasThread))
                .isEqualTo(company.elena().toString());
    }

    @Test
    void aSecondRequestToTheSameWriterJoinsTheThreadTheFirstOpened() throws Exception {
        Company company = buildTheCompany();
        does(company.maria(), ACCOUNT_MANAGER);
        does(company.andrei(), CONTENT_WRITER);

        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        String job = openJob(browser, brief, "Rebranding Aurora Coffee");

        String blog = said(browser, conversation, "Andrei, imi scrii articolul de blog pentru Aurora?");
        String newsletter = said(browser, conversation, "Si newsletter-ul de luna asta, tot pentru Aurora");

        String first = requestOf(blog, job, company.andrei());
        String second = requestOf(newsletter, job, company.andrei());

        assertThat(second)
                .as("one pair, one performer, one thread — however many things are asked for")
                .isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from track where job_id = ?::uuid", Long.class, job))
                .as("the opening thread and the one they share — a third would mean the lookup missed it")
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from work_node where track_id = ?::uuid", Long.class, first))
                .as("both pieces of work are in it")
                .isEqualTo(2L);
        assertThat(columnOfTrack("state", first))
                .as("a thread with two nodes is work that happened, not a request nobody answered")
                .isEqualTo("ACTIVE");
    }

    @Test
    void somebodyWithNoStatedJobStillGetsAThreadAndItIsDisclosedAsWeak() throws Exception {
        Company company = buildTheCompany();
        does(company.maria(), ACCOUNT_MANAGER);
        does(company.andrei(), CONTENT_WRITER);

        hasNoStatedJob(company.ionut());
        assertThat(jdbc.queryForObject(
                        "select functional_role_id from workspace_membership where user_id = ?",
                        String.class,
                        company.ionut()))
                .as("the control is only a control if he genuinely has no stated job")
                .isNull();

        String withAndrei = conversationBetween(browser, company.andrei());
        String withIonut = conversationBetween(browser, company.ionut());
        String brief = said(browser, withAndrei, "Aurora Coffee vrea un rebranding complet");
        String job = openJob(browser, brief, "Rebranding Aurora Coffee");

        String copy = said(browser, withAndrei, "Andrei, imi scrii articolul de blog pentru Aurora?");
        String favour = said(browser, withIonut, "Ionut, poti sa vorbesti tu cu tipografia?");

        String stated = requestOf(copy, job, company.andrei());
        String unstated = requestOf(favour, job, company.ionut());

        assertThat(columnOfTrack("key_basis", unstated))
                .as("no stated job at one end, so the ladder drops to its last rung")
                .isEqualTo(KeyBasis.PERFORMER.name());
        assertThat(columnOfTrack("from_role_id", unstated)).isNull();
        assertThat(columnOfTrack("to_role_id", unstated)).isNull();
        assertThat(columnOfTrack("performer_id", unstated))
                .as("he is still threaded — work never waits on an unanswered question")
                .isEqualTo(company.ionut().toString());

        assertThat(KeyBasis.valueOf(columnOfTrack("key_basis", unstated)).isWeak())
                .as("findings over this thread must travel with their caution")
                .isTrue();

        assertThat(columnOfTrack("key_basis", stated))
                .as("and the intended rung is still reached in the same engagement, or this proves nothing")
                .isEqualTo(KeyBasis.ROLE_PAIR.name());
        assertThat(KeyBasis.valueOf(columnOfTrack("key_basis", stated)).isWeak())
                .as("nothing about Andrei's thread is disclosed as weak")
                .isFalse();
    }
}
