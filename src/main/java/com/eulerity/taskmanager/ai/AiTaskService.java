package com.eulerity.taskmanager.ai;

import com.eulerity.taskmanager.ai.dto.BreakdownResponse;
import com.eulerity.taskmanager.ai.dto.Subtask;
import com.eulerity.taskmanager.ai.dto.SuggestedTask;
import com.eulerity.taskmanager.ai.prompts.Prompts;
import com.eulerity.taskmanager.task.Priority;
import com.eulerity.taskmanager.task.Status;
import com.eulerity.taskmanager.task.Task;
import com.eulerity.taskmanager.task.TaskNotFoundException;
import com.eulerity.taskmanager.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AiTaskService {

    private static final Logger log = LoggerFactory.getLogger(AiTaskService.class);

    private static final int SUGGEST_MAX_CHARS = 1000;
    private static final int BREAKDOWN_MAX_CHARS = 2200;

    // Defense #2: explicit control-char range; \n (0x0A), \r (0x0D), \t (0x09) preserved.
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

    // Defense #5: known jailbreak phrases. Soft flag -- log, don't block.
    private static final Pattern SUSPICIOUS = Pattern.compile(
            "(?i)(ignore (previous|prior|above)|system prompt|you are now|\\bDAN\\b|developer mode)");

    // Defense #8: refusal markers (model declining the task).
    private static final Pattern REFUSAL = Pattern.compile(
            "^(I cannot|I can't|I'm sorry|I am sorry|I am unable|I won't)\\b",
            Pattern.CASE_INSENSITIVE);

    private final OpenAiClient client;
    private final TaskRepository taskRepo;

    public AiTaskService(OpenAiClient client, TaskRepository taskRepo) {
        this.client = client;
        this.taskRepo = taskRepo;
    }

    public SuggestedTask suggest(String rawText) {
        String sanitized = enforceLengthAndStrip(rawText, SUGGEST_MAX_CHARS);
        flagSuspicious(sanitized);
        String wrapped = wrapInSentinels(sanitized);

        SuggestedTask raw = client.suggest(wrapped);   // may throw AiResponseException (defense #6)
        validateSuggestion(raw);                       // defenses #7, #8
        return new SuggestedTask(
                stripSentinels(raw.title()),
                stripSentinels(raw.description()),
                raw.dueDate(),
                raw.priority(),
                raw.status()
        );
    }

    public BreakdownResponse breakdown(long taskId) {
        Task t = taskRepo.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        String content = (t.getTitle() == null ? "" : t.getTitle())
                + "\n"
                + (t.getDescription() == null ? "" : t.getDescription());
        String sanitized = enforceLengthAndStrip(content, BREAKDOWN_MAX_CHARS);
        flagSuspicious(sanitized);

        // Reconstruct a sanitized Task to hand to the client (don't mutate the persisted one).
        // Both title and description are user-supplied via POST /tasks, so BOTH get the
        // full defense-#3 treatment: control-strip and sentinel-wrap. Treating title as
        // "trusted" because it's short would leave an injection vector wide open.
        Task safe = new Task();
        safe.setId(t.getId());
        safe.setTitle(wrapInSentinels(stripControl(t.getTitle())));
        safe.setDescription(wrapInSentinels(stripControl(t.getDescription())));
        safe.setPriority(t.getPriority());
        safe.setStatus(t.getStatus());

        BreakdownResponse raw = client.breakdown(safe);
        validateBreakdown(raw);
        // Strip sentinels from subtask titles in case the model echoed them.
        List<Subtask> cleaned = raw.subtasks().stream()
                .map(s -> new Subtask(s.order(), stripSentinels(s.title()),
                        s.estimatedMinutes(), s.priority()))
                .toList();
        return new BreakdownResponse(raw.taskId(), cleaned);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private String enforceLengthAndStrip(String text, int max) {
        String input = text == null ? "" : text;
        if (input.length() > max) {
            throw new PromptValidationException("input exceeds maximum length (" + max + ")");
        }
        return stripControl(input);
    }

    private String stripControl(String s) {
        return s == null ? null : CONTROL_CHARS.matcher(s).replaceAll("");
    }

    private void flagSuspicious(String s) {
        if (s == null) return;
        if (SUSPICIOUS.matcher(s).find()) {
            log.warn("suspicious pattern in AI input (hash={})", sha256(s));
        }
    }

    private String wrapInSentinels(String s) {
        return Prompts.USER_INPUT_BEGIN + "\n" + (s == null ? "" : s) + "\n" + Prompts.USER_INPUT_END;
    }

    private String stripSentinels(String s) {
        if (s == null) return null;
        return s.replace(Prompts.USER_INPUT_BEGIN, "")
                .replace(Prompts.USER_INPUT_END, "")
                .trim();
    }

    private void validateSuggestion(SuggestedTask t) {
        if (t == null) throw new AiResponseException("model returned null");
        if (t.title() == null || t.title().isBlank())
            throw new AiResponseException("model returned blank title");
        if (REFUSAL.matcher(t.title().trim()).find()
                || (t.description() != null && REFUSAL.matcher(t.description().trim()).find())) {
            throw new AiResponseException("model returned a refusal");
        }
        if (t.priority() == null) throw new AiResponseException("model returned null priority");
        if (t.status() == null)   throw new AiResponseException("model returned null status");
        // 1-day buffer: "today" is server-local (UTC in a container, local-tz in dev),
        // and the model may anchor to the user's tz. Tolerate one day of drift.
        if (t.dueDate() != null && t.dueDate().isBefore(LocalDate.now().minusDays(1))) {
            throw new AiResponseException("model returned dueDate before today - 1 day");
        }
    }

    private void validateBreakdown(BreakdownResponse r) {
        if (r == null) throw new AiResponseException("model returned null");
        if (r.subtasks() == null || r.subtasks().isEmpty())
            throw new AiResponseException("model returned no subtasks");
        for (Subtask s : r.subtasks()) {
            if (s.title() == null || s.title().isBlank())
                throw new AiResponseException("model returned blank subtask title");
            if (REFUSAL.matcher(s.title().trim()).find())
                throw new AiResponseException("model returned a refusal in subtasks");
            if (s.priority() == null)
                throw new AiResponseException("model returned null subtask priority");
            if (s.estimatedMinutes() <= 0)
                throw new AiResponseException("model returned non-positive estimatedMinutes");
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "unavailable";
        }
    }
}
