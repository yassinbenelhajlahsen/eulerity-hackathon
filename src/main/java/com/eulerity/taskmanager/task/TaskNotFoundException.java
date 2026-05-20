package com.eulerity.taskmanager.task;

public class TaskNotFoundException extends RuntimeException {
    private final long id;

    public TaskNotFoundException(long id) {
        super("Task not found: " + id);
        this.id = id;
    }

    public long getId() { return id; }
}
