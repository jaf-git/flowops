package com.flowops.aiinsight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AI-INSIGHT-ACT-ON-INSIGHT-01")
class InsightDecisionRoundTripTest extends CompanyScenarioTest {
    private static final String LIBRARY = "/api/task-templates";

    @Test
    void reportsATemplateTheLibraryHasMovedPast() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID forgotten = anApprovedTemplateApprovedLongAgo(maria, "Pregătire audit trimestrial");

        JsonNode findings = findingsAbout(maria, forgotten);

        assertThat(findings).hasSize(1);
        JsonNode stale = findings.get(0);
        assertThat(stale.get("kind").asText()).isEqualTo("UNUSED_TEMPLATE");
        assertThat(stale.get("action").asText()).isEqualTo("RETIRE_TEMPLATE");
        assertThat(stale.get("windowDays").asInt())
                .as("the window is on the card, because a template used every January is unused in July")
                .isEqualTo(90);
        assertThat(stale.get("daysSinceLastUse").asInt())
                .as("-1 says never used, which is a different sentence from unused since March")
                .isEqualTo(-1);
    }

    @Test
    void appliesThroughTheSubjectFeaturesOwnEditPath() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID forgotten = anApprovedTemplateApprovedLongAgo(maria, "Inventar anual");

        assertThat(findingsAbout(maria, forgotten))
                .as("there is something to apply")
                .hasSize(1);

        ResponseEntity<String> applied = maria.post("/api/insights/apply", decisionOn(forgotten));

        assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(statusOf(maria, forgotten))
                .as("retire, never delete: a template that stamped work is the provenance of that work")
                .isEqualTo("RETIRED");
        assertThat(findingsAbout(maria, forgotten))
                .as("the condition producing it is resolved, so it is absent afterwards")
                .isEmpty();
    }

    @Test
    void dismissesWithoutChangingAnythingAnywhereElse() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID forgotten = anApprovedTemplateApprovedLongAgo(maria, "Raport sezonier");

        assertThat(findingsAbout(maria, forgotten))
                .as("there is something to dismiss")
                .hasSize(1);

        ResponseEntity<String> dismissed = maria.post("/api/insights/dismiss", decisionOn(forgotten));

        assertThat(dismissed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(statusOf(maria, forgotten))
                .as("nothing anywhere changes except the record of the judgement")
                .isEqualTo("APPROVED");
        assertThat(findingsAbout(maria, forgotten)).isEmpty();
    }

    @Test
    void keepsADismissedFindingAwayHoweverMuchTheEvidenceGrows() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID forgotten = anApprovedTemplateApprovedLongAgo(maria, "Curățenie de primăvară");

        maria.post("/api/insights/dismiss", decisionOn(forgotten));
        ageTheApproval(forgotten, 900);

        assertThat(findingsAbout(maria, forgotten))
                .as("a resurfaced dismissal teaches a person that dismissing does not work")
                .isEmpty();
    }

    @Test
    void refusesApplyToSomebodyWhoMayReadFindingsAndNotEditTheSubject() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID forgotten = anApprovedTemplateApprovedLongAgo(maria, "Verificare stoc");

        lendTheViewTo("andrei@atelier.ro");
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        assertThat(findingsAbout(andrei, forgotten))
                .as("he may read the finding — that is what was lent to him")
                .hasSize(1);

        assertThat(andrei.post("/api/insights/apply", decisionOn(forgotten)).getStatusCode())
                .as("seeing that something is wrong and being allowed to change it are different rights")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf(maria, forgotten))
                .as("the refusal happened before anything was written")
                .isEqualTo("APPROVED");

        assertThat(andrei.post("/api/insights/dismiss", decisionOn(forgotten)).getStatusCode())
                .as("Dismiss is not an act of authority and is never withheld")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void namesTheKindRatherThanTheSubjectWhenTheKindIsTheProblem() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        ResponseEntity<String> refused = maria.post(
                "/api/insights/dismiss",
                """
                {"subjectType":"task_template","subjectId":"%s","kind":"BISCUIT","findingKey":"unused"}"""
                        .formatted(UUID.randomUUID()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode envelope = json.readTree(refused.getBody());
        assertThat(envelope.get("code").asText()).isEqualTo("UNKNOWN_INSIGHT_KIND");
        assertThat(envelope.get("details").get(0).get("field").asText()).isEqualTo("kind");
    }

    @Test
    void refusesADecisionAboutAFindingThatNoLongerHolds() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID recent = anApprovedTemplate(maria, "Ședință săptămânală");

        ResponseEntity<String> refused = maria.post("/api/insights/apply", decisionOn(recent));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("INSIGHT_NO_LONGER_HOLDS");
    }

    @Test
    void refusesTheSecondApplyOfOneFinding() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID forgotten = anApprovedTemplateApprovedLongAgo(maria, "Arhivare dosare");

        assertThat(maria.post("/api/insights/apply", decisionOn(forgotten)).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(maria.post("/api/insights/apply", decisionOn(forgotten)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void offersNoRouteThatAggregatesDecisionsPerPerson() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID forgotten = anApprovedTemplateApprovedLongAgo(maria, "Raport lunar vechi");
        maria.post("/api/insights/dismiss", decisionOn(forgotten));

        assertThat(maria.get("/api/insights/decisions").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(maria.get("/api/insights?decider=" + company.maria()).getStatusCode())
                .as("the read takes a subject and has no idea what a decider is")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void namesNobodyOnEitherDecisionRoute() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID forgotten = anApprovedTemplateApprovedLongAgo(maria, "Plan vechi de instruire");

        maria.post("/api/insights/dismiss", decisionOn(forgotten));
        String body = maria.get(insightsPath(forgotten)).getBody();

        assertThat(body).doesNotContain("Maria").doesNotContain(company.maria().toString());
    }

    private String decisionOn(UUID templateId) {
        return """
               {"subjectType":"task_template","subjectId":"%s","kind":"UNUSED_TEMPLATE","findingKey":"unused"}"""
                .formatted(templateId);
    }

    private String insightsPath(UUID templateId) {
        return "/api/insights?subject_type=task_template&subject_id=" + templateId;
    }

    private JsonNode findingsAbout(RoundTripClient reader, UUID templateId) throws Exception {
        ResponseEntity<String> answered = reader.get(insightsPath(templateId));
        assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(answered.getBody());
    }

    private String statusOf(RoundTripClient reader, UUID templateId) throws Exception {
        return json.readTree(reader.get(LIBRARY + "/" + templateId).getBody())
                .get("status")
                .asText();
    }

    private UUID anApprovedTemplate(RoundTripClient owner, String title) throws Exception {
        ResponseEntity<String> created = owner.post(
                LIBRARY,
                """
                {"title":"%s","description":"Ce facem de fiecare dată.","type":"Administrativ",
                 "priority":"NORMAL","estimatedHours":1.5,
                 "checklist":["Adună documentele","Verifică","Arhivează"],"submitForApproval":true}"""
                        .formatted(title));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID id = UUID.fromString(json.readTree(created.getBody()).get("id").asText());
        assertThat(owner.post(LIBRARY + "/" + id + "/approval", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return id;
    }

    private UUID anApprovedTemplateApprovedLongAgo(RoundTripClient owner, String title) throws Exception {
        UUID id = anApprovedTemplate(owner, title);
        ageTheApproval(id, 400);
        return id;
    }

    private void ageTheApproval(UUID templateId, int days) {
        jdbc.update(
                "update task_template set approved_at = ? where id = ?",
                OffsetDateTime.now().minusDays(days),
                templateId);
    }

    private void lendTheViewTo(String email) {
        jdbc.update(
                """
                insert into auth_user_permission (user_id, permission_name)
                select id, 'AI_INSIGHT_VIEW' from auth_user where email = ?
                on conflict do nothing
                """,
                email);
    }
}
