package com.eulerity.taskmanager.ai;

public class PromptValidationException extends RuntimeException {
    public PromptValidationException(String reason) {
        super(reason);
    }
}
