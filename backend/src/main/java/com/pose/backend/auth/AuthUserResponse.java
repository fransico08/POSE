package com.pose.backend.auth;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public record AuthUserResponse(UUID id, String email, String fullName, String status,
                               Organization organization, List<String> roles,
                               List<String> permissions, List<UUID> teamIds) {
    public record Organization(UUID id, String name) {}

    public static AuthUserResponse from(AuthenticatedAccount account) {
        return new AuthUserResponse(account.userId(), account.email(), account.fullName(), "ACTIVE",
                new Organization(account.organizationId(), account.organizationName()),
                account.roles().stream().map(Enum::name).sorted().toList(),
                account.permissions().stream().map(permission -> permission.code()).sorted().toList(),
                Stream.concat(account.teamIds().stream(), account.managedTeamIds().stream())
                        .distinct().sorted().toList());
    }
}
