package com.flowops.nodepipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.infrastructure.session.SessionPrincipal;
import com.flowops.nodepipeline.application.port.ProcessDraftPort;
import com.flowops.nodepipeline.domain.compose.DraftProcess;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import com.flowops.tasklib.application.published.TemplateResolutionUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@Tag("NODEPIPE-COMPOSE-01")
@Tag("roundtrip")
class ComposedProcessRoundTripTest extends CompanyScenarioTest {
    @Autowired
    private ProcessDraftPort drafts;

    @Autowired
    private TemplateResolutionUseCase templates;

    private UUID maria;

    @AfterEach
    void forgetWhoWasCalling() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savesTheObservedOrderAndBlocksNothingWithIt() throws Exception {
        RoundTripClient browser = signedIn();

        UUID template = drafts.save(aLaunchObservedEightTimes());

        assertThat(jdbc.queryForObject(
                        "select count(*) from step_dependency where template_id = ? and kind = 'OBSERVED'",
                        Integer.class,
                        template))
                .as("the order the pipeline watched, kept")
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select min(confidence) from step_dependency where template_id = ?", Double.class, template))
                .as("and how often it held, which is what a person judges before agreeing to it")
                .isEqualTo(0.8);

        assertThat(dependenciesOn(browser, template))
                .as("no observed edge reaches the graph, so no step can be stranded by one")
                .isZero();
    }

    @Test
    void promotingAnEdgeMakesItBlockAndSaysWhoDecided() throws Exception {
        RoundTripClient browser = signedIn();
        UUID template = drafts.save(aLaunchObservedEightTimes());
        List<String> steps = stepIdsOf(browser, template);

        var promoted = browser.post(
                "/api/process-templates/" + template + "/dependencies/promote",
                """
                {"dependentStepId":"%s","dependsOnStepId":"%s"}"""
                        .formatted(steps.get(1), steps.get(0)));

        assertThat(promoted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dependenciesOn(browser, template))
                .as("exactly the one she decided, and not the one she left alone")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from step_dependency where template_id = ? and kind = 'CONFIRMED' "
                                + "and confirmed_by is not null and confirmed_at is not null",
                        Integer.class,
                        template))
                .as("a promotion says who and when, or it is not a record of a decision")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select confidence from step_dependency where template_id = ? and kind = 'CONFIRMED'",
                        Double.class,
                        template))
                .as("the rate stops meaning anything once somebody has simply decided")
                .isNull();
    }

    @Test
    void promotingTwiceChangesNothingTheSecondTime() throws Exception {
        RoundTripClient browser = signedIn();
        UUID template = drafts.save(aLaunchObservedEightTimes());
        List<String> steps = stepIdsOf(browser, template);

        assertThat(promote(browser, template, steps.get(1), steps.get(0)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(promote(browser, template, steps.get(1), steps.get(0)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(dependenciesOn(browser, template)).isEqualTo(1);
    }

    @Test
    void promotingSomethingNobodyEverSawChangesNothing() throws Exception {
        RoundTripClient browser = signedIn();
        UUID template = drafts.save(aLaunchObservedEightTimes());
        List<String> steps = stepIdsOf(browser, template);

        assertThat(promote(browser, template, steps.get(2), steps.get(0)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(dependenciesOn(browser, template)).isZero();
    }

    @Test
    void refusesToComposeOnAStepThatWasNeverResolved() throws Exception {
        signedIn();

        DraftProcess unresolved = new DraftProcess(
                "P-2",
                "Half a launch",
                "DRAFT",
                "COMPOSED_FROM_DISCOVERY",
                List.of(new DraftProcess.DraftStep("P-2-S1", "brief", "Brief", "TT-DRAFT-1", "DRAFT", 0, 1, false)),
                List.of(),
                null);

        assertThatThrownBy(() -> drafts.save(unresolved))
                .isInstanceOf(ProcessDraftPort.UnresolvedDraftStepException.class)
                .hasMessageContaining("TT-DRAFT-1");
    }

    private DraftProcess aLaunchObservedEightTimes() {
        DraftProcess.DraftStep brief = step(1, "brief", "Client brief");
        DraftProcess.DraftStep copy = step(2, "copy", "Write the copy");
        DraftProcess.DraftStep design = step(3, "design", "Design the assets");

        return new DraftProcess(
                "P-1",
                "Client launch",
                "DRAFT",
                "COMPOSED_FROM_DISCOVERY",
                List.of(brief, copy, design),
                List.of(
                        new DraftProcess.DraftEdge(copy.id(), brief.id(), "OBSERVED", 0.8),
                        new DraftProcess.DraftEdge(design.id(), copy.id(), "OBSERVED", 0.9)),
                new DraftProcess.Evidence(List.of(), List.of(), 0.9, 0.8, List.of("ran 8 times")));
    }

    private DraftProcess.DraftStep step(int position, String label, String title) {
        return new DraftProcess.DraftStep(
                "P-1-S" + position,
                label,
                title,
                templates.resolve(title, null, maria).toString(),
                "DRAFT",
                0,
                position,
                false);
    }

    private org.springframework.http.ResponseEntity<String> promote(
            RoundTripClient browser, UUID template, String dependent, String dependsOn) throws Exception {
        return browser.post(
                "/api/process-templates/" + template + "/dependencies/promote",
                """
                {"dependentStepId":"%s","dependsOnStepId":"%s"}""".formatted(dependent, dependsOn));
    }

    private int dependenciesOn(RoundTripClient reader, UUID template) throws Exception {
        JsonNode read =
                json.readTree(reader.get("/api/process-templates/" + template).getBody());
        return read.get("dependencies").size();
    }

    private List<String> stepIdsOf(RoundTripClient reader, UUID template) throws Exception {
        JsonNode read =
                json.readTree(reader.get("/api/process-templates/" + template).getBody());
        return read.get("steps").findValues("id").stream().map(JsonNode::asText).toList();
    }

    private RoundTripClient signedIn() throws Exception {
        buildTheCompany();
        maria = jdbc.queryForObject("select id from auth_user where email = ?", UUID.class, OWNER_EMAIL);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        new SessionPrincipal(maria, OWNER_EMAIL),
                        null,
                        List.of("PROCESS_TEMPLATE_AUTHOR", "PROCESS_VIEW_ANY", "TASK_TEMPLATE_CREATE").stream()
                                .map(SimpleGrantedAuthority::new)
                                .map(GrantedAuthority.class::cast)
                                .toList()));
        return signedInBrowser(OWNER_EMAIL);
    }
}
