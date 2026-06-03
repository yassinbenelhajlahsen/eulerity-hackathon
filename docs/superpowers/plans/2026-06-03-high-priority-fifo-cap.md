# HIGH-Priority FIFO Cap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce a configurable cap (default 5) on the number of *active* HIGH-priority tasks (priority=HIGH AND status ∈ {TODO, IN_PROGRESS}). When a save would move a task into the active-HIGH set and the cap is reached, the oldest active-HIGH task (by `createdAt`) is automatically demoted to MEDIUM, and the demoted task is reported back in the mutation response so the UI can surface a toast.

**Architecture:** Cap enforcement lives as a private method on `TaskService`, gated by a single predicate ("would this save move the task into active-HIGH from outside it?"). Two new derived queries on `TaskRepository` provide the count and the FIFO victim. `POST /tasks` and `PUT /tasks/{id}` switch from returning bare `TaskResponse` to a new `MutationResponse { task, demoted }` wrapper; `GET` and `DELETE` are unchanged. The cap value is read from `application.yml` (`tasks.priority.high.max`).

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Data JPA, H2 (in-memory), JUnit 5, Mockito, MockMvc.

---

## Key invariants (do not violate)

- The trigger predicate is **"is this save moving the task into the active-HIGH set?"** Active-HIGH = `priority == HIGH && status ∈ {TODO, IN_PROGRESS}`. The cap fires only when the post-save state is active-HIGH AND the pre-save state was NOT active-HIGH (for `create`, pre-save state is treated as "not active-HIGH").
- DONE tasks **never count** toward the cap and **never get picked** as the demotion victim.
- FIFO key is `createdAt`. Do not switch to `updatedAt` or invent a new column.
- A `PUT` that does not change the task's active-HIGH membership (e.g., editing the title of an already-active-HIGH task, or moving an active-HIGH task to DONE) **must not** invoke cap logic.
- The cap value is read from `application.yml`. Do not hardcode `5` in `TaskService`.
- All cap-enforcement work happens inside the same `@Transactional` boundary as the create/update — demotion of the victim and persistence of the new/updated task must commit together or fail together.

---

## File structure

**Create:**
- `src/main/java/com/eulerity/taskmanager/task/dto/MutationResponse.java`
- `src/test/java/com/eulerity/taskmanager/task/dto/MutationResponseTest.java`
- `src/test/java/com/eulerity/taskmanager/task/TaskHighPriorityCapIntegrationTest.java`

**Modify:**
- `src/main/resources/application.yml` — add `tasks.priority.high.max: 5`
- `src/main/java/com/eulerity/taskmanager/task/TaskRepository.java` — add two derived queries
- `src/main/java/com/eulerity/taskmanager/task/TaskService.java` — new ctor param, `ACTIVE_STATUSES`, `enforceHighPriorityCap`, return `MutationResponse` from `create`/`update`
- `src/main/java/com/eulerity/taskmanager/task/TaskController.java` — return `MutationResponse` from POST and PUT
- `src/test/java/com/eulerity/taskmanager/task/TaskServiceTest.java` — adapt existing tests to new ctor + return shape, add cap-behavior tests
- `src/test/java/com/eulerity/taskmanager/task/TaskCrudIntegrationTest.java` — adapt assertions to `$.task.*`
- `src/main/resources/static/index.html` — read `.task` from POST/PUT responses, toast on `.demoted`

**Untouched:** `Task` (entity), `Priority`, `Status`, `CreateTaskRequest`, `UpdateTaskRequest`, `TaskResponse`, `GlobalExceptionHandler`, all AI subsystem files.

---

### Task 1: Add cap configuration property

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Add the property**

Edit `src/main/resources/application.yml` and append (sibling to `spring:`, `server:`, `logging:`):

```yaml
tasks:
  priority:
    high:
      max: 5
```

- [ ] **Step 2: Verify the app still boots**

Run: `./gradlew bootRun` (Ctrl-C once you see `Started TaskManagerApplication`).
Expected: clean boot, no property-related errors.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "feat(task): add tasks.priority.high.max config property"
```

---

### Task 2: Add `MutationResponse` DTO

**Files:**
- Create: `src/main/java/com/eulerity/taskmanager/task/dto/MutationResponse.java`
- Create: `src/test/java/com/eulerity/taskmanager/task/dto/MutationResponseTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/eulerity/taskmanager/task/dto/MutationResponseTest.java`:

```java
package com.eulerity.taskmanager.task.dto;

