package com.flowops.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.infrastructure.mail.AsyncMailDispatcher;
import com.flowops.auth.infrastructure.persistence.repository.AuthCredentialJpaRepository;
import com.flowops.auth.infrastructure.persistence.repository.AuthEventJpaRepository;
import com.flowops.auth.infrastructure.persistence.repository.AuthLoginAttemptJpaRepository;
import com.flowops.auth.infrastructure.persistence.repository.AuthSessionMetadataJpaRepository;
import com.flowops.auth.infrastructure.persistence.repository.AuthSignupPasscodeJpaRepository;
import com.flowops.auth.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.flowops.support.DatabaseContainer;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class AuthIntegrationTest {
    protected static final String CLIENT_ADDRESS = "127.0.0.1";

    protected static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0";
    protected static final String VALID_PASSWORD = "a-long-enough-passphrase";

    protected static final String EMPLOYEE_EMAIL = "ionut@atelier.ro";

    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @DynamicPropertySource
    static void useTheContainerDatabase(DynamicPropertyRegistry registry) {
        DatabaseContainer.registerOn(registry);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected AuthUserJpaRepository users;

    @Autowired
    protected AuthCredentialJpaRepository credentials;

    @Autowired
    protected AuthEventJpaRepository events;

    @Autowired
    protected AuthSignupPasscodeJpaRepository passcodes;

    @Autowired
    protected AuthSessionMetadataJpaRepository sessionMetadata;

    @Autowired
    protected AuthLoginAttemptJpaRepository loginAttempts;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected AsyncMailDispatcher mailDispatcher;

    @MockitoSpyBean
    protected AppendAuthEventPort appendAuthEventPort;

    @BeforeEach
    void emptyTheTablesTheTestsWriteTo() {
        jdbc.execute(
                """
                truncate workspace_invitation,
                         workspace_membership,
                         auth_event,
                         auth_login_attempt,
                         auth_session_metadata,
                         auth_signup_passcode,
                         auth_credential,
                         auth_user_permission,
                         auth_user,
                         spring_session_attributes,
                         spring_session cascade
                """);
    }

    protected String passcodeIssuedTo(String email) throws Exception {
        mockMvc.perform(requestFor("/api/auth/signup/passcode", Map.of("email", email)));
        Matcher code = PASSCODE_IN_BODY.matcher(lastMessageBody());
        if (!code.find()) {
            throw new AssertionError("no passcode was delivered to " + email);
        }
        return code.group(1);
    }

    protected String lastMessageBody() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher, atLeastOnce()).send(any(EmailAddress.class), anyString(), body.capture());
        return body.getAllValues().getLast();
    }

    protected MockHttpServletResponse signUp(String email, String password) throws Exception {
        String passcode = passcodeIssuedTo(email);
        return mockMvc.perform(requestFor(
                        "/api/auth/signup", Map.of("email", email, "passcode", passcode, "password", password)))
                .andReturn()
                .getResponse();
    }

    protected MockHttpServletResponse logIn(String email, String password) throws Exception {
        return mockMvc.perform(requestFor("/api/auth/login", Map.of("email", email, "password", password)))
                .andReturn()
                .getResponse();
    }

    protected MockHttpServletResponse logOut(Cookie session) throws Exception {
        return mockMvc.perform(requestFor("/api/auth/logout", Map.of()).cookie(session))
                .andReturn()
                .getResponse();
    }

    protected Cookie sessionCookieOf(MockHttpServletResponse response) {
        return response.getCookie("SESSION");
    }

    protected String sessionIdOf(Cookie session) {
        return new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.UTF_8);
    }

    protected MockHttpServletRequestBuilder requestFor(String path, Map<String, String> body) throws Exception {
        return post(path)
                .with(csrf())
                .header("User-Agent", USER_AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    protected Cookie anEmployeeSignedIn() throws Exception {
        UUID employeeId = UUID.randomUUID();
        jdbc.update(
                """
                insert into auth_user (id, email, account_state, role_name, created_at)
                values (?, ?, 'ACTIVE', 'EMPLOYEE', now())
                """,
                employeeId,
                EMPLOYEE_EMAIL);
        jdbc.update(
                """
                insert into auth_credential (user_id, password_hash, algorithm, updated_at)
                select ?, password_hash, algorithm, updated_at from auth_credential limit 1
                """,
                employeeId);
        return sessionCookieOf(logIn(EMPLOYEE_EMAIL, VALID_PASSWORD));
    }

    protected UUID employeeId() {
        return jdbc.queryForObject("select id from auth_user where email = ?", UUID.class, EMPLOYEE_EMAIL);
    }

    protected UUID ownerId(String email) {
        return jdbc.queryForObject("select id from auth_user where email = ?", UUID.class, email);
    }

    protected String referenceOfSessionBehind(Cookie session) {
        return jdbc.queryForObject(
                "select reference from auth_session_metadata where session_id = ?", String.class, sessionIdOf(session));
    }

    protected List<String> referencesIn(MockHttpServletResponse response) throws Exception {
        JsonNode listed = json.readTree(response.getContentAsString());
        List<String> references = new ArrayList<>();
        listed.forEach(entry -> references.add(entry.get("reference").asText()));
        return references;
    }

    protected List<String> referencesMarkedCurrentIn(MockHttpServletResponse response) throws Exception {
        JsonNode listed = json.readTree(response.getContentAsString());
        List<String> references = new ArrayList<>();
        listed.forEach(entry -> {
            if (entry.get("current").asBoolean()) {
                references.add(entry.get("reference").asText());
            }
        });
        return references;
    }

    protected int countOf(String body, String fragment) {
        return body.split(Pattern.quote(fragment), -1).length - 1;
    }

    protected List<String> recordedActions() {
        return events.findAll().stream().map(event -> event.getAction()).toList();
    }

    protected List<String> everyRecordedEventValue() {
        return jdbc.query("select * from auth_event", (rows, rowNumber) -> {
            StringBuilder row = new StringBuilder();
            for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                row.append(rows.getString(column)).append(' ');
            }
            return row.toString();
        });
    }
}
