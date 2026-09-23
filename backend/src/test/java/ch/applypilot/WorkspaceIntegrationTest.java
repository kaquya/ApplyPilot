package ch.applypilot;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
        "app.storage-path=./target/test-documents",
        "app.openai-key=",
        "app.billing=false",
        "app.resend-key=",
        "app.google-id=",
    }
)
@AutoConfigureMockMvc
class WorkspaceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    AccountRepository accounts;

    Account alice, bob;

    @BeforeEach
    void users() {
        alice = createAccount();
        bob = createAccount();
    }

    Account createAccount() {
        var a = new Account();
        a.email = UUID.randomUUID() + "@example.test";
        a.name = "Test applicant";
        return accounts.saveAndFlush(a);
    }

    ObjectNode job() {
        return json
            .createObjectNode()
            .put("title", "Junior Engineer")
            .put("company", "Example AG")
            .put("status", "APPLIED")
            .put("description", "Java and Spring Boot required")
            .put("language", "en");
    }

    JsonNode createJob() throws Exception {
        return json.readTree(
            mvc
                .perform(
                    post("/api/jobs")
                        .with(user(alice.id.toString()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(job().toString())
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
        );
    }

    @Test
    void anonymousRequestsAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(
            post("/api/jobs")
                .with(user(alice.id.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(job().toString())
        ).andExpect(status().isForbidden());
    }

    @Test
    void registrationCreatesSessionAndLoginWorks() throws Exception {
        var credentials = json
            .createObjectNode()
            .put("name", "Local test")
            .put("email", UUID.randomUUID() + "@example.test")
            .put("password", "long-test-password-123");
        var result = mvc
            .perform(
                post("/api/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials.toString())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan").value("FREE"))
            .andReturn();
        var cookies = result.getResponse().getCookies();
        assertThat(cookies).isNotEmpty();
        mvc.perform(get("/api/auth/me").cookie(cookies))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Local test"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials.toString())
        ).andExpect(status().isOk());
        credentials.put("password", "wrong-password-12345");
        mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials.toString())
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void recordsArePrivateToTheirOwner() throws Exception {
        var created = createJob();
        String id = created.path("id").asText();
        mvc.perform(get("/api/jobs").with(user(bob.id.toString())))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
        var edit = json.createObjectNode().put("version", 0);
        edit.set("data", job());
        mvc.perform(
            put("/api/jobs/" + id)
                .with(user(bob.id.toString()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(edit.toString())
        ).andExpect(status().isNotFound());
        mvc.perform(
            delete("/api/jobs/" + id)
                .with(user(bob.id.toString()))
                .with(csrf())
        ).andExpect(status().isNotFound());
        assertThat(created.path("appliedDate").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void updatesRequireCurrentVersion() throws Exception {
        var created = createJob();
        var changed = job().put("status", "INTERVIEW");
        var edit = json.createObjectNode().put("version", created.path("version").asLong());
        edit.set("data", changed);
        mvc.perform(
            put("/api/jobs/" + created.path("id").asText())
                .with(user(alice.id.toString()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(edit.toString())
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INTERVIEW"));
        mvc.perform(
            put("/api/jobs/" + created.path("id").asText())
                .with(user(alice.id.toString()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(edit.toString())
        ).andExpect(status().isConflict());
    }

    @Test
    void unsafeLinksAndInvertedSalaryAreRejected() throws Exception {
        for (var invalid : List.of(
            job().put("url", "javascript:alert(1)"),
            job().put("salaryMin", 90000).put("salaryMax", 75000),
            job().put("status", "INVALID"),
            job().put("title", "")
        )) {
            mvc.perform(
                post("/api/jobs")
                    .with(user(alice.id.toString()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalid.toString())
            ).andExpect(status().isBadRequest());
        }
    }

    @Test
    void profileFromAnotherAccountCannotBeLinked() throws Exception {
        String profile = mvc
            .perform(
                post("/api/profiles")
                    .with(user(bob.id.toString()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Support\",\"language\":\"de\",\"text\":\"Windows support\"}"
                    )
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        mvc.perform(
            post("/api/jobs")
                .with(user(alice.id.toString()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    job().put("profileId", json.readTree(profile).path("id").asText()).toString()
                )
        ).andExpect(status().isNotFound());
    }

    @Test
    void uploadsAreExtractedAndDownloadsArePrivate() throws Exception {
        var file = new MockMultipartFile(
            "file",
            "CV.txt",
            "text/plain",
            "Java, Spring Boot. German B2.".getBytes(StandardCharsets.UTF_8)
        );
        var result = mvc
            .perform(
                multipart("/api/documents").file(file).with(user(alice.id.toString())).with(csrf())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value("Java, Spring Boot. German B2."))
            .andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get("/api/documents/" + id).with(user(bob.id.toString()))).andExpect(
            status().isNotFound()
        );
        mvc.perform(get("/api/documents/" + id).with(user(alice.id.toString())))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().bytes(file.getBytes()));
        mvc.perform(
            delete("/api/documents/" + id)
                .with(user(alice.id.toString()))
                .with(csrf())
        ).andExpect(status().isOk());
    }

    @Test
    void disguisedExecutableIsRejected() throws Exception {
        var file = new MockMultipartFile(
            "file",
            "CV.pdf",
            "application/pdf",
            "MZ fake executable".getBytes(StandardCharsets.UTF_8)
        );
        mvc.perform(
            multipart("/api/documents").file(file).with(user(alice.id.toString())).with(csrf())
        ).andExpect(status().isBadRequest());
    }

    @Test
    void unavailableIntegrationsFailExplicitly() throws Exception {
        var created = createJob();
        mvc.perform(
            post("/api/jobs/" + created.path("id").asText() + "/ai")
                .with(user(alice.id.toString()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"analysis\"}")
        ).andExpect(status().isServiceUnavailable());
        mvc.perform(
            post("/api/billing/checkout")
                .with(user(alice.id.toString()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PRO\"}")
        ).andExpect(status().isServiceUnavailable());
        mvc.perform(
            post("/api/email/reminders")
                .with(user(alice.id.toString()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"language\":\"en\"}")
        ).andExpect(status().isServiceUnavailable());
    }
}