import com.eulerity.taskmanager.task.Priority;
import com.eulerity.taskmanager.task.Status;
import com.eulerity.taskmanager.task.Task;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MutationResponseTest {

    @Test
    void of_withDemotedNull_wrapsSavedAndKeepsDemotedNull() {
        Task saved = new Task();
        saved.setId(1L);
        saved.setTitle("new high");
        saved.setPriority(Priority.HIGH);
        saved.setStatus(Status.TODO);

        MutationResponse r = MutationResponse.of(saved, null);

        assertThat(r.task()).isNotNull();
        assertThat(r.task().id()).isEqualTo(1L);
        assertThat(r.task().priority()).isEqualTo(Priority.HIGH);
        assertThat(r.demoted()).isNull();
    }

    @Test
    void of_withDemoted_wrapsBoth() {
        Task saved = new Task();
        saved.setId(2L);
        saved.setTitle("new high");
        saved.setPriority(Priority.HIGH);
        saved.setStatus(Status.TODO);

        Task demoted = new Task();
        demoted.setId(99L);
        demoted.setTitle("oldest");
        demoted.setPriority(Priority.MEDIUM);
        demoted.setStatus(Status.TODO);

        MutationResponse r = MutationResponse.of(saved, demoted);

        assertThat(r.task().id()).isEqualTo(2L);
        assertThat(r.demoted()).isNotNull();
        assertThat(r.demoted().id()).isEqualTo(99L);
        assertThat(r.demoted().priority()).isEqualTo(Priority.MEDIUM);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.eulerity.taskmanager.task.dto.MutationResponseTest"`
Expected: FAIL — `MutationResponse` class doesn't exist (compilation error).

- [ ] **Step 3: Implement the DTO**

Create `src/main/java/com/eulerity/taskmanager/task/dto/MutationResponse.java`:

```java
package com.eulerity.taskmanager.task.dto;

import com.eulerity.taskmanager.task.Task;

public record MutationResponse(TaskResponse task, TaskResponse demoted) {

    public static MutationResponse of(Task saved, Task demoted) {
        return new MutationResponse(
                TaskResponse.from(saved),
                demoted == null ? null : TaskResponse.from(demoted));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.eulerity.taskmanager.task.dto.MutationResponseTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/eulerity/taskmanager/task/dto/MutationResponse.java \
        src/test/java/com/eulerity/taskmanager/task/dto/MutationResponseTest.java
git commit -m "feat(task): add MutationResponse DTO wrapping saved + demoted task"
```

---

### Task 3: Migrate `TaskService`, controller, and existing tests to the new response shape

This task is a pure refactor that changes the return shape of `TaskService.create` / `TaskService.update` from `TaskResponse` to `MutationResponse` (with `demoted` always `null` for now). No cap behavior is added yet — that comes in Task 5. Everything that consumes those return values must be updated in this same commit so the tree stays green.

**Files:**
- Modify: `src/main/java/com/eulerity/taskmanager/task/TaskService.java`
- Modify: `src/main/java/com/eulerity/taskmanager/task/TaskController.java`
- Modify: `src/test/java/com/eulerity/taskmanager/task/TaskServiceTest.java`
- Modify: `src/test/java/com/eulerity/taskmanager/task/TaskCrudIntegrationTest.java`
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: Update `TaskService` to return `MutationResponse` (cap-less)**

Replace the body of `src/main/java/com/eulerity/taskmanager/task/TaskService.java` with:

```java
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
```

- [ ] **Step 2: Update `TaskController` to return `MutationResponse` from POST and PUT**

Replace `src/main/java/com/eulerity/taskmanager/task/TaskController.java` with:

```java
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
```

- [ ] **Step 3: Update existing unit tests to the new ctor + return shape**

Replace `src/test/java/com/eulerity/taskmanager/task/TaskServiceTest.java` with:

```java
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

    private static final int MAX_HIGH = 5;

    private TaskRepository repo;
    private TaskService service;

    @BeforeEach
    void setUp() {
        repo = mock(TaskRepository.class);
        service = new TaskService(repo, MAX_HIGH);
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
        assertThat(resp.task().id()).isEqualTo(7L);
        assertThat(resp.task().title()).isEqualTo("Buy milk");
        assertThat(resp.demoted()).isNull();
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

        assertThat(resp.task().title()).isEqualTo("new title");
        assertThat(resp.task().priority()).isEqualTo(Priority.HIGH);
        assertThat(resp.task().status()).isEqualTo(Status.DONE);
        assertThat(resp.demoted()).isNull();
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
```

- [ ] **Step 4: Update the CRUD integration test for the new JSON shape**

Replace `src/test/java/com/eulerity/taskmanager/task/TaskCrudIntegrationTest.java` with:

```java
package com.eulerity.taskmanager.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskCrudIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void fullCrudLifecycle() throws Exception {
        // CREATE — response is now { task, demoted }; demoted is null here.
        String createBody = """
                { "title": "Buy milk", "description": "2%",
                  "dueDate": "2026-06-01", "priority": "MEDIUM" }
                """;
        String created = mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.task.id").exists())
                .andExpect(jsonPath("$.task.status").value("TODO"))
                .andExpect(jsonPath("$.demoted").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("task").get("id").asLong();

        // LIST — unchanged shape (still bare TaskResponse[]).
        mvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // GET BY ID — unchanged shape.
        mvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Buy milk"));

        // UPDATE — response is now { task, demoted }.
        String updateBody = """
                { "title": "Buy oat milk", "description": "barista blend",
                  "dueDate": "2026-06-02", "priority": "HIGH", "status": "IN_PROGRESS" }
                """;
        mvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.title").value("Buy oat milk"))
                .andExpect(jsonPath("$.task.priority").value("HIGH"))
                .andExpect(jsonPath("$.task.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.demoted").doesNotExist());

        // DELETE — unchanged.
        mvc.perform(delete("/tasks/{id}", id))
                .andExpect(status().isNoContent());

        // GET BY ID after delete → 404 (unchanged).
        mvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("task_not_found"));
    }

    @Test
    void create_invalidPayload_returns400() throws Exception {
        String body = """
                { "title": "", "priority": "MEDIUM" }
                """;
        mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.title").exists());
    }
}
```

Note: `jsonPath("$.demoted").doesNotExist()` matches when the JSON value is `null` (Jackson serializes `null` record fields as `null`, and `doesNotExist()` treats JSON `null` as "does not exist" by default in Spring's MockMvc). If this assertion fails on your local JUnit run, use `.value(org.hamcrest.Matchers.nullValue())` instead.

- [ ] **Step 5: Update `index.html` to read `.task` from POST/PUT responses**

Open `src/main/resources/static/index.html`.

In the `inlineUpdate` function (around line 361), change the `try` block from:

```javascript
  try {
    const updated = await api('/tasks/' + id, { method: 'PUT', body: JSON.stringify(body) });
    tasksById[id] = updated;
    // Keep the breakdown task-picker label in sync if the title changed.
    if (field === 'title') {
      const opt = document.querySelector(`#bTask option[value="${id}"]`);
      if (opt) opt.textContent = `#${id} ${updated.title}`;
    }
  } catch (e) {
```

to:

```javascript
  try {
    const r = await api('/tasks/' + id, { method: 'PUT', body: JSON.stringify(body) });
    const updated = r.task;
    tasksById[id] = updated;
    // Keep the breakdown task-picker label in sync if the title changed.
    if (field === 'title') {
      const opt = document.querySelector(`#bTask option[value="${id}"]`);
      if (opt) opt.textContent = `#${id} ${updated.title}`;
    }
    if (r.demoted) {
      showToast(`"${r.demoted.title}" was moved to MEDIUM (HIGH limit reached)`, 'info');
      loadTasks();
    }
  } catch (e) {
```

In the `createTask` function (around line 406), change:

```javascript
      const r = await api('/tasks', { method: 'POST', body: JSON.stringify(body) });
      out.innerHTML =
        `<div class="card">
           <span class="ok">✓ Created task #${r.id}: ${escapeHtml(r.title)}</span>
           ${renderRaw(r)}
         </div>`;
      loadTasks();
```

to:

```javascript
      const r = await api('/tasks', { method: 'POST', body: JSON.stringify(body) });
      const created = r.task;
      out.innerHTML =
        `<div class="card">
           <span class="ok">✓ Created task #${created.id}: ${escapeHtml(created.title)}</span>
           ${renderRaw(r)}
         </div>`;
      if (r.demoted) {
        showToast(`"${r.demoted.title}" was moved to MEDIUM (HIGH limit reached)`, 'info');
      }
      loadTasks();
```

In the `acceptSuggestion` function (around line 468), change:

```javascript
      const saved = await api('/tasks', { method: 'POST', body: JSON.stringify(lastSuggestion) });
      out.innerHTML = renderSuggestion(lastSuggestion) +
        `<div class="card"><span class="ok">✓ Saved as task #${saved.id}</span></div>`;
      lastSuggestion = null;
      document.getElementById('acceptBtn').disabled = true;
      loadTasks();
```

to:

```javascript
      const r = await api('/tasks', { method: 'POST', body: JSON.stringify(lastSuggestion) });
      const saved = r.task;
      out.innerHTML = renderSuggestion(lastSuggestion) +
        `<div class="card"><span class="ok">✓ Saved as task #${saved.id}</span></div>`;
      if (r.demoted) {
        showToast(`"${r.demoted.title}" was moved to MEDIUM (HIGH limit reached)`, 'info');
      }
      lastSuggestion = null;
      document.getElementById('acceptBtn').disabled = true;
      loadTasks();
```

- [ ] **Step 6: Run the full test suite**

Run: `./gradlew test`
Expected: all tests pass (no cap behavior yet — demotion paths are dead code so far).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/eulerity/taskmanager/task/TaskService.java \
        src/main/java/com/eulerity/taskmanager/task/TaskController.java \
        src/test/java/com/eulerity/taskmanager/task/TaskServiceTest.java \
        src/test/java/com/eulerity/taskmanager/task/TaskCrudIntegrationTest.java \
        src/main/resources/static/index.html
git commit -m "refactor(task): return MutationResponse from mutating endpoints"
```

---

### Task 4: Add the two repository derived queries

**Files:**
- Modify: `src/main/java/com/eulerity/taskmanager/task/TaskRepository.java`

- [ ] **Step 1: Add the methods**

Replace `src/main/java/com/eulerity/taskmanager/task/TaskRepository.java` with:

```java
package com.eulerity.taskmanager.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    long countByPriorityAndStatusIn(Priority priority, Collection<Status> statuses);

    Optional<Task> findFirstByPriorityAndStatusInOrderByCreatedAtAsc(
            Priority priority, Collection<Status> statuses);
}
```

- [ ] **Step 2: Verify boot still works (catches malformed derived-query names early)**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

Then run: `./gradlew test --tests "com.eulerity.taskmanager.TaskManagerApplicationTests"`
Expected: PASS. The context-loads test will fail loudly if Spring Data can't parse the method names.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/eulerity/taskmanager/task/TaskRepository.java
git commit -m "feat(task): add derived queries for active-HIGH count and FIFO victim"
```

---

### Task 5: Enforce cap on `create` when status is active

**Files:**
- Modify: `src/test/java/com/eulerity/taskmanager/task/TaskServiceTest.java`
- Modify: `src/main/java/com/eulerity/taskmanager/task/TaskService.java`

- [ ] **Step 1: Write the failing tests**

Append the following tests inside `TaskServiceTest` (before the closing `}`):

```java
    // --- HIGH-priority cap behavior ---

    @Test
    void create_high_belowCap_doesNotDemote() {
        when(repo.countByPriorityAndStatusIn(eq(Priority.HIGH), anySet()))
                .thenReturn(4L);
        when(repo.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(10L);
            return t;
        });

        var req = new CreateTaskRequest("urgent", null, null, Priority.HIGH, Status.TODO);
        var resp = service.create(req);

        assertThat(resp.task().priority()).isEqualTo(Priority.HIGH);
        assertThat(resp.demoted()).isNull();
        verify(repo, never()).findFirstByPriorityAndStatusInOrderByCreatedAtAsc(any(), anySet());
        verify(repo, times(1)).save(any(Task.class));
    }

    @Test
    void create_high_atCap_demotesOldestActiveHigh() {
        when(repo.countByPriorityAndStatusIn(eq(Priority.HIGH), anySet()))
                .thenReturn(5L);
        Task oldest = new Task();
        oldest.setId(42L);
        oldest.setTitle("oldest");
        oldest.setPriority(Priority.HIGH);
        oldest.setStatus(Status.TODO);
        when(repo.findFirstByPriorityAndStatusInOrderByCreatedAtAsc(eq(Priority.HIGH), anySet()))
                .thenReturn(Optional.of(oldest));
        when(repo.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) t.setId(11L);
            return t;
        });

        var req = new CreateTaskRequest("new urgent", null, null,
                Priority.HIGH, Status.TODO);
        var resp = service.create(req);

        assertThat(resp.task().id()).isEqualTo(11L);
        assertThat(resp.task().priority()).isEqualTo(Priority.HIGH);
        assertThat(resp.demoted()).isNotNull();
        assertThat(resp.demoted().id()).isEqualTo(42L);
        assertThat(resp.demoted().priority()).isEqualTo(Priority.MEDIUM);
        // Both saves: the demoted task and the new task.
        verify(repo, times(2)).save(any(Task.class));
    }
