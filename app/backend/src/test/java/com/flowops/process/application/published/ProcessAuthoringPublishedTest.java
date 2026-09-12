package com.flowops.process.application.published;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.infrastructure.session.SessionPrincipal;
import com.flowops.shared.published.PublishedRefusal;
import com.flowops.shared.published.RefusalKind;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@Tag("AI-INSIGHT-ACT-ON-INSIGHT-01")
class ProcessAuthoringPublishedTest extends CompanyScenarioTest {
    @Autowired
    private ProcessAuthoringUseCase processes;

    @Autowired
    private TemplateResolutionUseCase templates;

    @AfterEach
    void forgetWhoWasCalling() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void putsTheStepWhereTheFindingSaidAndNotAtTheEnd() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplateOfThreeSteps(maria);

        callingAs(company().maria(), "PROCESS_TEMPLATE_EDIT", "PROCESS_VIEW_ANY");
        processes.insertStep(template, 1, aStepFor("Send reminder"));

        assertThat(stepTitlesOf(maria, template))
                .as("between Discovery call and Proposal is where eight of twelve runs put it")
                .containsExactly("Discovery call", "Send reminder", "Proposal", "Contract");
    }

    @Test
    void clampsAPositionPastTheEndRatherThanRefusingTheFinding() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplateOfThreeSteps(maria);

        callingAs(company().maria(), "PROCESS_TEMPLATE_EDIT", "PROCESS_VIEW_ANY");
        processes.insertStep(template, 99, aStepFor("Follow up"));

        assertThat(stepTitlesOf(maria, template)).last().isEqualTo("Follow up");
    }

    @Test
    void leavesTheStepsThatWereAlreadyThereWithTheirOwnIdentities() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplateOfThreeSteps(maria);
        List<String> before = stepIdsOf(maria, template);

        callingAs(company().maria(), "PROCESS_TEMPLATE_EDIT", "PROCESS_VIEW_ANY");
        processes.insertStep(template, 1, aStepFor("Send reminder"));

        assertThat(stepIdsOf(maria, template))
                .as("a recreated step loses every dependency the graph has drawn against it")
                .containsAll(before);
    }

    @Test
    void refusesANonAuthorInWordsTheNeighbourCanRender() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID mariasTemplate = aTemplateOfThreeSteps(maria);

        callingAs(company.ionut(), "PROCESS_TEMPLATE_EDIT");

        assertThatThrownBy(() -> processes.insertStep(mariasTemplate, 1, aStepFor("Send reminder")))
                .isInstanceOfSatisfying(PublishedRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo("NOT_THE_AUTHOR");
                    assertThat(refusal.kind()).isEqualTo(RefusalKind.NOT_PERMITTED);
                    assertThat(refusal.getMessage())
                            .as("PROCESS's own sentence, which its own endpoint returns for the same rule")
                            .isEqualTo(
                                    "That process is somebody else's to change. Ask whoever wrote it, or the owner.");
                });
    }

    @Test
    void refusesAnAbsentTemplateAsAbsent() throws Exception {
        Company company = buildTheCompany();

        callingAs(company.maria(), "PROCESS_TEMPLATE_EDIT", "PROCESS_VIEW_ANY");

        assertThatThrownBy(() -> processes.insertStep(UUID.randomUUID(), 0, aStepFor("Anything")))
                .isInstanceOfSatisfying(PublishedRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo("TEMPLATE_NOT_FOUND");
                    assertThat(refusal.kind()).isEqualTo(RefusalKind.NOT_FOUND);
                });
    }

    @Test
    void refusesACallerWithoutTheEditPermission() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplateOfThreeSteps(maria);

        callingAs(company.maria(), "AI_INSIGHT_VIEW");

        assertThatThrownBy(() -> processes.insertStep(template, 1, aStepFor("Send reminder")))
                .as("reading a finding about a template is not permission to edit it")
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void applyingTheSameFindingTwiceLeavesOneTemplateAndOneStep() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplateOfThreeSteps(maria);

        callingAs(company().maria(), "PROCESS_TEMPLATE_EDIT", "PROCESS_VIEW_ANY");
        processes.insertStep(template, 1, aStepFor("Trimite reminder"));
        processes.insertStep(template, 1, aStepFor("Trimite reminder"));

        assertThat(stepTitlesOf(maria, template))
                .as("the second Apply must not add a second step")
                .containsExactly("Discovery call", "Trimite reminder", "Proposal", "Contract");
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_template where lower(btrim(title)) = 'trimite reminder'",
                        Integer.class))
                .as("nor a second template -- find-before-create, and V56 under it")
                .isEqualTo(1);
    }

    @Test
    void resolvesToATemplateThatAlreadyMeansTheSameWork() throws Exception {
        buildTheCompany();
        UUID first = templates.resolve("Trimite reminderul", null, company().maria());
        UUID again = templates.resolve("Trimite reminder", null, company().maria());

        assertThat(again)
                .as("close enough to be the same work, so no second row is created")
                .isEqualTo(first);
    }

    @Test
    void saysNothingWhenAskedToRemoveAnEdgeThatIsNotThere() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplateOfThreeSteps(maria);

        callingAs(company().maria(), "PROCESS_TEMPLATE_AUTHOR", "PROCESS_VIEW_ANY");
        processes.removeDependency(template, "Proposal", "Discovery call");

        assertThat(stepTitlesOf(maria, template)).containsExactly("Discovery call", "Proposal", "Contract");
    }

    @Test
    void writesAnObservedEdgeThatCarriesItsConfidenceAndBlocksNothing() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplateOfThreeSteps(maria);

        callingAs(company().maria(), "PROCESS_TEMPLATE_AUTHOR", "PROCESS_VIEW_ANY");
        processes.addDependency(template, "Proposal", "Discovery call", "OBSERVED", 0.83);

        assertThat(jdbc.queryForObject(
                        "select kind from step_dependency where template_id = ?", String.class, template))
                .isEqualTo("OBSERVED");
        assertThat(jdbc.queryForObject(
                        "select confidence from step_dependency where template_id = ?", Double.class, template))
                .as("how often the order held -- a person reads this before deciding it is real")
                .isEqualTo(0.83);
        assertThat(edgesTheDomainCanSee(template))
                .as("only CONFIRMED edges reach the graph, so an observed one can never strand a step")
                .isZero();
    }

    @Test
    void acceptsAnObservedCycleBecauseObservationIsNotDecision() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplateOfThreeSteps(maria);

        callingAs(company().maria(), "PROCESS_TEMPLATE_AUTHOR", "PROCESS_VIEW_ANY");
        processes.addDependency(template, "Proposal", "Discovery call", "OBSERVED", 0.7);
        processes.addDependency(template, "Discovery call", "Proposal", "OBSERVED", 0.3);

        assertThat(jdbc.queryForObject(
                        "select count(*) from step_dependency where template_id = ?", Integer.class, template))
                .as("both directions were observed, and neither is a claim about what must happen")
                .isEqualTo(2);
    }

    @Test
    void drawsOneRowWhenTheSameObservedEdgeArrivesTwice() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplateOfThreeSteps(maria);

        callingAs(company().maria(), "PROCESS_TEMPLATE_AUTHOR", "PROCESS_VIEW_ANY");
        processes.addDependency(template, "Proposal", "Discovery call", "OBSERVED", 0.83);
        processes.addDependency(template, "Proposal", "Discovery call", "OBSERVED", 0.83);

        assertThat(jdbc.queryForObject(
                        "select count(*) from step_dependency where template_id = ?", Integer.class, template))
                .isEqualTo(1);
    }

    @Test
    void saysNothingWhenAnObservedEdgeNamesAStepThatIsNotThere() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplateOfThreeSteps(maria);

        callingAs(company().maria(), "PROCESS_TEMPLATE_AUTHOR", "PROCESS_VIEW_ANY");
        processes.addDependency(template, "Proposal", "Photo shoot", "OBSERVED", 0.6);

        assertThat(jdbc.queryForObject(
                        "select count(*) from step_dependency where template_id = ?", Integer.class, template))
                .isZero();
    }

    @Test
    void leavesAnEdgeDrawnByHandConfirmedAndBlocking() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        UUID template = aTemplateOfThreeSteps(maria);

        List<String> steps = stepIdsOf(maria, template);
        var drawn = maria.post(
                "/api/process-templates/" + template + "/dependencies",
                """
                {"dependentStepId":"%s","dependsOnStepId":"%s"}"""
                        .formatted(steps.get(1), steps.get(0)));
        assertThat(drawn.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject(
                        "select kind from step_dependency where template_id = ?", String.class, template))
                .isEqualTo("CONFIRMED");
        assertThat(edgesTheDomainCanSee(template))
                .as("a person decided this one, so it blocks")
                .isEqualTo(1);
    }

    private Integer edgesTheDomainCanSee(UUID template) {
        return jdbc.queryForObject(
                "select count(*) from step_dependency where template_id = ? and kind = 'CONFIRMED'",
                Integer.class,
                template);
    }

    private Company theCompany;

    private Company company() {
        return theCompany;
    }

    @Override
    protected Company buildTheCompany() throws Exception {
        theCompany = super.buildTheCompany();
        return theCompany;
    }

    private void callingAs(UUID userId, String... authorities) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        new SessionPrincipal(userId, "somebody@atelier.ro"),
                        null,
                        List.of(authorities).stream()
                                .map(SimpleGrantedAuthority::new)
                                .map(org.springframework.security.core.GrantedAuthority.class::cast)
                                .toList()));
    }

    private ProcessAuthoringUseCase.StepSpecification aStepFor(String work) {
        return new ProcessAuthoringUseCase.StepSpecification(
                templates.resolve(work, null, company().maria()), null);
    }

    private UUID aTemplateOfThreeSteps(RoundTripClient author) throws Exception {
        var created = author.post(
                "/api/process-templates",
                """
                {"name":"Client onboarding","overview":"first conversation to work starting","steps":[
                  {"taskTemplateId":"%s","expectedDurationHours":2},
                  {"taskTemplateId":"%s","expectedDurationHours":4},
                  {"taskTemplateId":"%s","expectedDurationHours":3}
                ]}"""
                        .formatted(
                                templates.resolve(
                                        "Discovery call", null, company().maria()),
                                templates.resolve("Proposal", null, company().maria()),
                                templates.resolve("Contract", null, company().maria())));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(json.readTree(created.getBody()).get("id").asText());
    }

    private List<String> stepTitlesOf(RoundTripClient reader, UUID template) throws Exception {
        return fieldOfEachStep(reader, template, "title");
    }

    private List<String> stepIdsOf(RoundTripClient reader, UUID template) throws Exception {
        return fieldOfEachStep(reader, template, "id");
    }

    private List<String> fieldOfEachStep(RoundTripClient reader, UUID template, String field) throws Exception {
        JsonNode read =
                json.readTree(reader.get("/api/process-templates/" + template).getBody());
        return read.get("steps").findValues(field).stream()
                .map(JsonNode::asText)
                .toList();
    }
}
