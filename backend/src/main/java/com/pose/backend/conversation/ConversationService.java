package com.pose.backend.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.ApiRequestException;
import com.pose.backend.api.JsonInput;
import com.pose.backend.api.PageResponse;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.channel.ChannelReplyAdapter;
import com.pose.backend.crm.CrmAccess;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.identity.FoundationAccess;
import com.pose.backend.identity.PermissionCode;
import com.pose.backend.identity.RoleCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ConversationService {
    private static final Set<String> STATUSES = Set.of("OPEN", "PENDING", "CLOSED");
    private static final int TEXT_LIMIT = 4000;
    private final JdbcTemplate jdbc;
    private final FoundationAccess access;
    private final CrmAccess crm;
    private final ConversationAccessScope conversationAccess;
    private final ConversationInteractionSessionService interactionSessions;
    private final FoundationAuditRepository audit;
    private final List<ChannelReplyAdapter> adapters;
    private final Clock clock;

    public ConversationService(JdbcTemplate jdbc, FoundationAccess access, CrmAccess crm,
                               ConversationAccessScope conversationAccess,
                               ConversationInteractionSessionService interactionSessions,
                               FoundationAuditRepository audit, List<ChannelReplyAdapter> adapters, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.crm = crm;
        this.conversationAccess = conversationAccess;
        this.interactionSessions = interactionSessions;
        this.audit = audit;
        this.adapters = List.copyOf(adapters);
        this.clock = clock;
    }

    @Transactional
    public PageResponse<ConversationResponse> list(ActorContext incoming, int page, int size,
                                                   String status, boolean assignedToMe) {
        PageResponse.validate(page, size);
        ActorContext actor = access.lockAndRefresh(incoming);
        SqlScope scope = scope(actor, PermissionCode.CONVERSATION_READ);
        String normalizedStatus = status == null ? null : enumStatus(status);
        StringBuilder where = new StringBuilder("c.organization_id=? AND ").append(scope.where());
        List<Object> args = new ArrayList<>();
        args.add(actor.organizationId());
        args.addAll(scope.args());
        if (normalizedStatus != null) add(where, args, "c.status=?", normalizedStatus);
        if (assignedToMe) add(where, args, "c.assigned_user_id=?", actor.userId());

        String from = " FROM conversations c JOIN channel_accounts ca ON ca.organization_id=c.organization_id AND ca.id=c.channel_account_id ";
        List<ConversationResponse> items = jdbc.query("""
                SELECT c.id,ca.channel_type,ca.display_name,c.status,c.assigned_user_id,c.lead_id,c.customer_id,
                       c.last_inbound_at,c.last_message_at
                """ + from + " WHERE " + where + " ORDER BY COALESCE(c.last_message_at,c.created_at) DESC,c.id LIMIT ? OFFSET ?",
                (rs, row) -> new ConversationResponse(rs.getObject("id", UUID.class), rs.getString("channel_type"),
                        rs.getString("display_name"), rs.getString("status"),
                        rs.getObject("assigned_user_id", UUID.class), rs.getObject("lead_id", UUID.class),
                        rs.getObject("customer_id", UUID.class), instant(rs, "last_inbound_at"),
                        instant(rs, "last_message_at")), withPage(args, size, (long) page * size).toArray());
        Long total = jdbc.queryForObject("SELECT count(*)" + from + " WHERE " + where, Long.class, args.toArray());
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    @Transactional
    public PageResponse<ConversationMessageResponse> messages(ActorContext incoming, UUID conversationId,
                                                               int page, int size) {
        PageResponse.validate(page, size);
        ActorContext actor = access.lockAndRefresh(incoming);
        ConversationRecord conversation = findScoped(actor, conversationId, PermissionCode.CONVERSATION_READ, false);
        String where = "organization_id=? AND conversation_id=?";
        List<Object> args = List.of(actor.organizationId(), conversation.id());
        List<ConversationMessageResponse> items = jdbc.query("""
                SELECT id,direction,content_type,content,sent_by_user_id,delivery_status,occurred_at,created_at
                FROM conversation_messages WHERE """ + " " + where + " ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new ConversationMessageResponse(rs.getObject("id", UUID.class), rs.getString("direction"),
                        rs.getString("content_type"), rs.getString("content"), rs.getObject("sent_by_user_id", UUID.class),
                        rs.getString("delivery_status"), instant(rs, "occurred_at"), instant(rs, "created_at")),
                withPage(args, size, (long) page * size).toArray());
        Long total = jdbc.queryForObject("SELECT count(*) FROM conversation_messages WHERE " + where,
                Long.class, args.toArray());
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    @Transactional
    public ConversationReplyResponse reply(ActorContext incoming, UUID conversationId, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        requireRolePermission(actor, PermissionCode.CONVERSATION_REPLY,
                RoleCode.ADMIN, RoleCode.MANAGER, RoleCode.SALES_REP, RoleCode.CUSTOMER_CARE);
        ConversationRecord conversation = findScoped(actor, conversationId, PermissionCode.CONVERSATION_READ, true);
        if ("CLOSED".equals(conversation.status())) throw ApiRequestException.conflict("CONVERSATION_CLOSED");
        if (!"ACTIVE".equals(conversation.accountStatus())) throw ApiRequestException.conflict("CHANNEL_ACCOUNT_INACTIVE");
        JsonInput input = JsonInput.of(body, "content");
        String content = input.text("content", TEXT_LIMIT, true, false);
        ChannelReplyAdapter adapter = adapters.stream().filter(a -> a.channelType().equals(conversation.channelType()))
                .findFirst().orElseThrow(() -> ApiRequestException.unavailable("CHANNEL_REPLY_NOT_CONFIGURED"));

        // The platform call is synchronous for W13; the database transaction remains tenant-scoped and records only success.
        String externalMessageId = adapter.send(conversation.externalThreadId(), content);
        Instant now = clock.instant();
        UUID messageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,
                    content_type,content,sent_by_user_id,delivery_status,occurred_at,created_at)
                VALUES (?,?,?,'OUTBOUND',?,'TEXT',?,?,'SENT',?,?)
                """, messageId, actor.organizationId(), conversationId, externalMessageId, content, actor.userId(),
                Timestamp.from(now), Timestamp.from(now));
        interactionSessions.attach(actor.organizationId(), conversationId, messageId, conversation.channelType(), now, now);
        jdbc.update("UPDATE conversations SET last_message_at=?,updated_at=? WHERE organization_id=? AND id=?",
                Timestamp.from(now), Timestamp.from(now), actor.organizationId(), conversationId);
        audit.record(actor, "MESSAGE_SENT", "CONVERSATION_MESSAGE", messageId, null,
                Map.of("conversationId", conversationId, "channelType", conversation.channelType(),
                        "direction", "OUTBOUND", "contentType", "TEXT"), now);
        return new ConversationReplyResponse(messageId, "OUTBOUND", content, "SENT", externalMessageId, now);
    }

    @Transactional
    public ConversationResponse link(ActorContext incoming, UUID conversationId, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        ConversationRecord before = findScoped(actor, conversationId, PermissionCode.CONVERSATION_READ, true);
        JsonInput input = JsonInput.of(body, "leadId", "customerId");
        UUID leadId = optionalUuid(input, "leadId");
        UUID customerId = optionalUuid(input, "customerId");
        if ((leadId == null) == (customerId == null))
            throw JsonInput.invalid("leadId", "Chọn đúng một Lead hoặc Customer có sẵn.");
        if ((before.leadId() != null || before.customerId() != null)
                && !(leadId != null && leadId.equals(before.leadId()))
                && !(customerId != null && customerId.equals(before.customerId()))) {
            throw ApiRequestException.conflict("CONVERSATION_ALREADY_LINKED");
        }
        if (leadId != null && leadId.equals(before.leadId()) || customerId != null && customerId.equals(before.customerId())) {
            return response(before);
        }
        validateProfileScope(actor, leadId, customerId);
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE conversations SET lead_id=?,customer_id=?,updated_at=?
                WHERE organization_id=? AND id=?
                """, leadId, customerId, Timestamp.from(now), actor.organizationId(), conversationId);
        jdbc.update("""
                UPDATE interactions SET lead_id=?,customer_id=?,updated_at=?
                WHERE organization_id=? AND conversation_id=? AND recorded_by_actor_type='SYSTEM'
                """, leadId, customerId, Timestamp.from(now), actor.organizationId(), conversationId);
        audit.record(actor, "CONVERSATION_LINKED", "CONVERSATION", conversationId,
                Map.of("leadId", before.leadId() == null ? "" : before.leadId(),
                        "customerId", before.customerId() == null ? "" : before.customerId()),
                Map.of("leadId", leadId == null ? "" : leadId, "customerId", customerId == null ? "" : customerId), now);
        return response(findScoped(actor, conversationId, PermissionCode.CONVERSATION_READ, false));
    }

    @Transactional
    public ConversationResponse assign(ActorContext incoming, UUID conversationId, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        if (!actor.hasPermission(PermissionCode.CONVERSATION_ASSIGN)
                || !crm.roleWith(actor, RoleCode.MANAGER, PermissionCode.CONVERSATION_ASSIGN)) FoundationAccess.denyPermission();
        ConversationRecord before = findScoped(actor, conversationId, PermissionCode.CONVERSATION_READ, true);
        JsonInput input = JsonInput.of(body, "assignedUserId");
        UUID ownerId = input.uuid("assignedUserId");
        if (!eligibleTeamMember(actor.organizationId(), before.teamId(), ownerId))
            throw ApiRequestException.unprocessable("assignedUserId", "Chọn thành viên đang hoạt động của team có quyền trả lời hội thoại.");
        if (ownerId.equals(before.assignedUserId())) return response(before);
        Instant now = clock.instant();
        jdbc.update("UPDATE conversations SET assigned_user_id=?,updated_at=? WHERE organization_id=? AND id=?",
                ownerId, Timestamp.from(now), actor.organizationId(), conversationId);
        List<AssignedWorkflowTask> openTasks = jdbc.query("""
                SELECT id,owner_user_id FROM crm_tasks
                WHERE organization_id=? AND conversation_id=? AND origin='WORKFLOW'
                  AND status IN ('OPEN','IN_PROGRESS') AND owner_user_id<>?
                FOR UPDATE
                """, (rs, row) -> new AssignedWorkflowTask(rs.getObject("id", UUID.class),
                rs.getObject("owner_user_id", UUID.class)), actor.organizationId(), conversationId, ownerId);
        jdbc.update("""
                UPDATE crm_tasks SET owner_user_id=?,updated_at=?
                WHERE organization_id=? AND conversation_id=? AND origin='WORKFLOW'
                  AND status IN ('OPEN','IN_PROGRESS') AND owner_user_id<>?
                """, ownerId, Timestamp.from(now), actor.organizationId(), conversationId, ownerId);
        for (AssignedWorkflowTask task : openTasks) {
            audit.record(actor, "TASK_ASSIGNED", "CRM_TASK", task.id(), Map.of("ownerUserId", task.previousOwnerId()),
                    Map.of("ownerUserId", ownerId, "conversationId", conversationId), now);
        }
        audit.record(actor, "CONVERSATION_ASSIGNED", "CONVERSATION", conversationId,
                Map.of("assignedUserId", before.assignedUserId() == null ? "" : before.assignedUserId()),
                Map.of("assignedUserId", ownerId), now);
        return response(findScoped(actor, conversationId, PermissionCode.CONVERSATION_READ, false));
    }

    private ConversationRecord findScoped(ActorContext actor, UUID conversationId, PermissionCode permission, boolean lock) {
        SqlScope scope = scope(actor, permission);
        String query = """
                SELECT c.id,c.channel_account_id,c.external_thread_id,c.status,c.assigned_user_id,c.lead_id,c.customer_id,
                       ca.channel_type,ca.display_name,ca.status AS account_status,ca.team_id,c.last_inbound_at,c.last_message_at
                FROM conversations c JOIN channel_accounts ca ON ca.organization_id=c.organization_id AND ca.id=c.channel_account_id
                WHERE c.organization_id=? AND c.id=? AND """ + " " + scope.where() + (lock ? " FOR UPDATE" : "");
        List<Object> args = new ArrayList<>(List.of(actor.organizationId(), conversationId));
        args.addAll(scope.args());
        List<ConversationRecord> rows = jdbc.query(query, (rs, row) -> new ConversationRecord(rs.getObject("id", UUID.class),
                rs.getObject("channel_account_id", UUID.class), rs.getString("external_thread_id"),
                rs.getString("status"), rs.getObject("assigned_user_id", UUID.class),
                rs.getObject("lead_id", UUID.class), rs.getObject("customer_id", UUID.class),
                rs.getString("channel_type"), rs.getString("display_name"), rs.getString("account_status"),
                rs.getObject("team_id", UUID.class), instant(rs, "last_inbound_at"),
                instant(rs, "last_message_at")), args.toArray());
        if (rows.isEmpty()) throw ApiRequestException.notFound();
        return rows.getFirst();
    }

    private SqlScope scope(ActorContext actor, PermissionCode permission) {
        if (permission != PermissionCode.CONVERSATION_READ) FoundationAccess.denyPermission();
        if (actor.hasRole(RoleCode.DATA_STAFF) || !actor.hasPermission(permission)) FoundationAccess.denyPermission();
        return conversationAccess.readable(actor, "c", "ca")
                .map(scope -> new SqlScope(scope.where(), scope.args()))
                .orElseThrow(() -> new com.pose.backend.identity.AuthorizationDeniedException(
                        com.pose.backend.identity.AuthorizationFailureCode.FORBIDDEN_SCOPE, "Conversation scope denied"));
    }

    private void requireRolePermission(ActorContext actor, PermissionCode permission, RoleCode... roles) {
        if (!actor.hasPermission(permission)) FoundationAccess.denyPermission();
        for (RoleCode role : roles) if (crm.roleWith(actor, role, permission)) return;
        FoundationAccess.denyPermission();
    }

    private void validateProfileScope(ActorContext actor, UUID leadId, UUID customerId) {
        boolean lead = leadId != null;
        CrmAccess.Scope profileScope = crm.scope(actor, lead, false);
        String table = lead ? "leads l" : "customers c";
        String id = lead ? "l.id" : "c.id";
        UUID selected = lead ? leadId : customerId;
        List<Object> args = new ArrayList<>(profileScope.args());
        args.add(selected);
        if (jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + profileScope.where() + " AND " + id + "=?",
                Integer.class, args.toArray()) == 0) FoundationAccess.denyScope();
    }

    private boolean eligibleTeamMember(UUID organizationId, UUID teamId, UUID userId) {
        if (teamId == null) return false;
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.queryForObject("""
                SELECT count(*) FROM users u
                JOIN team_memberships tm ON tm.organization_id=u.organization_id AND tm.user_id=u.id AND tm.team_id=?
                JOIN user_role_grants g ON g.organization_id=u.organization_id AND g.user_id=u.id
                JOIN roles r ON r.organization_id=g.organization_id AND r.id=g.role_id AND r.status='ACTIVE'
                JOIN role_permissions rp ON rp.role_id=r.id
                JOIN permissions p ON p.id=rp.permission_id AND p.code='conversation.reply'
                WHERE u.organization_id=? AND u.id=? AND u.status='ACTIVE' AND u.archived_at IS NULL
                  AND tm.status='ACTIVE' AND tm.joined_at<=? AND (tm.left_at IS NULL OR tm.left_at>?)
                  AND g.granted_at<=? AND (g.revoked_at IS NULL OR g.revoked_at>?)
                """, Integer.class, teamId, organizationId, userId, now, now, now, now) > 0;
    }

    private static List<Object> withPage(List<Object> args, int size, long offset) {
        List<Object> result = new ArrayList<>(args);
        result.add(size);
        result.add(offset);
        return result;
    }

    private static UUID optionalUuid(JsonInput input, String field) {
        return input.missingOrBlank(field) ? null : input.uuid(field);
    }

    private static String enumStatus(String status) {
        String normalized = status.strip().toUpperCase(java.util.Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw JsonInput.invalid("status", "Trạng thái hội thoại không hợp lệ.");
        return normalized;
    }

    private static void add(StringBuilder where, List<Object> args, String predicate, Object value) {
        where.append(" AND ").append(predicate);
        args.add(value);
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static ConversationResponse response(ConversationRecord value) {
        return new ConversationResponse(value.id(), value.channelType(), value.channelName(), value.status(), value.assignedUserId(),
                value.leadId(), value.customerId(), value.lastInboundAt(), value.lastMessageAt());
    }

    private record SqlScope(String where, List<Object> args) {}
    private record AssignedWorkflowTask(UUID id, UUID previousOwnerId) {}
    private record ConversationRecord(UUID id, UUID channelAccountId, String externalThreadId, String status,
                                      UUID assignedUserId, UUID leadId, UUID customerId, String channelType,
                                      String channelName, String accountStatus, UUID teamId,
                                      Instant lastInboundAt, Instant lastMessageAt) {}
}
