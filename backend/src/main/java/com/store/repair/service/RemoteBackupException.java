package com.store.repair.service;

public class RemoteBackupException extends RuntimeException {

    private final boolean retryable;

    public RemoteBackupException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public RemoteBackupException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
