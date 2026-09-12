package com.flowops.auth.application.completesetup;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.workspace.WorkspaceIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("AUTH-LOGIN-01")
class SetupRoutingIntegrationTest extends WorkspaceIntegrationTest {
    private static final String EMAIL = "elena@atelier.ro";
    private static final String PASSWORD = "corect-cal-baterie-capsator";

    @Test
    void aFreshlyRegisteredOwnerIsRoutedToSetupOverHttp() throws Exception {
        MockHttpServletResponse response = signUp(EMAIL, PASSWORD);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).contains("\"landingTarget\":\"WORKSPACE_SETUP\"");
    }

    @Test
    void theFlagSurvivesTheRoundTripThroughPostgres() throws Exception {
        signUp(EMAIL, PASSWORD);

        assertThat(users.findAll()).singleElement().satisfies(row -> assertThat(row.isSetupCompleted())
                .isFalse());

        MockHttpServletResponse login = logIn(EMAIL, PASSWORD);

        assertThat(login.getContentAsString()).contains("\"landingTarget\":\"WORKSPACE_SETUP\"");
    }

    @Test
    void completingSetupChangesWhereTheNextLoginLands() throws Exception {
        Cookie session = signUp(EMAIL, PASSWORD).getCookie("SESSION");

        MockHttpServletResponse completed = setUpTheWorkspace(session);

        assertThat(completed.getStatus()).isEqualTo(200);
        assertThat(users.findAll()).singleElement().satisfies(row -> assertThat(row.isSetupCompleted())
                .isTrue());
        assertThat(logIn(EMAIL, PASSWORD).getContentAsString()).contains("\"landingTarget\":\"TRIAGE\"");
    }

    @Test
    void completingSetupIsRecorded() throws Exception {
        Cookie session = signUp(EMAIL, PASSWORD).getCookie("SESSION");

        setUpTheWorkspace(session);

        assertThat(recordedActions()).contains(AuthAction.SETUP_COMPLETED.name());
    }

    @Test
    void completingSetupTwiceSucceedsAndRecordsItOnlyOnce() throws Exception {
        Cookie session = signUp(EMAIL, PASSWORD).getCookie("SESSION");

        setUpTheWorkspace(session);
        MockHttpServletResponse second = setUpTheWorkspace(session);

        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(recordedActions().stream()
                        .filter(AuthAction.SETUP_COMPLETED.name()::equals)
                        .count())
                .isEqualTo(1);
    }

    @Test
    void aCallerWithNoSessionCannotCompleteSetup() throws Exception {
        signUp(EMAIL, PASSWORD);

        MockHttpServletResponse response = mockMvc.perform(requestFor(
                        "/api/workspace/setup",
                        Map.of(
                                "ownerName", OWNER_NAME,
                                "workspaceName", WORKSPACE_NAME,
                                "use", "WORK",
                                "timezone", BUCHAREST)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(users.findAll()).singleElement().satisfies(row -> assertThat(row.isSetupCompleted())
                .isFalse());
    }

    @Test
    void thereIsNoEndpointThatMarksSetupCompleteOnItsOwn() throws Exception {
        Cookie session = signUp(EMAIL, PASSWORD).getCookie("SESSION");

        MockHttpServletResponse response = mockMvc.perform(
                        requestFor("/api/auth/setup/complete", Map.of()).cookie(session))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(users.findAll()).singleElement().satisfies(row -> assertThat(row.isSetupCompleted())
                .isFalse());
        assertThat(storedWorkspaceName()).isNull();
    }

    @Test
    void registrationAsksForNoWorkspace() throws Exception {
        signUp(EMAIL, PASSWORD);

        assertThat(recordedActions()).doesNotContain(AuthAction.WORKSPACE_CREATION_REQUESTED.name());
    }
}
