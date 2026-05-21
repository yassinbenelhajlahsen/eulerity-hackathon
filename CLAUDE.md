# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew bootRun                                          # run app at http://localhost:8080
./gradlew test                                             # run full test suite (offline; AI calls mocked)
./gradlew test --tests "com.eulerity.taskmanager.ai.AiTaskServiceTest"           # single class
./gradlew test --tests "com.eulerity.taskmanager.ai.AiTaskServiceTest.<method>"  # single method
./gradlew build                                            # compile + test + jar
```

Java 17 required (toolchain enforces it). No Maven — use the wrapper.

## Stub mode vs real OpenAI

The app boots in **stub mode** when `OPENAI_API_KEY` is unset/blank — AI endpoints return canned data from `StubOpenAiClient`. With the key set (env var or `.env` file at project root), `SpringAiOpenAiClient` is wired instead. Stub detection happens at bean-wiring time in `SpringAiConfig`; you cannot flip modes at runtime.

`.env` loading is done by `DotEnvEnvironmentPostProcessor` (registered via `META-INF/spring.factories`). OS env wins over `.env` if both are set.

## Architecture

Two cohesive subsystems share the Spring context:

- **`task/`** — JPA CRUD over an in-memory H2 (`create-drop`, so the DB resets every boot). `TaskController` → `TaskService` → `TaskRepository` (Spring Data). `PUT /tasks/{id}` is a **full replace** — partial PATCH is not supported. DTOs (`CreateTaskRequest`, `UpdateTaskRequest`, `TaskResponse`) keep the JPA entity off the wire.
- **`ai/`** — `AiTaskController` → `AiTaskService` → `OpenAiClient` interface (two impls: `SpringAiOpenAiClient`, `StubOpenAiClient`). AI output is **never persisted**; the UI shows a preview and the user posts it back to `/tasks` if they accept. `breakdown` reads the task by ID from `TaskRepository`, but only sanitized copies of title/description are sent to the model.

`MetaController` exposes `GET /meta` returning `{ stubMode }`, backed by the `StubModeIndicator` record bean that `SpringAiConfig` registers based on which `OpenAiClient` impl was wired.

`GlobalExceptionHandler` (a `@RestControllerAdvice`) is the single source of HTTP error mapping. Throw the typed exceptions (`TaskNotFoundException`, `PromptValidationException`, `AiResponseException`, `AiServiceUnavailableException`) and they become 404/422/502/503 respectively. Don't return `ResponseEntity` for errors from controllers.

The UI is a single static `src/main/resources/static/index.html` served at `/` by Spring Boot. Open it via `http://localhost:8080`, **not** by double-clicking the file — `file://` triggers same-origin failures on `fetch()`.

## Prompt-injection defenses

`AiTaskService` applies 9 layered defenses around every model call. When editing it, preserve the layering — they are individually weak but interlocking. The full list is in README.md; the non-obvious points:

- Both `title` AND `description` are user-supplied (via `POST /tasks`), so the breakdown path control-strips and sentinel-wraps **both** before sending. Treating title as "trusted" because it's short would leave an injection vector open.
- `dueDate` validation allows `today - 1 day` to absorb timezone drift between server (often UTC in a container), model anchor, and user's local clock. Tests setting past dates within that window must remain valid.
- The suspicious-pattern check (`flagSuspicious`) **logs and continues** — it does not block. Don't tighten it to a reject without weighing false positives.
- Refusal-marker detection (`REFUSAL` regex) maps to 502 `model_returned_invalid_response`, not 422, because a refusal indicates the model failed to produce structured output we can use — not bad user input.

## Spring AI configuration gotchas

- **Read `OPENAI_API_KEY` directly with `@Value("${OPENAI_API_KEY:}")`, never via `spring.ai.openai.api-key`.** The yml has a placeholder default (`stub-mode-no-real-calls`) so Spring AI 1.0.7's `Assert.hasText()` passes at boot — reading the property here would break stub-mode detection.
- **All chat options (model, temperature, etc.) live in `application.yml` only.** Spring AI precedence is per-call > builder > yml, so duplicating in `ChatClient.Builder.defaultOptions()` or per-call `.options()` would silently shadow the yml. Single source of truth = the yml block.
- The pinned versions (`Boot 3.5.0` + `springAiVersion = 1.0.7`) are deliberate — Spring AI 1.0.6 added Boot 3.5 dependency-managed compatibility, and 1.0.7 is the latest safe pairing. Don't bump one without the other.
- Model is `gpt-4o-mini`, not `gpt-4.1-mini`. The older model has a longer production track record on `response_format=json_schema`; community reports show intermittent failures on `gpt-4.1-mini` for the same feature.

## Testing layout

Unit tests use plain JUnit + Mockito. Integration tests (`*IntegrationTest`) use `@SpringBootTest` and a real in-memory H2. The integration tests for AI endpoints (`AiEndpointIntegrationTest`) inject a mocked `OpenAiClient` to keep them offline — preserve that pattern when adding tests; no real network calls in CI.
