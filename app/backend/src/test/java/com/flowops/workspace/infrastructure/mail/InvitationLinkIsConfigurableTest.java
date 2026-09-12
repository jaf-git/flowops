package com.flowops.workspace.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

@Tag("WORKSPACE-INVITE-01")
class InvitationLinkIsConfigurableTest {
    private static final String ENVIRONMENT_VARIABLE = "FLOWOPS_INVITATION_ACCEPT_URL";

    @Test
    @SuppressWarnings("unchecked")
    void theInvitationLinkIsDeclaredInConfigurationAndOverridableByTheEnvironment() throws Exception {
        try (InputStream yaml = getClass().getResourceAsStream("/application.yaml")) {
            assertThat(yaml).as("application.yaml is on the test classpath").isNotNull();
            Map<String, Object> root = new Yaml().load(yaml);

            Map<String, Object> flowops = (Map<String, Object>) root.get("flowops");
            Map<String, Object> workspace = (Map<String, Object>) flowops.get("workspace");
            Map<String, Object> invitation = (Map<String, Object>) workspace.get("invitation");
            String acceptUrl = (String) invitation.get("accept-url");

            assertThat(acceptUrl)
                    .as("the emailed invitation link must be settable without rebuilding")
                    .contains("${" + ENVIRONMENT_VARIABLE);
            assertThat(acceptUrl)
                    .as("and its origin must come from the one place an origin is written")
                    .contains("${flowops.web.app-origin}");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void bothEmailedLinksTakeTheirOriginFromOnePlace() throws Exception {
        try (InputStream yaml = getClass().getResourceAsStream("/application.yaml")) {
            Map<String, Object> root = new Yaml().load(yaml);
            Map<String, Object> flowops = (Map<String, Object>) root.get("flowops");

            Map<String, Object> web = (Map<String, Object>) flowops.get("web");
            assertThat((String) web.get("app-origin")).contains("${FLOWOPS_APP_ORIGIN");

            Map<String, Object> auth = (Map<String, Object>) flowops.get("auth");
            Map<String, Object> workspace = (Map<String, Object>) flowops.get("workspace");
            Map<String, Object> invitation = (Map<String, Object>) workspace.get("invitation");

            assertThat((String) auth.get("reset-link-base")).contains("${flowops.web.app-origin}");
            assertThat((String) invitation.get("accept-url")).contains("${flowops.web.app-origin}");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void soIsThePasswordResetLink() throws Exception {
        try (InputStream yaml = getClass().getResourceAsStream("/application.yaml")) {
            Map<String, Object> root = new Yaml().load(yaml);
            Map<String, Object> flowops = (Map<String, Object>) root.get("flowops");
            Map<String, Object> auth = (Map<String, Object>) flowops.get("auth");

            assertThat((String) auth.get("reset-link-base")).contains("${FLOWOPS_RESET_LINK_BASE");
        }
    }
}
