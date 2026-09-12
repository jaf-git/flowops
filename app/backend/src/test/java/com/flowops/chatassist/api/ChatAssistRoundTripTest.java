package com.flowops.chatassist.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("CHAT-ASSIST-SUGGEST-WORK-01")
class ChatAssistRoundTripTest extends CompanyScenarioTest {
    private static final String ASK = "/api/chat-assist/conversations/%s/work-suggestion";

    @Test
    void saysItIsNotConnectedRatherThanThatTheConversationIsDull() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        UUID conversation = aConversationBetween(ionut, company.andrei());

        ResponseEntity<String> answered = ionut.post(ASK.formatted(conversation), "");

        assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode said = json.readTree(answered.getBody());
        assertThat(said.get("available").asBoolean())
                .as("switched off is a bean rather than a branch, so there is no model in this build")
                .isFalse();
        assertThat(said.get("steps")).isEmpty();
    }

    @Test
    void refusesANonParticipantExactlyAsItRefusesAConversationThatDoesNotExist() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        UUID theirs = aConversationBetween(ionut, company.andrei());

        RoundTripClient outsider = signedInBrowser("elena@atelier.ro");

        ResponseEntity<String> notYours = outsider.post(ASK.formatted(theirs), "");
        ResponseEntity<String> noSuchThing = outsider.post(ASK.formatted(UUID.randomUUID()), "");

        assertThat(notYours.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(noSuchThing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(notYours.getBody()).get("code").asText())
                .as("the same code, so the two situations cannot be told apart from outside")
                .isEqualTo(json.readTree(noSuchThing.getBody()).get("code").asText());
    }

    @Test
    void writesNothingHoweverOftenItIsAsked() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        UUID conversation = aConversationBetween(ionut, company.andrei());

        long tasksBefore = countOf("task");
        long messagesBefore = countOf("message");

        for (int asked = 0; asked < 3; asked++) {
            assertThat(ionut.post(ASK.formatted(conversation), "").getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        assertThat(countOf("task")).as("asking is not creating").isEqualTo(tasksBefore);
        assertThat(countOf("message")).isEqualTo(messagesBefore);
    }

    @Test
    void letsEitherParticipantAsk() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        UUID conversation = aConversationBetween(ionut, company.andrei());

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        assertThat(andrei.post(ASK.formatted(conversation), "").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void namesNobodyInItsAnswer() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        UUID conversation = aConversationBetween(ionut, company.andrei());

        String body = ionut.post(ASK.formatted(conversation), "").getBody();

        assertThat(body)
                .as("identifiers, never what somebody is called")
                .doesNotContain("Andrei")
                .doesNotContain("Ionuț")
                .doesNotContain("displayName");
    }

    private long countOf(String table) {
        Long rows = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return rows == null ? 0 : rows;
    }

    private UUID aConversationBetween(RoundTripClient opener, UUID other) throws Exception {
        ResponseEntity<String> started = opener.post("/api/conversations", "{\"personId\":\"%s\"}".formatted(other));
        assertThat(started.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);

        UUID conversation =
                UUID.fromString(json.readTree(started.getBody()).get("id").asText());
        opener.post(
                "/api/conversations/" + conversation + "/messages",
                "{\"body\":\"Aurora Coffee signed. Discovery workshop first, then the competitor scan.\"}");
        return conversation;
    }
}
