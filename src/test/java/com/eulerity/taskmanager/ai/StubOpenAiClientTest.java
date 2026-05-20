package com.eulerity.taskmanager.ai;

import com.eulerity.taskmanager.ai.dto.BreakdownResponse;
import com.eulerity.taskmanager.ai.dto.SuggestedTask;
import com.eulerity.taskmanager.ai.prompts.Prompts;
import com.eulerity.taskmanager.task.Priority;
import com.eulerity.taskmanager.task.Status;
import com.eulerity.taskmanager.task.Task;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubOpenAiClientTest {

    private final OpenAiClient stub = new StubOpenAiClient();

    @Test
    void suggest_returnsCanonicalStubResponse() {
        SuggestedTask r = stub.suggest("remind me to finish the report");

        assertThat(r.title()).isEqualTo("[STUB] Example task suggestion");
        assertThat(r.description()).contains("Stub AI response");
        assertThat(r.description()).contains("remind me to finish the report");
        assertThat(r.dueDate()).isNull();
        assertThat(r.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(r.status()).isEqualTo(Status.TODO);
    }

    @Test
    void suggest_truncatesLongInputInEcho() {
        String longText = "x".repeat(500);
        SuggestedTask r = stub.suggest(longText);
        // Description echoes only the first 80 chars
        assertThat(r.description()).contains("x".repeat(80));
        assertThat(r.description()).doesNotContain("x".repeat(81));
    }

    @Test
    void suggest_whenInputIsSentinelWrapped_echoIsCleanUserText() {
        // Reflects what AiTaskService actually passes at runtime: user text
        // wrapped between USER_INPUT_BEGIN / USER_INPUT_END. The stub must
        // strip the wrapping before echoing or reviewers see the sentinel
        // strings in their "stub mode" output.
        String wrapped = Prompts.USER_INPUT_BEGIN + "\nremind me to buy milk\n" + Prompts.USER_INPUT_END;
        SuggestedTask r = stub.suggest(wrapped);

        assertThat(r.description()).contains("remind me to buy milk");
        assertThat(r.description()).doesNotContain(Prompts.USER_INPUT_BEGIN);
        assertThat(r.description()).doesNotContain(Prompts.USER_INPUT_END);
        // No leftover leading whitespace from the wrapping newlines.
        int echoIdx = r.description().indexOf("Echo of input: ") + "Echo of input: ".length();
        assertThat(r.description().substring(echoIdx)).startsWith("remind");
    }

    @Test
    void breakdown_returnsCanonicalStubResponse() {
        Task t = new Task();
        t.setId(42L);
        t.setTitle("Quarterly report");
        t.setPriority(Priority.HIGH);
        t.setStatus(Status.TODO);

        BreakdownResponse r = stub.breakdown(t);

        assertThat(r.taskId()).isEqualTo(42L);
        assertThat(r.subtasks()).hasSize(2);
        assertThat(r.subtasks().get(0).order()).isEqualTo(1);
        assertThat(r.subtasks().get(0).title()).isEqualTo("[STUB] First subtask");
        assertThat(r.subtasks().get(1).title()).isEqualTo("[STUB] Second subtask");
        assertThat(r.subtasks()).allSatisfy(s -> {
            assertThat(s.estimatedMinutes()).isEqualTo(30);
            assertThat(s.priority()).isEqualTo(Priority.MEDIUM);
        });
    }
}
