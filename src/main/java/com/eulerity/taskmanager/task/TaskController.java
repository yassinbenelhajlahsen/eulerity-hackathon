package com.eulerity.taskmanager.task;

import com.eulerity.taskmanager.task.dto.CreateTaskRequest;
import com.eulerity.taskmanager.task.dto.MutationResponse;
import com.eulerity.taskmanager.task.dto.TaskResponse;
import com.eulerity.taskmanager.task.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MutationResponse> create(@Valid @RequestBody CreateTaskRequest req) {
        MutationResponse r = service.create(req);
        return ResponseEntity.created(URI.create("/tasks/" + r.task().id())).body(r);
    }

    @GetMapping
    public List<TaskResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    public MutationResponse update(@PathVariable long id, @Valid @RequestBody UpdateTaskRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
