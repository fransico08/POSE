package com.pose.backend.identity;

import java.util.Objects;

public final class AuthorizationDeniedException extends RuntimeException {

    private final AuthorizationFailureCode code;

    public AuthorizationDeniedException(AuthorizationFailureCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code is required");
    }

    public AuthorizationFailureCode code() {
        return code;
    }
}
