package com.eulerity.taskmanager.ai;

public class AiResponseException extends RuntimeException {
    public AiResponseException(String reason) {
        super(reason);
    }

    public AiResponseException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
