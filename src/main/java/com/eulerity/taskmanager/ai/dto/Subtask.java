package com.eulerity.taskmanager.ai.dto;

import com.eulerity.taskmanager.task.Priority;

public record Subtask(
        int order,
        String title,
        int estimatedMinutes,
        Priority priority
) {}
