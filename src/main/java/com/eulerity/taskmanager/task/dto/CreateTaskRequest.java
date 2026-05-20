package com.eulerity.taskmanager.task.dto;

import com.eulerity.taskmanager.task.Priority;
import com.eulerity.taskmanager.task.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        LocalDate dueDate,
        @NotNull Priority priority,
        Status status
) {}
