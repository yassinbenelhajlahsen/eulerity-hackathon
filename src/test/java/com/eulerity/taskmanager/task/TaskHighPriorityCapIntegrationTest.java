package com.eulerity.taskmanager.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "tasks.priority.high.max=2")
class TaskHighPriorityCapIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TaskRepository repo;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    private long createHigh(String title) throws Exception {
        String body = """
                { "title": "%s", "priority": "HIGH", "status": "TODO" }
                """.formatted(title);
        MvcResult res = mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString())
                .get("task").get("id").asLong();
    }

    @Test
    void createThirdHigh_demotesOldest() throws Exception {
        long firstId  = createHigh("first");
        long secondId = createHigh("second");

        // Third HIGH triggers demotion of the oldest (firstId).
        String body = """
                { "title": "third", "priority": "HIGH", "status": "TODO" }
                """;
        MvcResult res = mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task.title").value("third"))
                .andExpect(jsonPath("$.task.priority").value("HIGH"))
                .andExpect(jsonPath("$.demoted.id").value((int) firstId))
                .andExpect(jsonPath("$.demoted.priority").value("MEDIUM"))
                .andReturn();

        // Verify persisted state.
        Task first = repo.findById(firstId).orElseThrow();
        assertThat(first.getPriority()).isEqualTo(Priority.MEDIUM);
        Task second = repo.findById(secondId).orElseThrow();
        assertThat(second.getPriority()).isEqualTo(Priority.HIGH);
        JsonNode root = json.readTree(res.getResponse().getContentAsString());
        long thirdId = root.get("task").get("id").asLong();
        Task third = repo.findById(thirdId).orElseThrow();
        assertThat(third.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void promoteToHigh_atCap_demotesOldest() throws Exception {
        long firstId  = createHigh("first");
        createHigh("second");

        // Create a MEDIUM task, then PUT it to HIGH — should demote firstId.
        String createBody = """
                { "title": "candidate", "priority": "MEDIUM", "status": "TODO" }
                """;
        MvcResult created = mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        long candidateId = json.readTree(created.getResponse().getContentAsString())
                .get("task").get("id").asLong();

        String updateBody = """
                { "title": "candidate", "priority": "HIGH", "status": "TODO" }
                """;
        mvc.perform(put("/tasks/{id}", candidateId)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.priority").value("HIGH"))
                .andExpect(jsonPath("$.demoted.id").value((int) firstId))
                .andExpect(jsonPath("$.demoted.priority").value("MEDIUM"));

        assertThat(repo.findById(firstId).orElseThrow().getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(repo.findById(candidateId).orElseThrow().getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void reopenDoneHighToTodo_atCap_demotesOldest() throws Exception {
        long firstId  = createHigh("first");
        createHigh("second");

        // Create a HIGH+DONE task — this is dormant, doesn't count toward the cap.
        String createBody = """
                { "title": "dormant", "priority": "HIGH", "status": "DONE" }
                """;
        MvcResult created = mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.demoted").doesNotExist())
                .andReturn();
        long dormantId = json.readTree(created.getResponse().getContentAsString())
                .get("task").get("id").asLong();

        // Reopen it: DONE → TODO while still HIGH. Now it enters the active-HIGH set.
        String reopenBody = """
                { "title": "dormant", "priority": "HIGH", "status": "TODO" }
                """;
        mvc.perform(put("/tasks/{id}", dormantId)
                        .contentType(MediaType.APPLICATION_JSON).content(reopenBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.status").value("TODO"))
                .andExpect(jsonPath("$.demoted.id").value((int) firstId))
                .andExpect(jsonPath("$.demoted.priority").value("MEDIUM"));
    }

    @Test
    void titleEditOnActiveHigh_doesNotDemote() throws Exception {
        long firstId  = createHigh("first");
        createHigh("second");

        String updateBody = """
                { "title": "first renamed", "priority": "HIGH", "status": "TODO" }
                """;
        mvc.perform(put("/tasks/{id}", firstId)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.title").value("first renamed"))
                .andExpect(jsonPath("$.demoted").doesNotExist());

        long activeHighCount = repo.countByPriorityAndStatusIn(
                Priority.HIGH, java.util.Set.of(Status.TODO, Status.IN_PROGRESS));
        assertThat(activeHighCount).isEqualTo(2);
    }

    @Test
    void createMediumPriority_neverInvokesCap() throws Exception {
        createHigh("first");
        createHigh("second");

        String body = """
                { "title": "medium one", "priority": "MEDIUM", "status": "TODO" }
                """;
        mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.demoted").doesNotExist());
    }

    @Test
    void createHighWithDoneStatus_skipsCap() throws Exception {
        createHigh("first");
        createHigh("second");

        String body = """
                { "title": "archived", "priority": "HIGH", "status": "DONE" }
                """;
        mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task.priority").value("HIGH"))
                .andExpect(jsonPath("$.task.status").value("DONE"))
                .andExpect(jsonPath("$.demoted").doesNotExist());

        long activeHighCount = repo.countByPriorityAndStatusIn(
                Priority.HIGH, java.util.Set.of(Status.TODO, Status.IN_PROGRESS));
        assertThat(activeHighCount).isEqualTo(2);
    }
}
