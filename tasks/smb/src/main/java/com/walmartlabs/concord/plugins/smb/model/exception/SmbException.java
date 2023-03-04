package com.walmartlabs.concord.plugins.smb.model.exception;

public class SmbException extends RuntimeException {
    public SmbException(String message) {
        super(message);
    }

    public SmbException(String message, Throwable cause) {
        super(message, cause);
    }
}
