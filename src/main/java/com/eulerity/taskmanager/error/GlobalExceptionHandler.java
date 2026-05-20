package com.eulerity.taskmanager.error;

import com.eulerity.taskmanager.ai.AiResponseException;
import com.eulerity.taskmanager.ai.AiServiceUnavailableException;
import com.eulerity.taskmanager.ai.PromptValidationException;
import com.eulerity.taskmanager.task.TaskNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(TaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "task_not_found",
                "id", ex.getId()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", "validation_failed",
                "fields", fields
        ));
    }

    @ExceptionHandler(PromptValidationException.class)
    public ResponseEntity<Map<String, Object>> handlePromptValidation(PromptValidationException ex) {
        return ResponseEntity.unprocessableEntity().body(Map.of(
                "error", "invalid_input",
                "reason", ex.getMessage()
        ));
    }

    @ExceptionHandler(AiResponseException.class)
    public ResponseEntity<Map<String, Object>> handleAiResponse(AiResponseException ex) {
        log.warn("AI response validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "model_returned_invalid_response"
        ));
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleAiUnavailable(AiServiceUnavailableException ex) {
        log.error("AI service unavailable", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "ai_service_unavailable"
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "internal_error"
        ));
    }
}
