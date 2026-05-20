package com.eulerity.taskmanager.ai;

public class AiServiceUnavailableException extends RuntimeException {
    public AiServiceUnavailableException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
