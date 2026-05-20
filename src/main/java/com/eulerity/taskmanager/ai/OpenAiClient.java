package com.eulerity.taskmanager.ai;

import com.eulerity.taskmanager.ai.dto.BreakdownResponse;
import com.eulerity.taskmanager.ai.dto.SuggestedTask;
import com.eulerity.taskmanager.task.Task;

public interface OpenAiClient {
    SuggestedTask suggest(String userText);
    BreakdownResponse breakdown(Task task);
}
