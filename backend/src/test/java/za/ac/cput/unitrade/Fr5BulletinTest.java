package za.ac.cput.unitrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import za.ac.cput.unitrade.repository.BulletinPostRepository;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR5 Bulletin board: public list, students post, authors delete their own posts. Through HTTP on H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fr5BulletinTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BulletinPostRepository posts;

    private String author;
    private String other;

    @BeforeEach
    void registerStudents() throws Exception {
        author = register("Ayesha Author", "ayesha@mycput.ac.za");
        other = register("Oli Other", "oli@mycput.ac.za");
    }

    private String register(String name, String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", name, "email", email, "password", "Password123!"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private long newPost(String token, String title, String category) throws Exception {
        Map<String, Object> body = Map.of("title", title, "body", "Details of " + title, "category", category);
        String json = send(post("/api/bulletin"), token, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Test
    void fr5_01_postingNeedsLogin() throws Exception {
        send(post("/api/bulletin"), null, Map.of("title", "x", "body", "y", "category", "EVENT")).andExpect(status().isUnauthorized());
        assertEquals(0, posts.count());
    }

    @Test
    void fr5_02_aStudentCanPostAndTheAuthorIsShownByNameOnly() throws Exception {
        send(post("/api/bulletin"), author, Map.of("title", "  Clean-up day ", "body", " Meet at the gate ", "category", "EVENT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Clean-up day"))
                .andExpect(jsonPath("$.body").value("Meet at the gate"))
                .andExpect(jsonPath("$.category").value("EVENT"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.author.fullName").value("Ayesha Author"))
                .andExpect(jsonPath("$.author.email").doesNotExist());
    }

    @Test
    void fr5_03_theListIsPublicNewestFirstAndCanBeFilteredByCategory() throws Exception {
        newPost(author, "First", "ANNOUNCEMENT");
        newPost(other, "Second", "EVENT");
        newPost(author, "Third", "EVENT");

        send(get("/api/bulletin"), null, null).andExpect(status().isOk())   // no login needed
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].title").value("Third"))
                .andExpect(jsonPath("$.items[2].title").value("First"));
        send(get("/api/bulletin").param("category", "EVENT"), null, null)
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].title").value("Third"));
        send(get("/api/bulletin").param("category", "SERVICE"), null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void fr5_04_theListIsPaged() throws Exception {
        for (int i = 1; i <= 7; i++) {
            newPost(author, "Post " + i, "ANNOUNCEMENT");
        }
        send(get("/api/bulletin").param("size", "3").param("page", "2"), null, null)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Post 1"))
                .andExpect(jsonPath("$.totalPages").value(3));
        send(get("/api/bulletin").param("size", "0"), null, null).andExpect(status().isBadRequest());
        send(get("/api/bulletin").param("page", "-1"), null, null).andExpect(status().isBadRequest());
    }

    @Test
    void fr5_05_validationRejectsMissingTitleBodyAndBadCategory() throws Exception {
        send(post("/api/bulletin"), author, new HashMap<>(Map.of("title", " ", "body", "", "category", "EVENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.fieldErrors.body").exists());
        send(post("/api/bulletin"), author, Map.of("title", "x", "body", "y", "category", "SELLING_STUFF"))
                .andExpect(status().isBadRequest());
        send(post("/api/bulletin"), author, Map.of("title", "x".repeat(151), "body", "y", "category", "EVENT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.title").exists());
        assertEquals(0, posts.count());
    }

    @Test
    void fr5_06_theAuthorCanDeleteTheirOwnPost() throws Exception {
        long id = newPost(author, "To delete", "ANNOUNCEMENT");
        send(delete("/api/bulletin/" + id), author, null).andExpect(status().isNoContent());
        send(get("/api/bulletin/" + id), null, null).andExpect(status().isNotFound());
        assertEquals(0, posts.count());
    }

    @Test
    void fr5_07_anotherStudentCannotDeleteItAndNeitherCanAnAnonymousVisitor() throws Exception {
        long id = newPost(author, "Mine", "ANNOUNCEMENT");
        send(delete("/api/bulletin/" + id), other, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only delete your own posts."));
        send(delete("/api/bulletin/" + id), null, null).andExpect(status().isUnauthorized());
        assertEquals(1, posts.count());
    }

    @Test
    void fr5_08_unknownPostIs404() throws Exception {
        send(get("/api/bulletin/999999"), null, null).andExpect(status().isNotFound());
        send(delete("/api/bulletin/999999"), author, null).andExpect(status().isNotFound());
    }

    @Test
    void fr5_09_onePostCanBeReadPublicly() throws Exception {
        long id = newPost(author, "Read me", "SERVICE");
        send(get("/api/bulletin/" + id), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Read me"))
                .andExpect(jsonPath("$.category").value("SERVICE"));
    }
}
