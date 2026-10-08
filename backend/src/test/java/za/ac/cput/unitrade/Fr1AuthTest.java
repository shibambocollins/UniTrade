package za.ac.cput.unitrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.domain.User;
import za.ac.cput.unitrade.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR1 Authentication, tested through the real HTTP layer (MockMvc) on in-memory H2.
 * Each test rolls back its database changes (@Transactional), so tests do not affect each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fr1AuthTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository users;

    private ResultActions register(String name, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fullName", name, "email", email, "password", password))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String tokenFrom(ResultActions result) throws Exception {
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return body.get("token").asText();
    }

    @Test
    void fr1_01_registerRejectsNonStudentEmail() throws Exception {
        register("Jane Dlamini", "jane@gmail.com", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.email").value("Use your university email address ending in @mycput.ac.za"));
        assertEquals(0, users.count());
    }

    @Test
    void fr1_02_registerRejectsLookalikeDomain() throws Exception {
        // The whole domain after "@" must match: a longer name that merely ends the same way is not a student address
        register("Eve", "x@evilmycput.ac.za", PASSWORD).andExpect(status().isBadRequest());
        register("Eve", "x@mycput.ac.za.evil.com", PASSWORD).andExpect(status().isBadRequest());
        register("Eve", "x@y@mycput.ac.za", PASSWORD).andExpect(status().isBadRequest());
        assertEquals(0, users.count());
    }

    @Test
    void fr1_03_registerStoresBcryptHashNormalisedEmailAndReturnsToken() throws Exception {
        ResultActions result = register("  Jane Dlamini ", "  Jane.Dlamini@MyCPUT.ac.za ", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("jane.dlamini@mycput.ac.za"))
                .andExpect(jsonPath("$.user.fullName").value("Jane Dlamini"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User stored = users.findByEmail("jane.dlamini@mycput.ac.za").orElseThrow();
        assertNotEquals(PASSWORD, stored.getPasswordHash());
        assertTrue(stored.getPasswordHash().startsWith("$2"), "expected a BCrypt hash, got " + stored.getPasswordHash());
        assertTrue(tokenFrom(result).split("\\.").length == 3, "a JWT has three dot-separated parts");
    }

    @Test
    void fr1_04_registerRejectsShortPassword() throws Exception {
        register("Jane", "jane@mycput.ac.za", "short7!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Password must be 8 to 72 characters"));
        register("Jane", "jane@mycput.ac.za", "x".repeat(73)).andExpect(status().isBadRequest());
        assertEquals(0, users.count());
    }

    @Test
    void fr1_05_registerRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void fr1_06_registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        register("Jane", "jane@mycput.ac.za", PASSWORD).andExpect(status().isCreated());
        register("Other Jane", "JANE@mycput.ac.za", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An account with this email already exists. Try logging in instead."));
        assertEquals(1, users.count());
    }

    @Test
    void fr1_07_loginReturnsJwtThatUnlocksProtectedEndpoint() throws Exception {
        register("Jane", "jane@mycput.ac.za", PASSWORD).andExpect(status().isCreated());

        String token = tokenFrom(login("Jane@mycput.ac.za", PASSWORD).andExpect(status().isOk()));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@mycput.ac.za"))
                .andExpect(jsonPath("$.fullName").value("Jane"));
    }

    @Test
    void fr1_08_loginRejectsWrongPasswordAndUnknownEmailWithTheSameMessage() throws Exception {
        register("Jane", "jane@mycput.ac.za", PASSWORD).andExpect(status().isCreated());

        login("jane@mycput.ac.za", "WrongPassword1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
        login("nobody@mycput.ac.za", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
    }

    @Test
    void fr1_09_protectedEndpointWithoutTokenIs401WithJsonBody() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void fr1_10_protectedEndpointRejectsGarbageForgedAndExpiredTokens() throws Exception {
        register("Jane", "jane@mycput.ac.za", PASSWORD).andExpect(status().isCreated());
        Long id = users.findByEmail("jane@mycput.ac.za").orElseThrow().getId();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());

        // A well-formed token signed with some OTHER key (what an attacker would produce)
        String forged = Jwts.builder().subject(String.valueOf(id))
                .signWith(Keys.hmacShaKeyFor("an-attacker-key-that-is-32-bytes!!".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());

        // A token whose lifetime is already over, even if the signature were genuine, is refused by the parser
        String expired = Jwts.builder().subject(String.valueOf(id))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor("an-attacker-key-that-is-32-bytes!!".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fr1_11_malformedJsonAndUnknownUrlGiveJsonErrorsNotHtmlOrStackTraces() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());

        // Unknown URLs need a login too (everything under /api is protected unless listed), so log in first
        String token = tokenFrom(register("Jane", "jane@mycput.ac.za", PASSWORD));
        mockMvc.perform(get("/api/does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