```

You'll need this import at the top of the file (if not already present):

```java
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.eulerity.taskmanager.task.TaskServiceTest"`
Expected: `create_high_atCap_demotesOldestActiveHigh` FAILS (`resp.demoted()` is null). `create_high_belowCap_doesNotDemote` may PASS already because nothing calls the cap methods yet, but keep it — it locks behavior in once the logic exists.

- [ ] **Step 3: Implement `enforceHighPriorityCap` and call it from `create`**

In `src/main/java/com/eulerity/taskmanager/task/TaskService.java`, replace the `create` method and add the helper:

```java
    @Transactional
    public MutationResponse create(CreateTaskRequest req) {
        Task t = new Task();
        t.setTitle(req.title());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        t.setPriority(req.priority());
        t.setStatus(req.status() == null ? Status.TODO : req.status());

        Task demoted = null;
        boolean becomesActiveHigh =
                t.getPriority() == Priority.HIGH && ACTIVE_STATUSES.contains(t.getStatus());
        if (becomesActiveHigh) {
            demoted = enforceHighPriorityCap();
        }

        Task saved = repo.save(t);
        return MutationResponse.of(saved, demoted);
    }

    /**
     * If the active-HIGH count has reached the cap, demote the oldest active-HIGH
     * task to MEDIUM and return it. Otherwise return null. Caller is responsible
     * for ensuring this is invoked only when the incoming save would enter the
     * active-HIGH set from outside it.
     */
    private Task enforceHighPriorityCap() {
        long count = repo.countByPriorityAndStatusIn(Priority.HIGH, ACTIVE_STATUSES);
        if (count < maxHighPriority) {
            return null;
        }
        Task oldest = repo.findFirstByPriorityAndStatusInOrderByCreatedAtAsc(
                Priority.HIGH, ACTIVE_STATUSES)
                .orElseThrow(() -> new IllegalStateException(
                        "active-HIGH count >= cap but no oldest task found"));
        oldest.setPriority(Priority.MEDIUM);
        return repo.save(oldest);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.eulerity.taskmanager.task.TaskServiceTest"`
