package com.company.starter.user;

import com.company.starter.user.model.Role;
import com.company.starter.user.model.User;
import com.company.starter.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UsersAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String body(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    /** Registers a USER, then promotes it to ADMIN in the database. */
    private String registerUser(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "password123")))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String promoteToAdminTokenFor(String email) throws Exception {
        // The access token's role claim is irrelevant: authority is read from the
        // DB on every request, so promoting the row makes this token act as ADMIN.
        String token = registerUser(email);
        User u = userRepository.findByEmailAndDisabledAtIsNull(email).orElseThrow();
        u.setRole(Role.ADMIN);
        userRepository.save(u);
        return token;
    }

    private UUID idOf(String email) {
        return userRepository.findByEmailAndDisabledAtIsNull(email).orElseThrow().getId();
    }

    @Test
    void adminCanListUsers() throws Exception {
        String admin = promoteToAdminTokenFor("admin1@example.com");

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.pageIndex").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void adminCanGetUserById() throws Exception {
        String admin = promoteToAdminTokenFor("admin2@example.com");
        UUID id = idOf("admin2@example.com");

        mockMvc.perform(get("/api/v1/users/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin2@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void adminCanChangeAnotherUsersRole() throws Exception {
        String admin = promoteToAdminTokenFor("admin3@example.com");
        registerUser("target3@example.com");
        UUID targetId = idOf("target3@example.com");

        mockMvc.perform(patch("/api/v1/users/" + targetId + "/role")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void adminCannotChangeOwnRole() throws Exception {
        String admin = promoteToAdminTokenFor("admin4@example.com");
        UUID ownId = idOf("admin4@example.com");

        mockMvc.perform(patch("/api/v1/users/" + ownId + "/role")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "USER"))))
                .andExpect(status().isConflict());
    }
}
