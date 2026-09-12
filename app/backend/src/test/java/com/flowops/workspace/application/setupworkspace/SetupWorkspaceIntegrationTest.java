package com.flowops.workspace.application.setupworkspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.flowops.workspace.WorkspaceIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("WORKSPACE-SETUP-01")
class SetupWorkspaceIntegrationTest extends WorkspaceIntegrationTest {
    @Test
    void allFourAnswersAreStoredAndSetupIsMarkedComplete() throws Exception {
        Cookie session = anOwnerSignedIn();

        MockHttpServletResponse response = setUpTheWorkspace(session);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(storedWorkspaceName()).isEqualTo(WORKSPACE_NAME);
        assertThat(storedWorkspaceUse()).isEqualTo("WORK");
        assertThat(storedDisplayNameOf(OWNER_EMAIL)).isEqualTo(OWNER_NAME);
        assertThat(settingsRows())
                .singleElement()
                .extracting(row -> row.get("timezone"))
                .isEqualTo(BUCHAREST);
        assertThat(setupIsCompleteFor(OWNER_EMAIL)).isTrue();
        assertThat(recordedWorkspaceActions()).containsExactly("SETUP_COMPLETED");
    }

    @Test
    void whenTheLastWriteFailsNothingIsStoredAndSetupIsStillIncomplete() throws Exception {
        Cookie session = anOwnerSignedIn();
        doThrow(new IllegalStateException("the event store is unreachable"))
                .when(appendWorkspaceEventPort)
                .append(any());

        setUpTheWorkspace(session);

        assertThat(storedWorkspaceName()).isNull();
        assertThat(storedWorkspaceUse()).isNull();
        assertThat(storedDisplayNameOf(OWNER_EMAIL)).isNull();
        assertThat(settingsRows()).isEmpty();
        assertThat(setupIsCompleteFor(OWNER_EMAIL)).isFalse();
        assertThat(recordedWorkspaceActions()).isEmpty();
    }

    @Test
    void anAbandonedSetupLeavesTheInstallationClaimedAndReturnsTheOwnerToSetup() throws Exception {
        anOwnerSignedIn();

        MockHttpServletResponse signedInAgain = logIn(OWNER_EMAIL, VALID_PASSWORD);

        assertThat(signedInAgain.getStatus()).isEqualTo(200);
        assertThat(json.readTree(signedInAgain.getContentAsString())
                        .get("landingTarget")
                        .asText())
                .isEqualTo("WORKSPACE_SETUP");
        assertThat(storedWorkspaceName()).isNull();
    }

    @Test
    void onceSetupIsDoneTheOwnerIsNoLongerRoutedToIt() throws Exception {
        Cookie session = anOwnerSignedIn();
        setUpTheWorkspace(session);

        MockHttpServletResponse prefill = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/workspace/setup")
                                .cookie(session))
                .andReturn()
                .getResponse();
        MockHttpServletResponse signedInAgain = logIn(OWNER_EMAIL, VALID_PASSWORD);