Expected: PASS (all tests, including the two new ones).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/eulerity/taskmanager/task/TaskService.java \
        src/test/java/com/eulerity/taskmanager/task/TaskServiceTest.java
git commit -m "feat(task): enforce HIGH cap on create with FIFO demotion"
```

---

### Task 6: Skip cap on `create` when status is DONE

**Files:**
- Modify: `src/test/java/com/eulerity/taskmanager/task/TaskServiceTest.java`

- [ ] **Step 1: Write the failing test**

Append to `TaskServiceTest`:

```java
    @Test
    void create_highWithDoneStatus_skipsCapCheck() {
        when(repo.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(20L);
            return t;
        });

        var req = new CreateTaskRequest("archived urgent", null, null,
                Priority.HIGH, Status.DONE);
        var resp = service.create(req);

        assertThat(resp.task().priority()).isEqualTo(Priority.HIGH);
        assertThat(resp.task().status()).isEqualTo(Status.DONE);
        assertThat(resp.demoted()).isNull();
        verify(repo, never()).countByPriorityAndStatusIn(any(), anySet());
        verify(repo, never()).findFirstByPriorityAndStatusInOrderByCreatedAtAsc(any(), anySet());
    }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew test --tests "com.eulerity.taskmanager.task.TaskServiceTest.create_highWithDoneStatus_skipsCapCheck"`
Expected: PASS — the `becomesActiveHigh` guard in Task 5 already covers this (DONE is not in `ACTIVE_STATUSES`). This test locks the behavior in.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/eulerity/taskmanager/task/TaskServiceTest.java
git commit -m "test(task): lock in 'create HIGH+DONE skips cap' behavior"
```

