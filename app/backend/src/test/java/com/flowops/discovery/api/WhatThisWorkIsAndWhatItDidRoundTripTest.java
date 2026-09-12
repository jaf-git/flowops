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

@Tag("DISCOVERY-ENRICH-NODE-01")
@Tag("DISCOVERY-VIEW-NODE-TRAIL-01")
class WhatThisWorkIsAndWhatItDidRoundTripTest extends CompanyScenarioTest {
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

    private String aRequestOfAndrei(Company company) throws Exception {
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        ResponseEntity<String> opened = browser.post(
                "/api/discovery/jobs", "{\"messageId\":\"%s\",\"name\":\"Rebranding Aurora\"}".formatted(brief));
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String job = json.readTree(opened.getBody()).get("jobId").asText();

        String asked = said(browser, conversation, "Andrei, poti sa scrii postarile pentru Aurora?");
        ResponseEntity<String> marked = browser.post(
                "/api/discovery/nodes",
                "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"REQUEST\",\"performerId\":\"%s\"}"
                        .formatted(asked, job, company.andrei()));
        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(marked.getBody()).get("nodeId").asText();
    }

    private ResponseEntity<String> enrich(RoundTripClient who, String node, String body) {
        return who.patch("/api/discovery/nodes/" + node + "/enrichment", body);
    }

    private long trailRowsOn(String node) {
        return jdbc.queryForObject(
                "select count(*) from work_node_state_transition where node_id = ?::uuid", Long.class, node);
    }

    @Test
    void aPersonNamesTheWorkAndItComesBackNamed() throws Exception {
        Company company = buildTheCompany();
        String node = aRequestOfAndrei(company);

        ResponseEntity<String> answered = enrich(
                browser,
                node,
                """
                {"title":"caption set for Aurora","detail":"three posts and a story",
                 "checklist":["draft the copy","check the brand voice"]}
                """);

        assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(answered.getBody());
        assertThat(body.get("title").asText()).isEqualTo("caption set for Aurora");
        assertThat(body.get("accepted").asBoolean()).isTrue();
        assertThat(body.get("checklist")).hasSize(2);

        assertThat(jdbc.queryForObject("select title from work_node where id = ?::uuid", String.class, node))
                .isEqualTo("caption set for Aurora");
        assertThat(jdbc.queryForObject("select detail from work_node where id = ?::uuid", String.class, node))
                .isEqualTo("three posts and a story");
    }

    @Test
    void whatOnePersonNamedAnotherPersonSees() throws Exception {
        Company company = buildTheCompany();
        String node = aRequestOfAndrei(company);
        enrich(browser, node, "{\"title\":\"caption set for Aurora\"}");

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        ResponseEntity<String> seen = enrich(andrei, node, "{\"detail\":\"three posts and a story\"}");

        assertThat(seen.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(seen.getBody());
        assertThat(body.get("title").asText()).isEqualTo("caption set for Aurora");
        assertThat(body.get("detail").asText()).isEqualTo("three posts and a story");

        ResponseEntity<String> refused = enrich(andrei, node, "{\"title\":\"something else entirely\"}");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theAuthorCanCorrectTheirOwnTypoWhileTheNodeIsOpen() throws Exception {
        Company company = buildTheCompany();
        String node = aRequestOfAndrei(company);
        enrich(browser, node, "{\"title\":\"caption ste for Aurora\"}");

        ResponseEntity<String> corrected = enrich(browser, node, "{\"title\":\"caption set for Aurora\"}");

        assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(corrected.getBody()).get("title").asText()).isEqualTo("caption set for Aurora");

        assertThat(jdbc.queryForObject("select title from work_node where id = ?::uuid", String.class, node))
                .isEqualTo("caption set for Aurora");
    }

    @Test
    void abystanderCannotDescribeSomebodyElsesWork() throws Exception {
        Company company = buildTheCompany();
        String node = aRequestOfAndrei(company);

        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        ResponseEntity<String> refused = enrich(elena, node, "{\"title\":\"not mine to name\"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_YOURS_TO_DESCRIBE");

        assertThat(jdbc.queryForObject("select title from work_node where id = ?::uuid", String.class, node))
                .isNull();
    }

    @Test
    void aTitleThatIsASentenceIsRefused() throws Exception {
        Company company = buildTheCompany();
        String node = aRequestOfAndrei(company);

        ResponseEntity<String> tooLong = enrich(browser, node, "{\"title\":\"%s\"}".formatted("x".repeat(121)));

        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void describingWorkThatDoesNotExistIsRefusedWithItsCode() throws Exception {
        buildTheCompany();

        ResponseEntity<String> missing =
                enrich(browser, UUID.randomUUID().toString(), "{\"title\":\"nothing to name\"}");

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(missing.getBody()).get("code").asText()).isEqualTo("UNKNOWN_WORK_NODE");
    }

    @Test
    void theTrailKeepsEveryMoveTheStateColumnOverwrote() throws Exception {
        Company company = buildTheCompany();
        String node = aRequestOfAndrei(company);

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        ResponseEntity<String> done =
                andrei.post("/api/discovery/nodes/" + node + "/output", "{\"outputType\":\"TEXT\"}");
        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject("select state from work_node where id = ?::uuid", String.class, node))
                .isEqualTo("COMPLETED");

        ResponseEntity<String> trail = andrei.get("/api/discovery/nodes/" + node + "/trail");
        assertThat(trail.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(trail.getBody());

        assertThat(body.get("recordedFromTheStart").asBoolean()).isTrue();
        assertThat(body.get("moves")).hasSize(4);
        assertThat(body.get("moves").get(0).get("from").isNull()).isTrue();
        assertThat(body.get("moves").get(0).get("to").asText()).isEqualTo("MARKED");
        assertThat(body.get("moves").get(3).get("to").asText()).isEqualTo("COMPLETED");

        assertThat(trailRowsOn(node)).isEqualTo(4);
    }

    @Test
    void asecondSaveDoesNotRepeatTheMovesAlreadyWrittenDown() throws Exception {
        Company company = buildTheCompany();
        String node = aRequestOfAndrei(company);
        long afterTheMark = trailRowsOn(node);

        enrich(browser, node, "{\"title\":\"caption set for Aurora\"}");
        enrich(browser, node, "{\"detail\":\"three posts and a story\"}");

        assertThat(trailRowsOn(node)).isEqualTo(afterTheMark);
    }

    @Test
    void theHistoryOfWorkThatDoesNotExistIsRefusedRatherThanEmpty() throws Exception {
        buildTheCompany();

        ResponseEntity<String> missing = browser.get("/api/discovery/nodes/" + UUID.randomUUID() + "/trail");

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(missing.getBody()).get("code").asText()).isEqualTo("UNKNOWN_WORK_NODE");
    }
}
