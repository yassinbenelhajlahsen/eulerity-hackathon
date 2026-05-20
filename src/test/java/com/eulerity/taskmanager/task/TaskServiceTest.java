package com.eulerity.taskmanager.task;

import com.eulerity.taskmanager.task.dto.CreateTaskRequest;
import com.eulerity.taskmanager.task.dto.UpdateTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskServiceTest {

    private TaskRepository repo;
    private TaskService service;

    @BeforeEach
    void setUp() {
        repo = mock(TaskRepository.class);
        service = new TaskService(repo);
    }

    @Test
    void create_persistsTaskWithDefaultedStatus() {
        when(repo.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(7L);
            return t;
        });

        var req = new CreateTaskRequest("Buy milk", "2%", LocalDate.now(),
                Priority.LOW, null);
        var resp = service.create(req);

        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(Status.TODO);
        assertThat(resp.id()).isEqualTo(7L);
        assertThat(resp.title()).isEqualTo("Buy milk");
    }

    @Test
    void list_returnsAllTasks() {
        Task t = new Task();
        t.setId(1L); t.setTitle("a"); t.setPriority(Priority.LOW); t.setStatus(Status.TODO);
        when(repo.findAll()).thenReturn(List.of(t));

        var result = service.list();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void getById_existingTask_returnsIt() {
        Task t = new Task();
        t.setId(1L); t.setTitle("a"); t.setPriority(Priority.LOW); t.setStatus(Status.TODO);
        when(repo.findById(1L)).thenReturn(Optional.of(t));

        var result = service.getById(1L);
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void getById_missingTask_throws() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void update_mutatesAndSaves() {
        Task existing = new Task();
        existing.setId(1L); existing.setTitle("old");
        existing.setPriority(Priority.LOW); existing.setStatus(Status.TODO);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new UpdateTaskRequest("new title", "desc",
                LocalDate.of(2026, 6, 1), Priority.HIGH, Status.DONE);
        var resp = service.update(1L, req);

        assertThat(resp.title()).isEqualTo("new title");
        assertThat(resp.priority()).isEqualTo(Priority.HIGH);
        assertThat(resp.status()).isEqualTo(Status.DONE);
    }

    @Test
    void update_missingTask_throws() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        var req = new UpdateTaskRequest("x", null, null, Priority.LOW, Status.TODO);
        assertThatThrownBy(() -> service.update(99L, req))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void delete_existingTask_callsRepo() {
        when(repo.existsById(1L)).thenReturn(true);
        service.delete(1L);
        verify(repo).deleteById(1L);
    }

    @Test
    void delete_missingTask_throws() {
        when(repo.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
