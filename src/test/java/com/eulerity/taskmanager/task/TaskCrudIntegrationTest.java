package com.eulerity.taskmanager.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskCrudIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void fullCrudLifecycle() throws Exception {
        // CREATE — response is now { task, demoted }; demoted is null here.
        String createBody = """
                { "title": "Buy milk", "description": "2%",
                  "dueDate": "2026-06-01", "priority": "MEDIUM" }
                """;
        String created = mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.task.id").exists())
                .andExpect(jsonPath("$.task.status").value("TODO"))
                .andExpect(jsonPath("$.demoted").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("task").get("id").asLong();

        // LIST — unchanged shape (still bare TaskResponse[]).
        mvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // GET BY ID — unchanged shape.
        mvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Buy milk"));

        // UPDATE — response is now { task, demoted }.
        String updateBody = """
                { "title": "Buy oat milk", "description": "barista blend",
                  "dueDate": "2026-06-02", "priority": "HIGH", "status": "IN_PROGRESS" }
                """;
        mvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.title").value("Buy oat milk"))
                .andExpect(jsonPath("$.task.priority").value("HIGH"))
                .andExpect(jsonPath("$.task.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.demoted").doesNotExist());

        // DELETE — unchanged.
        mvc.perform(delete("/tasks/{id}", id))
                .andExpect(status().isNoContent());

        // GET BY ID after delete → 404 (unchanged).
        mvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("task_not_found"));
    }

    @Test
    void create_invalidPayload_returns400() throws Exception {
        String body = """
                { "title": "", "priority": "MEDIUM" }
                """;
        mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.title").exists());
    }
}
