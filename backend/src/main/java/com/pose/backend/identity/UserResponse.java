package com.pose.backend.identity;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
public record UserResponse(UUID id, String email, String fullName, String status, JsonNode metadata,
                           List<String> roles, List<String> permissions, List<UUID> teamIds) {}