---

### Task 7: Enforce cap on `update` when the save moves a task into active-HIGH

**Files:**
- Modify: `src/test/java/com/eulerity/taskmanager/task/TaskServiceTest.java`
- Modify: `src/main/java/com/eulerity/taskmanager/task/TaskService.java`

This task covers the two update paths in one go: (a) promotion from non-HIGH to HIGH while status is active, and (b) reopening a DONE-HIGH task to TODO/IN_PROGRESS. Both flip the predicate `becomesActiveHigh && !wasActiveHigh` from false to true, so a single code change handles both.

- [ ] **Step 1: Write the failing tests**

Append to `TaskServiceTest`:

```java
    @Test
    void update_promoteToHigh_atCap_demotesOldest() {
        Task existing = new Task();
        existing.setId(1L);
        existing.setTitle("was medium");
        existing.setPriority(Priority.MEDIUM);
        existing.setStatus(Status.TODO);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.countByPriorityAndStatusIn(eq(Priority.HIGH), anySet()))
                .thenReturn(5L);
        Task oldest = new Task();
        oldest.setId(42L);
        oldest.setTitle("oldest");
        oldest.setPriority(Priority.HIGH);
        oldest.setStatus(Status.TODO);
        when(repo.findFirstByPriorityAndStatusInOrderByCreatedAtAsc(eq(Priority.HIGH), anySet()))
                .thenReturn(Optional.of(oldest));
        when(repo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new UpdateTaskRequest("was medium", null, null,
                Priority.HIGH, Status.TODO);
        var resp = service.update(1L, req);

        assertThat(resp.task().priority()).isEqualTo(Priority.HIGH);
        assertThat(resp.demoted()).isNotNull();
        assertThat(resp.demoted().id()).isEqualTo(42L);
        assertThat(resp.demoted().priority()).isEqualTo(Priority.MEDIUM);
    }

    @Test
    void update_reopenDoneHighToTodo_atCap_demotesOldest() {
        Task existing = new Task();
        existing.setId(1L);
        existing.setTitle("was done high");
        existing.setPriority(Priority.HIGH);
        existing.setStatus(Status.DONE);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.countByPriorityAndStatusIn(eq(Priority.HIGH), anySet()))
                .thenReturn(5L);
        Task oldest = new Task();
        oldest.setId(42L);
        oldest.setTitle("oldest");
        oldest.setPriority(Priority.HIGH);
        oldest.setStatus(Status.TODO);
        when(repo.findFirstByPriorityAndStatusInOrderByCreatedAtAsc(eq(Priority.HIGH), anySet()))
                .thenReturn(Optional.of(oldest));
        when(repo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new UpdateTaskRequest("was done high", null, null,
                Priority.HIGH, Status.TODO);
        var resp = service.update(1L, req);

        assertThat(resp.task().status()).isEqualTo(Status.TODO);
        assertThat(resp.demoted()).isNotNull();
        assertThat(resp.demoted().id()).isEqualTo(42L);
    }

    @Test
    void update_editTitleOfActiveHigh_doesNotInvokeCap() {
        Task existing = new Task();
        existing.setId(1L);
        existing.setTitle("active high");
        existing.setPriority(Priority.HIGH);
        existing.setStatus(Status.IN_PROGRESS);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new UpdateTaskRequest("renamed", null, null,
                Priority.HIGH, Status.IN_PROGRESS);
        var resp = service.update(1L, req);

        assertThat(resp.task().title()).isEqualTo("renamed");
        assertThat(resp.demoted()).isNull();
        verify(repo, never()).countByPriorityAndStatusIn(any(), anySet());
        verify(repo, never()).findFirstByPriorityAndStatusInOrderByCreatedAtAsc(any(), anySet());
    }

    @Test
    void update_completeActiveHigh_doesNotInvokeCap() {
        Task existing = new Task();
        existing.setId(1L);
        existing.setTitle("active high");
        existing.setPriority(Priority.HIGH);
        existing.setStatus(Status.TODO);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new UpdateTaskRequest("active high", null, null,
                Priority.HIGH, Status.DONE);
        var resp = service.update(1L, req);

        assertThat(resp.task().status()).isEqualTo(Status.DONE);
        assertThat(resp.demoted()).isNull();
        verify(repo, never()).countByPriorityAndStatusIn(any(), anySet());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.eulerity.taskmanager.task.TaskServiceTest"`
