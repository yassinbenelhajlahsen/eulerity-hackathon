# Eulerity Take-Home: Task Manager

A small Spring Boot REST API for a personal task manager, with two AI-powered
endpoints that use OpenAI to turn natural language into structured tasks and to
break down complex tasks into subtasks.

## Prerequisites

- Java 17 (any vendor; Temurin recommended).
- Internet access (for Gradle dependency resolution on first run).
- `OPENAI_API_KEY` (optional). Without it, the app boots in **stub mode** — AI
  endpoints return deterministic canned data. The key can be supplied either as
  an exported environment variable OR via a `.env` file in the project root
  (single line: `OPENAI_API_KEY=sk-...`). The OS environment wins if both are
  set. `.env` is gitignored.

## Run

```bash
./gradlew bootRun
```

Then open **`http://localhost:8080`** in a browser. (The UI is served at the
application root by Spring Boot — not at `/index.html`, and **not** by opening
the HTML file directly from disk, since `file://` triggers same-origin errors
on `fetch()`.)

## Test

```bash
./gradlew test
```

All tests are offline (the real OpenAI call is mocked).

## Stub mode

If `OPENAI_API_KEY` is unset or blank, the app uses `StubOpenAiClient` and
prints a banner at startup:

```
============================================================
OPENAI_API_KEY not set — running with STUB AI client.
AI endpoints (/tasks/suggest, /tasks/{id}/breakdown) will
return deterministic canned data, not real model output.
Set OPENAI_API_KEY in your environment to enable real calls.
============================================================
```

The UI also shows a `[STUB MODE]` badge in the header so you don't mistake
canned data for real AI output. To enable real calls, either export the
variable:

```bash
export OPENAI_API_KEY=sk-...
./gradlew bootRun
```

…or drop a `.env` file at the project root:

```bash
echo 'OPENAI_API_KEY=sk-...' > .env
./gradlew bootRun
```

A `DotEnvEnvironmentPostProcessor` loads `.env` into the Spring environment
on boot. The OS environment still wins if the variable is set in both places
(matches how docker-compose and most dotenv tools behave). `.env` is
gitignored so the key won't accidentally land in a commit.

## Endpoints

### CRUD

| Method | Path | Description |
|---|---|---|
| POST   | `/tasks`        | Create a task |
| GET    | `/tasks`        | List all tasks |
| GET    | `/tasks/{id}`   | Get one task |
| PUT    | `/tasks/{id}`   | Replace a task |
| DELETE | `/tasks/{id}`   | Delete a task |

A `Task` has: `id`, `title`, `description`, `dueDate`, `priority` (`LOW`/`MEDIUM`/`HIGH`),
`status` (`TODO`/`IN_PROGRESS`/`DONE`), plus `createdAt` / `updatedAt`.

### AI

**`POST /tasks/suggest`** — turn natural language into a structured task.

Request:
```json
{ "text": "remind me to submit the quarterly report before Friday" }
```

Stub-mode response:
```json
{
  "title": "[STUB] Example task suggestion",
  "description": "Stub AI response. Set OPENAI_API_KEY to enable real suggestions. Echo of input: remind me to submit the quarterly report before Friday",
  "dueDate": null,
  "priority": "MEDIUM",
  "status": "TODO"
}
```

Real-mode response (example):
```json
{
  "title": "Submit quarterly report",
  "description": "Quarterly report submission flagged urgent by user.",
  "dueDate": "2026-05-22",
  "priority": "HIGH",
  "status": "TODO"
}
```

The response is **not persisted** — the UI lets the user review and click
"Accept & save" to POST it back to `/tasks`.

**`POST /tasks/{id}/breakdown`** — break a complex task into ordered subtasks.

Stub-mode response (for `taskId=42`):
```json
{
  "taskId": 42,
  "subtasks": [
    { "order": 1, "title": "[STUB] First subtask",  "estimatedMinutes": 30, "priority": "MEDIUM" },
    { "order": 2, "title": "[STUB] Second subtask", "estimatedMinutes": 30, "priority": "MEDIUM" }
  ]
}
```

