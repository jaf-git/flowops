package com.flowops.aiinsight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AI-INSIGHT-MISSING-STEP-01")
class InsightRoundTripTest extends CompanyScenarioTest {
    @Test
    void saysNothingAboutATemplateWithNoCompletedRuns() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplate(maria);

        ResponseEntity<String> answered =
                maria.get("/api/insights?subject_type=process_template&subject_id=" + template);

        assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(answered.getBody()))
                .as("below the floor the product says nothing at all — not a hedged finding, not a"
                        + " greyed one. A hedge is read as a recommendation by anybody in a hurry")
                .isEmpty();
    }

    @Test
    void isReachableByManagersAndTheOwnerAndNobodyElse() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplate(maria);
        String path = "/api/insights?subject_type=process_template&subject_id=" + template;

        assertThat(maria.get(path).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(signedInBrowser("ionut@atelier.ro").get(path).getStatusCode())
                .as("a manager authors processes and is the person most likely to act on this")
                .isEqualTo(HttpStatus.OK);
        assertThat(signedInBrowser("andrei@atelier.ro").get(path).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void refusesASubjectItCannotReasonAbout() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        ResponseEntity<String> refused =
                maria.get("/api/insights?subject_type=biscuit&subject_id=" + UUID.randomUUID());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("UNKNOWN_SUBJECT_TYPE");
    }

    @Test
    void namesNobodyOnAnyRoute() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplate(maria);

        String body = maria.get("/api/insights?subject_type=process_template&subject_id=" + template)
                .getBody();

        assertThat(body)
                .doesNotContain("assignee")
                .doesNotContain("Maria")
                .doesNotContain(company.andrei().toString());
    }

    private UUID aTemplate(RoundTripClient author) throws Exception {
        ResponseEntity<String> created = author.post(
                "/api/process-templates",
                """
                {"name":"Client onboarding","overview":"first conversation to work starting","steps":[
                  {"taskTemplateId":"%s","expectedDurationHours":2},
                  {"taskTemplateId":"%s","expectedDurationHours":4},
                  {"taskTemplateId":"%s","expectedDurationHours":3}
                ]}"""
                        .formatted(work("Discovery call"), work("Proposal"), work("Contract")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode template = json.readTree(created.getBody());
        return UUID.fromString(template.get("id").asText());
    }
}
