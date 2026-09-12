package com.flowops.workspace;

import com.flowops.auth.AuthIntegrationTest;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

public abstract class WorkspaceIntegrationTest extends AuthIntegrationTest {
    protected static final String OWNER_EMAIL = "maria@atelier.ro";
    protected static final String OWNER_NAME = "Maria Ionescu";
    protected static final String WORKSPACE_NAME = "Atelier Ionescu";
    protected static final String BUCHAREST = "Europe/Bucharest";

    @MockitoSpyBean
    protected AppendWorkspaceEventPort appendWorkspaceEventPort;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate workspaceJdbc;

    @BeforeEach
    void returnTheWorkspaceToItsSeededState() {
        workspaceJdbc.execute("truncate workspace_event cascade");
        workspaceJdbc.execute("delete from workspace_settings");
        workspaceJdbc.execute("update workspace set name = null, workspace_use = null");
    }

    protected Cookie anOwnerSignedIn() throws Exception {
        return sessionCookieOf(signUp(OWNER_EMAIL, VALID_PASSWORD));
    }

    protected MockHttpServletResponse setUpTheWorkspace(Cookie session) throws Exception {
        return setUpTheWorkspace(session, OWNER_NAME, WORKSPACE_NAME, "WORK", BUCHAREST);
    }

    protected MockHttpServletResponse setUpTheWorkspace(
            Cookie session, String ownerName, String workspaceName, String use, String timezone) throws Exception {
        return mockMvc.perform(requestFor(
                                "/api/workspace/setup",
                                Map.of(
                                        "ownerName", ownerName,
                                        "workspaceName", workspaceName,
                                        "use", use,
                                        "timezone", timezone))
                        .cookie(session))
                .andReturn()
                .getResponse();
    }

    protected String storedWorkspaceName() {
        return workspaceJdbc.queryForObject("select name from workspace", String.class);
    }

    protected String storedWorkspaceUse() {
        return workspaceJdbc.queryForObject("select workspace_use from workspace", String.class);
    }

    protected String storedDisplayNameOf(String email) {
        return workspaceJdbc.queryForObject("select display_name from auth_user where email = ?", String.class, email);
    }

    protected boolean setupIsCompleteFor(String email) {
        return Boolean.TRUE.equals(workspaceJdbc.queryForObject(
                "select setup_completed from auth_user where email = ?", Boolean.class, email));
    }

    protected List<Map<String, Object>> settingsRows() {
        return workspaceJdbc.queryForList("select * from workspace_settings order by effective_from desc");
    }

    protected List<String> recordedWorkspaceActions() {
        return workspaceJdbc.queryForList("select action from workspace_event", String.class);
    }

    protected List<String> everyRecordedWorkspaceEventValue() {
        return workspaceJdbc.query("select * from workspace_event", (rows, rowNumber) -> {
            StringBuilder row = new StringBuilder();
            for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                row.append(rows.getString(column)).append(' ');
            }
            return row.toString();
        });
    }
}