Returns 404 if the task ID doesn't exist.

### Meta

`GET /meta` — `{ "stubMode": boolean }`. Used by the UI to show the stub badge.

## Prompt-injection defenses

The AI service applies 9 layered defenses (see `AiTaskService.java`):

1. **Length cap** — input over 1000 chars (suggest) / 2200 chars (breakdown) → 422.
2. **Control-character strip** — strips `U+0000`–`U+001F` except `\n`, `\r`, `\t`. Newlines preserved so multi-line task descriptions survive.
3. **Delimiter fencing** — user text wrapped in `<<<USER_INPUT_BEGIN>>>` / `<<<USER_INPUT_END>>>` sentinels, with the system prompt explicitly stating the sentinels demarcate untrusted data.
4. **Role separation** — `SystemMessage` and `UserMessage` are distinct; user text is never concatenated into the system prompt.
5. **Suspicious-pattern soft flag** — regex scan for `"ignore previous"`, `"system prompt"`, `"you are now"`, `"DAN"`, `"developer mode"`. Logs WARN with a SHA-256 hash of the input; doesn't block (false-positive-prone).
6. **Structured-output coercion** — Spring AI's `.entity(SuggestedTask.class)` forces JSON-schema-shaped output; parse failures → 502.
7. **Enum + range validation** — `priority`, `status` must be in-range; `dueDate >= today - 1 day` (one-day buffer for tz drift).
8. **Refusal-marker detection** — output starting with "I cannot" / "I'm sorry" / "I can't" / "I won't" → 422.
9. **Sentinel echo strip** — defensively removes `<<<USER_INPUT_*>>>` strings from any output field.

## Input quirks worth knowing

- **`dueDate` buffer:** AI output must have `dueDate >= today - 1 day`. If you test with a date in the past and get 422 `model_returned_invalid_response`, that's the buffer doing its job. The 1-day slack accounts for timezone drift between the server, the model's internal anchor, and the user's local clock.

## Design choices

- **Gradle (not Maven)** — wrapper-driven, no local install needed.
- **Spring AI (not raw HTTP)** — gets structured-output / JSON-schema binding for free; mockable behind a small interface.
- **Model `gpt-4o-mini` (not `gpt-4.1-mini`)** — both list Structured Outputs as supported, but `gpt-4o-mini` has been the reference model for `response_format=json_schema` since 2024-07-18 and has the longest production track record on this exact feature. `gpt-4.1-mini` is newer with cheaper/faster numbers, but a handful of community reports show intermittent json-schema failures. For a cold-run evaluation where any flake looks like our bug, the older, more battle-tested model is the right pick.
- **Stub fallback as default** — boots without an API key.
- **In-memory H2 + `create-drop` DDL** — honest about JVM-bound lifetime (`update` would imply a persistent DB).
- **AI output not persisted** — keeps the AI call stateless (per the brief) and gives the user a confirmation step before anything hits the DB.

## Project layout

```
src/main/java/com/eulerity/taskmanager/
  TaskManagerApplication.java
  config/SpringAiConfig.java        // bean selection + startup banner
  meta/MetaController.java          // GET /meta
  error/GlobalExceptionHandler.java // HTTP error mapping
  task/                             // CRUD: entity, service, controller, DTOs
  ai/                               // AI: service, controllers, client interface, defenses
src/test/java/com/eulerity/taskmanager/
  task/TaskServiceTest.java         // unit
  task/TaskCrudIntegrationTest.java // @SpringBootTest, real H2
  ai/AiTaskServiceTest.java         // unit, one test per defense
  ai/StubOpenAiClientTest.java      // covers default no-API-key path
  ai/AiEndpointIntegrationTest.java // @SpringBootTest, both AI endpoints + 404
```
