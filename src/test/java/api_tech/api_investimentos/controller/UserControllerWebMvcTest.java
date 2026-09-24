package api_tech.api_investimentos.controller;

import api_tech.api_investimentos.entity.User;
import api_tech.api_investimentos.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    void shouldCreateUserWithoutExposingPassword() throws Exception {
        var user = sampleUser();
        var request = new CreateUserDto(user.getUsername(), user.getEmail(), "plain-password");

        when(userService.createUser(request)).thenReturn(user.getId());
        when(userService.getUserById(user.getId())).thenReturn(Optional.of(user));

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/users/" + user.getId()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.username").value(user.getUsername()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldReturnProblemDetailsForInvalidCreateRequestWithoutEchoingPassword() throws Exception {
        var request = new CreateUserDto("ab", "invalid-email", "p4ss");

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:api-investimentos:validation-error"))
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("One or more request fields are invalid."))
                .andExpect(jsonPath("$.instance").value("/v1/users"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("username", "email", "password")))
                .andExpect(content().string(not(containsString("\"p4ss\""))));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturnProblemDetailsForMalformedJson() throws Exception {
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:api-investimentos:malformed-request"))
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/v1/users"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturnProblemDetailsForInvalidUserId() throws Exception {
        mockMvc.perform(get("/v1/users/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:api-investimentos:invalid-parameter"))
                .andExpect(jsonPath("$.title").value("Invalid request parameter"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("The parameter 'id' has an invalid format."))
                .andExpect(jsonPath("$.instance").value("/v1/users/not-a-uuid"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturnProblemDetailsWhenUserDoesNotExist() throws Exception {
        var id = UUID.randomUUID();
        when(userService.getUserById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/users/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:api-investimentos:resource-not-found"))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("User was not found."))
                .andExpect(jsonPath("$.instance").value("/v1/users/" + id));
    }

    @Test
    void shouldPartiallyUpdateUser() throws Exception {
        var id = UUID.randomUUID();
        var request = new UpdateUserDto("new-username", null);

        mockMvc.perform(patch("/v1/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userService).updateUserById(
                eq(id),
                argThat(dto -> dto.username().equals("new-username") && dto.password() == null)
        );
    }

    private static User sampleUser() {
        return new User(
                UUID.randomUUID(),
                "lindembergue",
                "dev@example.com",
                "encoded-password",
                Instant.parse("2026-09-24T00:00:00Z"),
                null
        );
    }
}
