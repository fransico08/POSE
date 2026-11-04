package com.pose.backend.audit;

import com.pose.backend.api.*;
import com.pose.backend.crm.CrmAccess;
import com.pose.backend.crm.CrmWorkAccess;
import com.pose.backend.identity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class AuditQueryService {
    private static final Set<String> ENTITY_TYPES = Set.of("INTERACTION", "CRM_TASK");
    private final AuditQueryRepository repository;
    private final CrmWorkAccess workAccess;

    public AuditQueryService(AuditQueryRepository repository, CrmWorkAccess workAccess) {
        this.repository = repository; this.workAccess = workAccess;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(ActorContext actor, int page, int size, String entityType,
            UUID entityId, String action, UUID actorUserId, Instant occurredFrom, Instant occurredTo) {
        PageResponse.validate(page, size);
        CrmAccess.Scope scope = scope(actor);
        StringBuilder where = new StringBuilder("a.organization_id=? AND a.entity_type IN ('INTERACTION','CRM_TASK') AND ")
                .append(scope.where());
        List<Object> args = new ArrayList<>(); args.add(actor.organizationId()); args.addAll(scope.args());
        if (entityType != null) {
            String type = entityType.strip().toUpperCase(Locale.ROOT);
            if (!ENTITY_TYPES.contains(type)) throw JsonInput.invalid("entityType", "Loại dữ liệu không hợp lệ.");
            add(where, args, "a.entity_type=?", type);
        }
        if (entityId != null) add(where, args, "a.entity_id=?", entityId);
        if (action != null) {
            String normalized = action.strip().toUpperCase(Locale.ROOT);
            if (!normalized.matches("[A-Z][A-Z0-9_]{0,119}")) throw JsonInput.invalid("action", "Mã thao tác không hợp lệ.");
            add(where, args, "a.action=?", normalized);
        }
        if (actorUserId != null) add(where, args, "a.actor_user_id=?", actorUserId);
        if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) throw JsonInput.invalid("occurredFrom", "Thời điểm bắt đầu phải trước thời điểm kết thúc.");
        if (occurredFrom != null) add(where, args, "a.occurred_at>=?", Timestamp.from(occurredFrom));
        if (occurredTo != null) add(where, args, "a.occurred_at<=?", Timestamp.from(occurredTo));
        return new PageResponse<>(repository.list(where.toString(), args, page, size), page, size,
                repository.count(where.toString(), args));
    }

    @Transactional(readOnly = true)
    public AuditEventResponse get(ActorContext actor, UUID id) {
        CrmAccess.Scope scope = scope(actor);
        String where = "a.organization_id=? AND a.entity_type IN ('INTERACTION','CRM_TASK') AND " + scope.where();
        List<Object> args = new ArrayList<>(); args.add(actor.organizationId()); args.addAll(scope.args());
        return repository.find(where, args, id).orElseGet(() -> { FoundationAccess.denyScope(); return null; });
    }

    private CrmAccess.Scope scope(ActorContext actor) {
        if (!actor.hasPermission(PermissionCode.AUDIT_READ)) FoundationAccess.denyPermission();
        if (!actor.hasRole(RoleCode.ADMIN) && !actor.hasRole(RoleCode.MANAGER)) FoundationAccess.denyPermission();
        if (workAccess.hasRolePermission(actor, RoleCode.ADMIN, PermissionCode.AUDIT_READ))
            return new CrmAccess.Scope("1=1", List.of());
        if (!workAccess.hasRolePermission(actor, RoleCode.MANAGER, PermissionCode.AUDIT_READ)) FoundationAccess.denyPermission();
        CrmAccess.Scope interaction = workAccess.managerSubjectScope(actor.userId(), "i", "i.customer_id", "i.lead_id");
        CrmAccess.Scope task = workAccess.managerSubjectScope(actor.userId(), "t", "t.customer_id", "t.lead_id");
        String where = "( (a.entity_type='INTERACTION' AND EXISTS (SELECT 1 FROM interactions i WHERE i.organization_id=a.organization_id AND i.id=a.entity_id AND "
                + interaction.where() + ")) OR (a.entity_type='CRM_TASK' AND EXISTS (SELECT 1 FROM crm_tasks t WHERE t.organization_id=a.organization_id AND t.id=a.entity_id AND "
                + task.where() + ")))";
        List<Object> args = new ArrayList<>(interaction.args()); args.addAll(task.args());
        return new CrmAccess.Scope(where, List.copyOf(args));
    }

    private static void add(StringBuilder where, List<Object> args, String predicate, Object value) { where.append(" AND ").append(predicate); args.add(value); }
}
