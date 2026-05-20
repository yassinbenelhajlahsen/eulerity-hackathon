package com.eulerity.taskmanager.ai;

import com.eulerity.taskmanager.ai.dto.BreakdownResponse;
import com.eulerity.taskmanager.ai.dto.Subtask;
import com.eulerity.taskmanager.ai.dto.SuggestedTask;
import com.eulerity.taskmanager.task.Priority;
import com.eulerity.taskmanager.task.Status;
import com.eulerity.taskmanager.task.Task;
import com.eulerity.taskmanager.task.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Pin api-key to empty so this test is deterministic even if the developer
// running it has OPENAI_API_KEY set in their shell. The @TestConfiguration
// mock OpenAiClient bean wins via @Primary regardless, but pinning the
// property keeps Spring AI's auto-config in a known state.
// All OpenAI auto-configurations are excluded because they assert a non-empty
// api-key at context startup and are entirely superseded by the @Primary mock.
@SpringBootTest(properties = {
        // Also pin OPENAI_API_KEY= so SpringAiConfig picks stub mode regardless of
        // any .env file in the developer's working directory (DotEnvEnvironmentPostProcessor
        // would otherwise leak the real key into tests and crash on the excluded auto-config).
        "OPENAI_API_KEY=",
        "spring.ai.openai.api-key=",
        "spring.autoconfigure.exclude=" +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration," +
                "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration"
})
@AutoConfigureMockMvc
class AiEndpointIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired OpenAiClient openAiClient;       // injected mock from TestConfig below
    @Autowired TaskRepository taskRepo;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(openAiClient);
        taskRepo.deleteAll();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary
        OpenAiClient mockOpenAiClient() {
            return Mockito.mock(OpenAiClient.class);
        }
    }

    @Test
    void injectedOpenAiClient_isTheMockitoMock_notTheStub() {
        // Guards against silent bean-conflict regressions: both SpringAiConfig
        // and this @TestConfiguration register an OpenAiClient bean. @Primary
        // should win, but if Spring's wiring order ever changes we want a
        // loud failure here, not mysterious "stub data appeared in tests".
        assertThat(Mockito.mockingDetails(openAiClient).isMock()).isTrue();
        assertThat(openAiClient).isNotInstanceOf(StubOpenAiClient.class);
    }

    @Test
    void suggestEndpoint_returnsStructuredTask() throws Exception {
        when(openAiClient.suggest(anyString())).thenReturn(new SuggestedTask(
                "Submit quarterly report", "urgent per user",
                LocalDate.now().plusDays(2), Priority.HIGH, Status.TODO));

        String body = """
                { "text": "remind me to submit the quarterly report by Friday" }
                """;
        mvc.perform(post("/tasks/suggest")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Submit quarterly report"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void breakdownEndpoint_existingTask_returnsSubtasks() throws Exception {
        Task t = new Task();
        t.setTitle("Ship feature X");
        t.setDescription("Big chunk of work");
        t.setPriority(Priority.HIGH);
        t.setStatus(Status.TODO);
        Task saved = taskRepo.save(t);

        when(openAiClient.breakdown(any(Task.class))).thenReturn(new BreakdownResponse(
                saved.getId(),
                List.of(new Subtask(1, "Plan", 30, Priority.HIGH))));

        mvc.perform(post("/tasks/{id}/breakdown", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(saved.getId()))
                .andExpect(jsonPath("$.subtasks[0].title").value("Plan"));
    }

    @Test
    void breakdownEndpoint_missingTask_returns404() throws Exception {
        mvc.perform(post("/tasks/{id}/breakdown", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("task_not_found"));
    }
}
