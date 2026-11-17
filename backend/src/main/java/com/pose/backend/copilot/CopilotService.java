package com.pose.backend.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.ai.AiGateway;
import com.pose.backend.ai.AiManagementService;
import com.pose.backend.ai.AiUseCase;
import com.pose.backend.api.JsonInput;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.identity.FoundationAccess;
import com.pose.backend.identity.PermissionCode;
import com.pose.backend.identity.RoleCode;
import com.pose.backend.conversation.ConversationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
    private final JdbcTemplate jdbc;
    private final ConversationService conversations;

    public CopilotService(AiGateway gateway, JdbcTemplate jdbc, ConversationService conversations) {
        this.gateway = gateway;
        this.jdbc = jdbc;
        this.conversations = conversations;
    }

    public CopilotResponse summarizeCustomer(ActorContext actor, UUID customerId, JsonNode body) {
        return invokeCustomer(actor, customerId, AiUseCase.C360_SUMMARY, body);
    }

    public CopilotResponse generateHandoverBrief(ActorContext actor, UUID customerId, JsonNode body) {
        return invokeCustomer(actor, customerId, AiUseCase.HANDOVER_BRIEF, body);
    }

    public CopilotResponse replyDraft(ActorContext actor, UUID conversationId, JsonNode body) {
        if (!actor.hasPermission(PermissionCode.AI_ASSIST_USE) || actor.hasRole(RoleCode.DATA_STAFF)) {
            FoundationAccess.denyPermission();
        }
        conversations.draftContext(actor, conversationId, true);
        return invoke(actor, "CONVERSATION", conversationId, AiUseCase.REPLY_DRAFT, body);
    }

    private CopilotResponse invokeCustomer(ActorContext actor, UUID customerId, AiUseCase useCase, JsonNode body) {
        return invoke(actor, "CUSTOMER", customerId, useCase, body);
    }

    private CopilotResponse invoke(ActorContext actor, String subjectType, UUID subjectId, AiUseCase useCase, JsonNode body) {
        boolean force = parseForce(body);
        Map<String,Object> schema = useCase == AiUseCase.REPLY_DRAFT ? REPLY_SCHEMA : COPILOT_SCHEMA;
        AiGateway.Result result = gateway.invoke(actor, useCase, subjectType, subjectId, Map.of("jsonSchema", schema, "reuse", !force));
        AiManagementService.Suggestion suggestion = null;
        if (result.suggestionId() != null) {
            List<AiManagementService.Suggestion> rows = jdbc.query(
                    "SELECT s.* FROM ai_suggestions s JOIN ai_invocations i ON i.id=s.invocation_id AND i.organization_id=s.organization_id WHERE s.organization_id=? AND s.id=? AND i.actor_user_id=?",
                    (rs, n) -> mapSuggestion(rs), actor.organizationId(), result.suggestionId(),actor.userId());
            if (!rows.isEmpty()) {
                suggestion = rows.getFirst();
            }
        }
        return new CopilotResponse(result.status(), result.errorCode(), result.reused(), result.invocationId(), suggestion);
    }

    public static final Map<String, Object> REPLY_SCHEMA = Map.of(
            "type", "object", "additionalProperties", false, "required", List.of("draft", "sources"),
            "properties", Map.of("draft", Map.of("type", "string"),
                    "sources", Map.of("type", "array", "items", Map.of("type", "string"))));

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

