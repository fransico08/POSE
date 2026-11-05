package com.pose.backend.audit;

public final class AuditWriteException extends RuntimeException {
    public AuditWriteException(RuntimeException cause) { super("Audit write failed", cause); }
}
