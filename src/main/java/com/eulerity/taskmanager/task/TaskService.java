package com.eulerity.taskmanager.task;

import com.eulerity.taskmanager.task.dto.CreateTaskRequest;
import com.eulerity.taskmanager.task.dto.MutationResponse;
import com.eulerity.taskmanager.task.dto.TaskResponse;
import com.eulerity.taskmanager.task.dto.UpdateTaskRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class TaskService {

    static final Set<Status> ACTIVE_STATUSES = EnumSet.of(Status.TODO, Status.IN_PROGRESS);

    private final TaskRepository repo;
    private final int maxHighPriority;

    public TaskService(TaskRepository repo,
                       @Value("${tasks.priority.high.max:5}") int maxHighPriority) {
        this.repo = repo;
        this.maxHighPriority = maxHighPriority;
    }

    @Transactional
    public MutationResponse create(CreateTaskRequest req) {
        Task t = new Task();
        t.setTitle(req.title());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        t.setPriority(req.priority());
        t.setStatus(req.status() == null ? Status.TODO : req.status());
        Task saved = repo.save(t);
        return MutationResponse.of(saved, null);
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
    public MutationResponse update(long id, UpdateTaskRequest req) {
        Task t = repo.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        t.setTitle(req.title());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        t.setPriority(req.priority());
        t.setStatus(req.status());
        Task saved = repo.save(t);
        return MutationResponse.of(saved, null);
    }

    @Transactional
    public void delete(long id) {
        if (!repo.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        repo.deleteById(id);
    }
}
