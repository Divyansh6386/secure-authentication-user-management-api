package com.company.starter.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String body(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "password123")))
                .andReturn();
    }

    private String accessTokenFor(String email) throws Exception {
        return tokensFor(email).get("accessToken").asText();
    }

    private com.fasterxml.jackson.databind.JsonNode tokensFor(String email) throws Exception {
        String response = register(email).getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    /** Performs a refresh, asserts 200, and returns the rotated refresh token. */
    private String doRefresh(String refreshToken) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("refreshToken").asText();
    }

    @Test
    void registerReturns201WithTokensAndUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("new@example.com", "password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void duplicateRegistrationReturns409() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dupe@example.com", "password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dupe@example.com", "password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidPayloadReturns422WithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-an-email", "short")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isNotEmpty());
    }

    @Test
    void profileRequiresABearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileReturnsTheCallerWithAValidToken() throws Exception {
        String token = accessTokenFor("me@example.com");

        mockMvc.perform(get("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void adminOnlyEndpointIsForbiddenForANormalUser() throws Exception {
        String token = accessTokenFor("plain@example.com");

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginSucceedsWithCorrectCredentialsAndFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("login@example.com", "password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("login@example.com", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("login@example.com", "wrong-password")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void garbageBearerTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/profile")
                        .header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());

        String unauthorized = mockMvc.perform(get("/api/v1/auth/profile")
                        .header("Authorization", "Bearer not.a.jwt"))
                .andReturn().getResponse().getContentAsString();
        assertThat(unauthorized).contains("AUTH_UNAUTHORIZED");
    }

    @Test
    void refreshReturnsNewTokens() throws Exception {
        String refresh = tokensFor("refresh@example.com").get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void logoutAllInvalidatesExistingAccessToken() throws Exception {
        String access = tokensFor("logoutall@example.com").get("accessToken").asText();

        // The token works before logout-all.
        mockMvc.perform(get("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        // logout-all bumps the user's tokenVersion.
        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        // The same token is now rejected (version mismatch).
        mockMvc.perform(get("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotationIssuesAWorkingNewToken() throws Exception {
        String r0 = tokensFor("rotate1@example.com").get("refreshToken").asText();

        // First refresh rotates r0 -> r1.
        String r1 = doRefresh(r0);

        // The rotated token is itself valid and rotates again.
        doRefresh(r1);
    }

    @Test
    void reusingARotatedRefreshTokenIsRejected() throws Exception {
        String r0 = tokensFor("rotate2@example.com").get("refreshToken").asText();

        // Consume r0 once (rotates to a new token).
        doRefresh(r0);

        // Replaying the now-revoked r0 is reuse -> 401.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", r0))))
                .andExpect(status().isUnauthorized());
    }
}
