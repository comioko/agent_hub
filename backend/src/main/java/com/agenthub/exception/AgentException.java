package com.agenthub.exception;

public class AgentException extends RuntimeException {
    private final int code;

    public AgentException(String message) {
        super(message);
        this.code = 3001;
    }

    public AgentException(int code, String message) {
        super(message);
        this.code = code;
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
        this.code = 3001;
    }

    public int getCode() {
        return code;
    }
}
