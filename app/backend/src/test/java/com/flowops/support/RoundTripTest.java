package com.flowops.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.auth.infrastructure.mail.AsyncMailDispatcher;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("roundtrip")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class RoundTripTest {
    private static final Map<String, String> SEEDED_HASHES = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void useTheContainerDatabase(DynamicPropertyRegistry registry) {
        DatabaseContainer.registerOn(registry);
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    protected AsyncMailDispatcher mailDispatcher;

    @MockitoBean
    protected JavaMailSender mailSender;

    protected RoundTripClient browser;

    protected final String seededHashOf(String passphrase) {
        return SEEDED_HASHES.computeIfAbsent(passphrase, passwordEncoder::encode);
    }

    @BeforeEach
    void openAFreshBrowserOnAnEmptyInstallation() {
        browser = new RoundTripClient(rest);

        jdbc.execute("set lock_timeout = '10s'");

        jdbc.execute("truncate workspace_event cascade");
        jdbc.execute("delete from workspace_settings");
        jdbc.execute("update workspace set name = null, workspace_use = null");
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

        jdbc.execute("delete from task_category");
        jdbc.execute("delete from process_category");

        jdbc.execute("delete from track_type");
        jdbc.execute("delete from counterparty_kind_event");
        jdbc.execute("delete from counterparty");
    }
}
