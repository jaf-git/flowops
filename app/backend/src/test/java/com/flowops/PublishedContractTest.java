package com.flowops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowops.auth.AuthIntegrationTest;
import org.junit.jupiter.api.Test;

class PublishedContractTest extends AuthIntegrationTest {
    @Test
    void theSpecificationIsServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(result -> assertThat(
                        result.getResponse().getContentAsString())
                .contains("/api/auth/signup")
                .contains("AUTH-REGISTER-OWNER-01"));
    }

    @Test
    void theBrowserInterfaceLoadsItsAssetsRatherThanAnsweringAFault() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void theInterfaceEntryPointRedirectsRatherThanFailing() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void aMissingAssetUnderAPermittedPathIsNotFoundRatherThanAFault() throws Exception {
        mockMvc.perform(get("/swagger-ui/there-is-no-such-asset.js")).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownAddressTellsAnAnonymousCallerNothingAboutWhetherItExists() throws Exception {
        mockMvc.perform(get("/there-is-nothing-here")).andExpect(status().isUnauthorized());
    }
}
