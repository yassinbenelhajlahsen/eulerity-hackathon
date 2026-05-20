package com.eulerity.taskmanager.task.dto;

import com.eulerity.taskmanager.task.Priority;
import com.eulerity.taskmanager.task.Status;
import com.eulerity.taskmanager.task.Task;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        LocalDate dueDate,
        Priority priority,
        Status status,
        Instant createdAt,
        Instant updatedAt
) {
    public static TaskResponse from(Task t) {
        return new TaskResponse(
                t.getId(), t.getTitle(), t.getDescription(), t.getDueDate(),
                t.getPriority(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
