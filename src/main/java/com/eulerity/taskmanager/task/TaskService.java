package com.eulerity.taskmanager.task;

import com.eulerity.taskmanager.task.dto.CreateTaskRequest;
import com.eulerity.taskmanager.task.dto.TaskResponse;
import com.eulerity.taskmanager.task.dto.UpdateTaskRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository repo;

    public TaskService(TaskRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest req) {
        Task t = new Task();
        t.setTitle(req.title());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        t.setPriority(req.priority());
        t.setStatus(req.status() == null ? Status.TODO : req.status());
        return TaskResponse.from(repo.save(t));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list() {
        return repo.findAll().stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getById(long id) {
        return TaskResponse.from(repo.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id)));
    }

    @Transactional
    public TaskResponse update(long id, UpdateTaskRequest req) {
        Task t = repo.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        t.setTitle(req.title());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        t.setPriority(req.priority());
        t.setStatus(req.status());
        return TaskResponse.from(repo.save(t));
    }

    @Transactional
    public void delete(long id) {
        if (!repo.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        repo.deleteById(id);
    }
}
