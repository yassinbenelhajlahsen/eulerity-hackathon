package com.eulerity.taskmanager.ai;

import com.eulerity.taskmanager.ai.dto.BreakdownResponse;
import com.eulerity.taskmanager.ai.dto.Subtask;
import com.eulerity.taskmanager.ai.dto.SuggestedTask;
import com.eulerity.taskmanager.ai.prompts.Prompts;
import com.eulerity.taskmanager.task.Priority;
import com.eulerity.taskmanager.task.Status;
import com.eulerity.taskmanager.task.Task;

import java.util.List;

public class StubOpenAiClient implements OpenAiClient {

    @Override
    public SuggestedTask suggest(String userText) {
        // AiTaskService wraps user text in sentinels before calling .suggest().
        // Strip them here so the echoed preview is the user's actual text, not
        // the wrapping artifacts. Only depends on public Prompts constants.
        String cleaned = userText == null ? "" : userText
                .replace(Prompts.USER_INPUT_BEGIN, "")
                .replace(Prompts.USER_INPUT_END, "")
                .strip();
        String echo = cleaned.substring(0, Math.min(80, cleaned.length()));
        return new SuggestedTask(
                "[STUB] Example task suggestion",
                "Stub AI response. Set OPENAI_API_KEY to enable real suggestions. Echo of input: " + echo,
                null,
                Priority.MEDIUM,
                Status.TODO
        );
    }

    @Override
    public BreakdownResponse breakdown(Task task) {
        return new BreakdownResponse(
                task.getId(),
                List.of(
                        new Subtask(1, "[STUB] First subtask",  30, Priority.MEDIUM),
                        new Subtask(2, "[STUB] Second subtask", 30, Priority.MEDIUM)
                )
        );
    }
}
