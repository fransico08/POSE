package com.pose.backend.auth;

public class AuthException extends RuntimeException {
    private final String code;

    public AuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }

    public static AuthException invalidCredentials() {
        return new AuthException("AUTH_INVALID_CREDENTIALS", "Thông tin đăng nhập không hợp lệ.");
    }

    public static AuthException invalidRefreshToken() {
        return new AuthException("AUTH_INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ hoặc đã hết hiệu lực.");
    }

    public static AuthException invalidAccessToken() {
        return new AuthException("AUTH_INVALID_TOKEN", "Phiên đăng nhập không còn hiệu lực.");
    }
}