        assertThat(json.readTree(prefill.getContentAsString())
                        .get("setupCompleted")
                        .asBoolean())
                .isTrue();
        assertThat(json.readTree(signedInAgain.getContentAsString())
                        .get("landingTarget")
                        .asText())
                .isNotEqualTo("WORKSPACE_SETUP");
    }

    @Test
    void anEmptyWorkspaceNameIsRefusedAndTheFieldIsNamed() throws Exception {
        Cookie session = anOwnerSignedIn();

        MockHttpServletResponse response = setUpTheWorkspace(session, OWNER_NAME, "   ", "WORK", BUCHAREST);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("workspaceName");
        assertThat(storedWorkspaceName()).isNull();
    }

    @Test
    void anEmptyOwnerNameIsRefusedAndTheFieldIsNamed() throws Exception {
        Cookie session = anOwnerSignedIn();

        MockHttpServletResponse response = setUpTheWorkspace(session, "   ", WORKSPACE_NAME, "WORK", BUCHAREST);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("ownerName");
        assertThat(storedDisplayNameOf(OWNER_EMAIL)).isNull();
    }

    @Test
    void aTimezoneTheRuntimeDoesNotKnowIsRefusedAndNothingIsStored() throws Exception {
        Cookie session = anOwnerSignedIn();

        MockHttpServletResponse response =
                setUpTheWorkspace(session, OWNER_NAME, WORKSPACE_NAME, "WORK", "Europe/Atlantis");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("timezone");
        assertThat(settingsRows()).isEmpty();
        assertThat(setupIsCompleteFor(OWNER_EMAIL)).isFalse();
    }

    @Test
    void theOwnersNameIsReadableOnTheirAccountAfterwards() throws Exception {
        Cookie session = anOwnerSignedIn();

        setUpTheWorkspace(session, "Ionuț Ștefănescu", WORKSPACE_NAME, "WORK", BUCHAREST);

        assertThat(storedDisplayNameOf(OWNER_EMAIL)).isEqualTo("Ionuț Ștefănescu");
    }

    @Test
    void theFirstSettingsRowIsWrittenInForceWithNoEndInstant() throws Exception {
        Cookie session = anOwnerSignedIn();

        setUpTheWorkspace(session);

        Map<String, Object> row = settingsRows().getFirst();
        assertThat(row.get("effective_from")).isNotNull();
        assertThat(row.get("effective_to")).isNull();
    }

    @Test
    void theEventRecordNamesNobody() throws Exception {
        Cookie session = anOwnerSignedIn();

        setUpTheWorkspace(session);

        assertThat(everyRecordedWorkspaceEventValue()).isNotEmpty().allSatisfy(row -> assertThat(row)
                .doesNotContain(OWNER_NAME)
                .doesNotContain(WORKSPACE_NAME)
                .doesNotContain(OWNER_EMAIL));
    }

    @Test
    void aSecondSetupChangesNothingAndAppendsNoSecondEvent() throws Exception {
        Cookie session = anOwnerSignedIn();
        setUpTheWorkspace(session);

        MockHttpServletResponse replay =
                setUpTheWorkspace(session, "Someone Else", "A Different Name", "PERSONAL", "Europe/London");

        assertThat(replay.getStatus()).isEqualTo(200);
        assertThat(storedWorkspaceName()).isEqualTo(WORKSPACE_NAME);
        assertThat(storedWorkspaceUse()).isEqualTo("WORK");
        assertThat(storedDisplayNameOf(OWNER_EMAIL)).isEqualTo(OWNER_NAME);
        assertThat(settingsRows()).hasSize(1);
        assertThat(recordedWorkspaceActions()).containsExactly("SETUP_COMPLETED");
    }

    @Test
    void someoneWhoDoesNotOwnSetupIsRefusedAndNothingIsStored() throws Exception {
        anOwnerSignedIn();
        Cookie employee = anEmployeeSignedIn();

        MockHttpServletResponse response = setUpTheWorkspace(employee);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("SETUP_NOT_PERMITTED");
        assertThat(storedWorkspaceName()).isNull();
        assertThat(storedDisplayNameOf(EMPLOYEE_EMAIL)).isNull();
    }

    @Test
    void someoneWhoDoesNotOwnSetupIsRefusedThePrefillToo() throws Exception {
        anOwnerSignedIn();
        Cookie employee = anEmployeeSignedIn();

        MockHttpServletResponse response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/workspace/setup")
                                .cookie(employee))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("SETUP_NOT_PERMITTED");
    }

    @Test
    void thePersonalAnswerIsStoredAndChangesNothingElse() throws Exception {
        Cookie session = anOwnerSignedIn();

        MockHttpServletResponse response =
                setUpTheWorkspace(session, OWNER_NAME, WORKSPACE_NAME, "PERSONAL", BUCHAREST);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(storedWorkspaceUse()).isEqualTo("PERSONAL");
        assertThat(storedWorkspaceName()).isEqualTo(WORKSPACE_NAME);
        assertThat(setupIsCompleteFor(OWNER_EMAIL)).isTrue();
        assertThat(recordedWorkspaceActions()).containsExactly("SETUP_COMPLETED");
        assertThat(settingsRows())
                .singleElement()
                .extracting(row -> row.get("timezone"))
                .isEqualTo(BUCHAREST);
    }
}