Expected: the two demote-on-update tests FAIL (still get `demoted: null`). The two no-op tests should pass already (the `update` method as currently written never calls cap logic).

- [ ] **Step 3: Implement the cap call in `update`**

In `src/main/java/com/eulerity/taskmanager/task/TaskService.java`, replace the `update` method with:

```java
    @Transactional
    public MutationResponse update(long id, UpdateTaskRequest req) {
        Task t = repo.findById(id).orElseThrow(() -> new TaskNotFoundException(id));

        boolean wasActiveHigh =
                t.getPriority() == Priority.HIGH && ACTIVE_STATUSES.contains(t.getStatus());

        t.setTitle(req.title());
        t.setDescription(req.description());
        t.setDueDate(req.dueDate());
        t.setPriority(req.priority());
        t.setStatus(req.status());

        boolean becomesActiveHigh =
                t.getPriority() == Priority.HIGH && ACTIVE_STATUSES.contains(t.getStatus());

        Task demoted = null;
        if (becomesActiveHigh && !wasActiveHigh) {
            demoted = enforceHighPriorityCap();
        }

        Task saved = repo.save(t);
        return MutationResponse.of(saved, demoted);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.eulerity.taskmanager.task.TaskServiceTest"`
Expected: PASS (all tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/eulerity/taskmanager/task/TaskService.java \
        src/test/java/com/eulerity/taskmanager/task/TaskServiceTest.java
git commit -m "feat(task): enforce HIGH cap on update transitions into active-HIGH"
```

---

### Task 8: End-to-end integration test with a real H2 and cap override

**Files:**
- Create: `src/test/java/com/eulerity/taskmanager/task/TaskHighPriorityCapIntegrationTest.java`

This test uses the real H2, the real repository, and `MockMvc` to drive the controller. We override the cap to 2 with `@TestPropertySource` so the test data stays small.

- [ ] **Step 1: Write the integration test**

Create `src/test/java/com/eulerity/taskmanager/task/TaskHighPriorityCapIntegrationTest.java`:

```java
package com.eulerity.taskmanager.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "tasks.priority.high.max=2")
class TaskHighPriorityCapIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TaskRepository repo;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    private long createHigh(String title) throws Exception {
        String body = """
                { "title": "%s", "priority": "HIGH", "status": "TODO" }
                """.formatted(title);
        MvcResult res = mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString())
                .get("task").get("id").asLong();
    }

    @Test
    void createThirdHigh_demotesOldest() throws Exception {
        long firstId  = createHigh("first");
        long secondId = createHigh("second");

        // Third HIGH triggers demotion of the oldest (firstId).
        String body = """
                { "title": "third", "priority": "HIGH", "status": "TODO" }
                """;
        MvcResult res = mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task.title").value("third"))
                .andExpect(jsonPath("$.task.priority").value("HIGH"))
                .andExpect(jsonPath("$.demoted.id").value((int) firstId))
                .andExpect(jsonPath("$.demoted.priority").value("MEDIUM"))
                .andReturn();

        // Verify persisted state.
        Task first = repo.findById(firstId).orElseThrow();
        assertThat(first.getPriority()).isEqualTo(Priority.MEDIUM);
        Task second = repo.findById(secondId).orElseThrow();
        assertThat(second.getPriority()).isEqualTo(Priority.HIGH);
        JsonNode root = json.readTree(res.getResponse().getContentAsString());
        long thirdId = root.get("task").get("id").asLong();
        Task third = repo.findById(thirdId).orElseThrow();
        assertThat(third.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void promoteToHigh_atCap_demotesOldest() throws Exception {
        long firstId  = createHigh("first");
        createHigh("second");

        // Create a MEDIUM task, then PUT it to HIGH — should demote firstId.
        String createBody = """
                { "title": "candidate", "priority": "MEDIUM", "status": "TODO" }
                """;
        MvcResult created = mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        long candidateId = json.readTree(created.getResponse().getContentAsString())
                .get("task").get("id").asLong();

        String updateBody = """
                { "title": "candidate", "priority": "HIGH", "status": "TODO" }
                """;
        mvc.perform(put("/tasks/{id}", candidateId)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.priority").value("HIGH"))
                .andExpect(jsonPath("$.demoted.id").value((int) firstId))
                .andExpect(jsonPath("$.demoted.priority").value("MEDIUM"));

        assertThat(repo.findById(firstId).orElseThrow().getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(repo.findById(candidateId).orElseThrow().getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void reopenDoneHighToTodo_atCap_demotesOldest() throws Exception {
        long firstId  = createHigh("first");
        createHigh("second");

        // Create a HIGH+DONE task — this is dormant, doesn't count toward the cap.
        String createBody = """
                { "title": "dormant", "priority": "HIGH", "status": "DONE" }
                """;
        MvcResult created = mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.demoted").doesNotExist())
                .andReturn();
        long dormantId = json.readTree(created.getResponse().getContentAsString())
                .get("task").get("id").asLong();

        // Reopen it: DONE → TODO while still HIGH. Now it enters the active-HIGH set.
        String reopenBody = """
                { "title": "dormant", "priority": "HIGH", "status": "TODO" }
                """;
        mvc.perform(put("/tasks/{id}", dormantId)
                        .contentType(MediaType.APPLICATION_JSON).content(reopenBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.status").value("TODO"))
                .andExpect(jsonPath("$.demoted.id").value((int) firstId))
                .andExpect(jsonPath("$.demoted.priority").value("MEDIUM"));
    }

    @Test
    void titleEditOnActiveHigh_doesNotDemote() throws Exception {
        long firstId  = createHigh("first");
        createHigh("second");

        String updateBody = """
                { "title": "first renamed", "priority": "HIGH", "status": "TODO" }
                """;
        mvc.perform(put("/tasks/{id}", firstId)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.title").value("first renamed"))
                .andExpect(jsonPath("$.demoted").doesNotExist());

        long activeHighCount = repo.countByPriorityAndStatusIn(
                Priority.HIGH, java.util.Set.of(Status.TODO, Status.IN_PROGRESS));
        assertThat(activeHighCount).isEqualTo(2);
    }

    @Test
    void createMediumPriority_neverInvokesCap() throws Exception {
        createHigh("first");
        createHigh("second");

        String body = """
                { "title": "medium one", "priority": "MEDIUM", "status": "TODO" }
                """;
        mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.demoted").doesNotExist());
    }

    @Test
    void createHighWithDoneStatus_skipsCap() throws Exception {
        createHigh("first");
        createHigh("second");

        String body = """
                { "title": "archived", "priority": "HIGH", "status": "DONE" }
                """;
        mvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task.priority").value("HIGH"))
                .andExpect(jsonPath("$.task.status").value("DONE"))
                .andExpect(jsonPath("$.demoted").doesNotExist());

        long activeHighCount = repo.countByPriorityAndStatusIn(
                Priority.HIGH, java.util.Set.of(Status.TODO, Status.IN_PROGRESS));
        assertThat(activeHighCount).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `./gradlew test --tests "com.eulerity.taskmanager.task.TaskHighPriorityCapIntegrationTest"`
Expected: all 6 tests PASS.

- [ ] **Step 3: Run the full test suite to catch regressions**

Run: `./gradlew test`
Expected: all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/eulerity/taskmanager/task/TaskHighPriorityCapIntegrationTest.java
git commit -m "test(task): add end-to-end integration tests for HIGH-priority FIFO cap"
```

---

### Task 9: Manual smoke test in the browser

**Files:** none

The UI changes from Task 3 + the cap behavior from Tasks 5–7 need a hands-on check. Per CLAUDE.md, type-checking and test suites don't verify feature correctness for UI; we must use the feature in a browser.

- [ ] **Step 1: Boot the app with the default cap (5)**

Run: `./gradlew bootRun` (leave running).

In a separate terminal, populate 5 active HIGH tasks via curl:

```bash
for i in 1 2 3 4 5; do
  curl -s -X POST http://localhost:8080/tasks \
    -H 'Content-Type: application/json' \
    -d "{\"title\":\"high-$i\",\"priority\":\"HIGH\",\"status\":\"TODO\"}" \
    > /dev/null
done
```

- [ ] **Step 2: Open `http://localhost:8080/` and create a 6th HIGH task via the form**

In the browser, fill the create-task form with: Title=`high-6`, Priority=`HIGH`, Status=`TODO`. Click Create.

Expected:
- The "✓ Created task #N" success card appears.
- An **info-style toast** ("`"high-1" was moved to MEDIUM (HIGH limit reached)`") appears top-center.
- The task list refreshes; `high-1` now shows priority MEDIUM, the other four originals + `high-6` are HIGH.

- [ ] **Step 3: Verify inline-edit promotion path**

Find a MEDIUM task in the list (e.g., `high-1`). Change its priority dropdown to HIGH.

Expected:
- The cell updates to HIGH styling.
- A toast appears naming the oldest active-HIGH task that just got demoted (likely `high-2`).
- The list refresh shows that task at MEDIUM.

- [ ] **Step 4: Verify "edit title of an active HIGH" does NOT demote**

Pick any active-HIGH task. Click its title cell, change a character, blur (or press Tab).

Expected:
- The title updates silently.
- **No toast** appears. No other task changes priority.

- [ ] **Step 5: Stop the server**

Ctrl-C the `bootRun` terminal.

- [ ] **Step 6: Commit (only if you ended up tweaking anything)**

If you made no UI edits during smoke-testing, skip this step. Otherwise:

```bash
git add src/main/resources/static/index.html
git commit -m "fix(ui): smoke-test fixups for HIGH-priority FIFO cap"
```

---

## Spec-coverage check (self-review)

- **Cap value configurable, default 5** — Task 1 (yml) + Task 5 wiring via `@Value`. ✓
- **Active = TODO + IN_PROGRESS, DONE excluded from count and victim selection** — Task 5 (`ACTIVE_STATUSES`) + Task 4 (derived queries take statuses Set). ✓
- **FIFO key = `createdAt`** — Task 4 (`findFirstByPriorityAndStatusInOrderByCreatedAtAsc`). ✓
- **Trigger predicate: enters active-HIGH set from outside it** — Tasks 5 (create branch) + 7 (update branch with `becomesActiveHigh && !wasActiveHigh`). ✓
- **Skip cap on create with HIGH+DONE** — Task 5's `becomesActiveHigh` guard, locked by Task 6 test. ✓
- **Skip cap on reopen of HIGH+DONE → HIGH+TODO behaves as transition** — Task 7's `wasActiveHigh` correctly returns false when the previous status was DONE. ✓
- **Response shape `{ task, demoted }` on POST + PUT only; GET unchanged** — Tasks 2 + 3 (controller + DTOs). ✓
- **Demotion + new save in the same `@Transactional`** — Task 5 (`@Transactional` on `create`, helper runs inline) + Task 7 (`@Transactional` on `update`). ✓
- **UI surfaces demotion via toast** — Task 3 Step 5 (three POST/PUT call sites). ✓
- **Existing tests still pass** — Tasks 3 (refactor), 8 (regression check via full suite). ✓

## Known non-goals (called out in spec)

- **Concurrent POSTs racing the cap check.** Two simultaneous `POST /tasks` with HIGH at count=4 can both succeed without demotion, ending at count=6. Not defended against — the app is single-user local. If multi-user becomes real, fix is row-level locks on the count+demote step.
- **Ties on `createdAt`.** Spring Data picks a stable but unspecified secondary order. Not defended against.
