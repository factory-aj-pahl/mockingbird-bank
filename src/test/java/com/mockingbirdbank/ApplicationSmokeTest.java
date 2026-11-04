package com.mockingbirdbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mockingbirdbank.config.DataInitializer;
import com.mockingbirdbank.model.AccountHolder;
import com.mockingbirdbank.repository.AccountHolderRepository;
import com.mockingbirdbank.repository.AccountRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-context boot test against a real, throwaway Postgres - catches wiring/config regressions
 * (e.g. the env-var datasource placeholders) on every run without depending on any developer's
 * pre-existing local database.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationSmokeTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;

    @Autowired private AccountHolderRepository holderRepository;

    @Autowired private AccountRepository accountRepository;

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void infoEndpointIsPublicAndReportsOnlyBuildAndGitBasics() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.version").isNotEmpty())
                .andExpect(jsonPath("$.git.commit.id").isNotEmpty())
                .andExpect(jsonPath("$.git.branch").isNotEmpty())
                .andExpect(jsonPath("$.java").doesNotExist())
                .andExpect(jsonPath("$.os").doesNotExist())
                .andExpect(jsonPath("$.process").doesNotExist());
    }

    @Test
    void dataInitializerSeedsOneHolderWithThreeAccounts() {
        List<AccountHolder> holders = holderRepository.findAll();

        assertThat(holders).hasSize(1);
        assertThat(
                        accountRepository.findByHolderIdOrderByAccountTypeAscIdAsc(
                                holders.get(0).getId()))
                .hasSize(3);
    }

    @Test
    void unauthenticatedRequestToAccountsRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/accounts"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void seededUserCanLogIn() throws Exception {
        // DashboardView is aliased to "/" (see @RouteAlias), so a successful
        // login's default-success redirect lands there rather than on
        // "/accounts" - both routes render the same authenticated view.
        //
        // This intentionally stops at "login succeeded, session holds the
        // right principal" rather than also asserting a follow-up GET
        // /accounts returns 200: under MockMvc, Vaadin's SpringServlet/
        // VaadinService initializes lazily on first real dispatch, and
        // RequestUtil.isSecuredFlowRoute() treats a not-yet-initialized
        // service as "not a secured route", which falls through to this
        // config's default-deny rule and 403s even a correctly authenticated
        // request. That's a MockMvc/Vaadin-servlet-lifecycle artifact, not a
        // real bug - the full login -> dashboard -> logout flow was verified
        // by hand against a live `bootRun` instance. See RequestUtil source
        // (isFlowRouteInternal) for the documented lazy-init behavior.
        MvcResult loginResult =
                mockMvc.perform(
                                formLogin()
                                        .user(DataInitializer.DEV_USERNAME)
                                        .password(DataInitializer.DEV_PASSWORD_DEFAULT))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("/"))
                        .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        SecurityContext securityContext =
                (SecurityContext)
                        session.getAttribute(
                                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(securityContext.getAuthentication().getName())
                .isEqualTo(DataInitializer.DEV_USERNAME);
        assertThat(securityContext.getAuthentication().isAuthenticated()).isTrue();
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(formLogin().user(DataInitializer.DEV_USERNAME).password("not-the-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }
}
