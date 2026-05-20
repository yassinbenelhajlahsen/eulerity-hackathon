package com.eulerity.taskmanager.ai;

import com.eulerity.taskmanager.ai.dto.BreakdownResponse;
import com.eulerity.taskmanager.ai.dto.Subtask;
import com.eulerity.taskmanager.ai.dto.SuggestedTask;
import com.eulerity.taskmanager.task.Priority;
import com.eulerity.taskmanager.task.Status;
import com.eulerity.taskmanager.task.Task;

import java.util.List;

public class StubOpenAiClient implements OpenAiClient {

    @Override
    public SuggestedTask suggest(String userText) {
        String echo = userText == null ? "" : userText.substring(0, Math.min(80, userText.length()));
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
