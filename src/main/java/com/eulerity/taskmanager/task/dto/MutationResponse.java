package com.eulerity.taskmanager.task.dto;

import com.eulerity.taskmanager.task.Task;

public record MutationResponse(TaskResponse task, TaskResponse demoted) {

    public static MutationResponse of(Task saved, Task demoted) {
        return new MutationResponse(
                TaskResponse.from(saved),
                demoted == null ? null : TaskResponse.from(demoted));
    }
}
