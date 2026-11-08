package com.pose.backend.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.ai.AiGateway;
import com.pose.backend.ai.AiManagementService;
import com.pose.backend.ai.AiSettings;
import com.pose.backend.ai.AiUseCase;
import com.pose.backend.api.JsonInput;
import com.pose.backend.customer360.Customer360Service;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.identity.FoundationAccess;
import com.pose.backend.identity.PermissionCode;
import com.pose.backend.identity.RoleCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CopilotService {
    public static final Map<String, Object> COPILOT_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("sections"),
            "properties", Map.of(
                    "sections", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "required", List.of("heading", "points"),
                                    "properties", Map.of(
                                            "heading", Map.of("type", "string"),
                                            "points", Map.of(
                                                    "type", "array",
                                                    "items", Map.of(
                                                            "type", "object",
                                                            "additionalProperties", false,
                                                            "required", List.of("text", "sources"),
                                                            "properties", Map.of(
                                                                    "text", Map.of("type", "string"),
                                                                    "sources", Map.of(
                                                                            "type", "array",
                                                                            "items", Map.of("type", "string")
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
            )
    );

    private final AiGateway gateway;
    private final AiSettings settings;
    private final Customer360Service customer360;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CopilotService(AiGateway gateway, AiSettings settings, Customer360Service customer360,
                          JdbcTemplate jdbc, Clock clock) {
        this.gateway = gateway;
        this.settings = settings;
        this.customer360 = customer360;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public CopilotResponse summarizeCustomer(ActorContext actor, UUID customerId, JsonNode body) {
        return invokeCustomer(actor, customerId, AiUseCase.C360_SUMMARY, body);
    }

    public CopilotResponse generateHandoverBrief(ActorContext actor, UUID customerId, JsonNode body) {
        return invokeCustomer(actor, customerId, AiUseCase.HANDOVER_BRIEF, body);
    }

    private CopilotResponse invokeCustomer(ActorContext actor, UUID customerId, AiUseCase useCase, JsonNode body) {
        boolean force = parseForce(body);
        if (!settings.enabled() || (settings.provider().equals("claude") && settings.apiKey().isBlank())) {
            AiGateway.Result disabled = gateway.invoke(actor, useCase, "CUSTOMER", customerId, Map.of("jsonSchema", COPILOT_SCHEMA));
            return new CopilotResponse(disabled.status(), disabled.errorCode(), false, disabled.invocationId(), null);
        }
        if (!actor.hasPermission(PermissionCode.AI_ASSIST_USE) || actor.hasRole(RoleCode.DATA_STAFF)) {
            FoundationAccess.denyPermission();
        }
        customer360.profile(actor, customerId);
        int reuseMinutes = settings.reuseMinutes();
        if (!force && reuseMinutes > 0) {
            Instant cutoff = clock.instant().minus(Duration.ofMinutes(reuseMinutes));
            List<AiManagementService.Suggestion> existing = jdbc.query("""
                    SELECT * FROM ai_suggestions
                    WHERE organization_id=? AND subject_type='CUSTOMER' AND subject_id=? AND kind=?
                      AND status='PROPOSED' AND created_at>=?
                    ORDER BY created_at DESC, id
                    LIMIT 1
                    """, (rs, n) -> mapSuggestion(rs), actor.organizationId(), customerId, useCase.name(), Timestamp.from(cutoff));
            if (!existing.isEmpty()) {
                AiManagementService.Suggestion reused = existing.getFirst();
                return new CopilotResponse("SUCCEEDED", null, true, reused.invocationId(), reused);
            }
        }
        AiGateway.Result result = gateway.invoke(actor, useCase, "CUSTOMER", customerId, Map.of("jsonSchema", COPILOT_SCHEMA));
        AiManagementService.Suggestion suggestion = null;
        if (result.suggestionId() != null) {
            List<AiManagementService.Suggestion> rows = jdbc.query(
                    "SELECT * FROM ai_suggestions WHERE organization_id=? AND id=?",
                    (rs, n) -> mapSuggestion(rs), actor.organizationId(), result.suggestionId());
            if (!rows.isEmpty()) {
                suggestion = rows.getFirst();
            }
        }
        return new CopilotResponse(result.status(), result.errorCode(), false, result.invocationId(), suggestion);
    }

    private static boolean parseForce(JsonNode body) {
        if (body == null || body.isNull()) return false;
        JsonInput in = JsonInput.of(body, "force");
        if (!in.has("force")) return false;
        JsonNode field = body.get("force");
        if (field == null || field.isNull()) return false;
        if (!field.isBoolean()) {
            throw JsonInput.invalid("force", "Trường force phải có kiểu boolean.");
        }
        return field.asBoolean();
    }

    private static AiManagementService.Suggestion mapSuggestion(ResultSet rs) throws SQLException {
        Timestamp reviewed = rs.getTimestamp("reviewed_at");
        return new AiManagementService.Suggestion(
                rs.getObject("id", UUID.class),
                rs.getObject("invocation_id", UUID.class),
                rs.getString("kind"),
                rs.getString("subject_type"),
                rs.getObject("subject_id", UUID.class),
                rs.getString("content"),
                AiManagementService.normalizeJson(rs.getString("evidence")),
                rs.getString("status"),
                rs.getString("final_content"),
                rs.getObject("reviewed_by", UUID.class),
                reviewed == null ? null : reviewed.toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}

