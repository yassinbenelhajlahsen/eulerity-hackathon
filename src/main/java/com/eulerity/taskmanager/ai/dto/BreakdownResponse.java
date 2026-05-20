package com.eulerity.taskmanager.ai.dto;

import java.util.List;

public record BreakdownResponse(
        long taskId,
        List<Subtask> subtasks
) {}
