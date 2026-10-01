package com.pose.backend.auth;

import com.pose.backend.security.JwtActorContextResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final JwtActorContextResolver actors;

    public AuthController(AuthService auth, JwtActorContextResolver actors) {
        this.auth = auth;
        this.actors = actors;
    }

    public record LoginRequest(@NotBlank @Size(max = 100) String organizationCode,
                               @NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 256) String password) {
        public LoginRequest {
            if (organizationCode != null) organizationCode = organizationCode.strip();
            if (email != null) email = email.strip();
        }

        @Override
        public String toString() { return "LoginRequest[redacted]"; }
    }

    public record RefreshTokenRequest(@NotBlank @Size(max = 512) String refreshToken) {
        @Override
        public String toString() { return "RefreshTokenRequest[redacted]"; }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokensResponse> login(@Valid @RequestBody LoginRequest request) {
        return noStore(auth.login(request.organizationCode(), request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokensResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return noStore(auth.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody RefreshTokenRequest request) {
        auth.logout(actors.currentActor(), UUID.fromString(jwt.getClaimAsString("sessionId")), request.refreshToken());
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me() {
        return noStore(auth.me(actors.currentActor()));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
