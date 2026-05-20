package com.eulerity.taskmanager.ai;

import com.eulerity.taskmanager.ai.dto.BreakdownResponse;
import com.eulerity.taskmanager.ai.dto.SuggestedTask;
import com.eulerity.taskmanager.ai.prompts.Prompts;
import com.eulerity.taskmanager.task.Task;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.LocalDate;
import java.util.List;

public class SpringAiOpenAiClient implements OpenAiClient {

    private final ChatClient chat;

    public SpringAiOpenAiClient(ChatClient chat) {
        this.chat = chat;
    }

    @Override
    public SuggestedTask suggest(String wrappedUserText) {
        try {
            return chat.prompt(new Prompt(List.of(
                            new SystemMessage(Prompts.suggestSystem(LocalDate.now())),
                            new UserMessage(wrappedUserText))))
                    .call()
                    .entity(SuggestedTask.class);
        } catch (RuntimeException ex) {
            throw mapException(ex);
        }
    }

    @Override
    public BreakdownResponse breakdown(Task task) {
        String userContent = """
                Task ID: %d
                Title: %s
                Description: %s
                Current priority: %s
                Current status: %s
                """.formatted(
                task.getId(),
                task.getTitle(),
                task.getDescription() == null ? "" : task.getDescription(),
                task.getPriority(),
                task.getStatus()
        );
        try {
            BreakdownResponse fromModel = chat.prompt(new Prompt(List.of(
                            new SystemMessage(Prompts.BREAKDOWN_SYSTEM),
                            new UserMessage(userContent))))
                    .call()
                    .entity(BreakdownResponse.class);
            // Ensure taskId is the one we asked about, not whatever the model echoed.
            return new BreakdownResponse(task.getId(), fromModel.subtasks());
        } catch (RuntimeException ex) {
            throw mapException(ex);
        }
    }

    private static RuntimeException mapException(RuntimeException ex) {
        // Spring AI wraps OpenAI 5xx / network errors in its own runtime types.
        // For a take-home we keep the mapping coarse: parse/schema problems →
        // AiResponseException, everything else → AiServiceUnavailableException.
        String name = ex.getClass().getSimpleName().toLowerCase();
        if (name.contains("convert") || name.contains("json") || name.contains("parse")) {
            return new AiResponseException("model output did not match schema", ex);
        }
        return new AiServiceUnavailableException("OpenAI call failed", ex);
    }
}
