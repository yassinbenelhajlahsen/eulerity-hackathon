package com.eulerity.taskmanager.ai.dto;

import com.eulerity.taskmanager.task.Priority;
import com.eulerity.taskmanager.task.Status;

import java.time.LocalDate;

public record SuggestedTask(
        String title,
        String description,
        LocalDate dueDate,
        Priority priority,
        Status status
) {}
