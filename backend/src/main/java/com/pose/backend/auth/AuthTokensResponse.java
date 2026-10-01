package com.pose.backend.auth;

public record AuthTokensResponse(String accessToken, String refreshToken, long expiresIn, AuthUserResponse user) {
    @Override
    public String toString() { return "AuthTokensResponse[userId=" + user.id() + "]"; }
}
