package com.pose.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.crm.*;
import com.pose.backend.identity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class TaskService {
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "DONE", "CANCELLED");
    private static final int TEXT_LIMIT = 4000;
    private final TaskRepository repository;
    private final CrmWorkAccess workAccess;
    private final CrmAccess crm;
    private final FoundationAccess access;
    private final FoundationAuditRepository audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TaskService(TaskRepository repository, CrmWorkAccess workAccess, CrmAccess crm,
                       FoundationAccess access, FoundationAuditRepository audit, JdbcTemplate jdbc, Clock clock) {
        this.repository = repository; this.workAccess = workAccess; this.crm = crm;
        this.access = access; this.audit = audit; this.jdbc = jdbc; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(ActorContext actor, int page, int size, UUID customerId, UUID leadId,
            UUID opportunityId, UUID ownerUserId, String status, String priority, Instant dueFrom, Instant dueTo) {
        PageResponse.validate(page, size);
        if (customerId != null && leadId != null) throw JsonInput.invalid("customerId", "Chỉ lọc theo Customer hoặc Lead.");
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) throw JsonInput.invalid("dueFrom", "Thời điểm bắt đầu phải trước thời điểm kết thúc.");
        CrmAccess.Scope scope = recordScope(actor, false);
        StringBuilder where = new StringBuilder("t.organization_id=? AND ").append(scope.where());
        List<Object> args = new ArrayList<>(); args.add(actor.organizationId()); args.addAll(scope.args());
        if (customerId != null) add(where, args, "t.customer_id=?", customerId);
        if (leadId != null) add(where, args, "t.lead_id=?", leadId);
        if (opportunityId != null) add(where, args, "t.opportunity_id=?", opportunityId);
        if (ownerUserId != null) add(where, args, "t.owner_user_id=?", ownerUserId);
        if (status != null) add(where, args, "t.status=?", enumFilter("status", status, STATUSES));
        if (priority != null) add(where, args, "t.priority=?", enumFilter("priority", priority, PRIORITIES));
        if (dueFrom != null) add(where, args, "t.due_at>=?", Timestamp.from(dueFrom));
        if (dueTo != null) add(where, args, "t.due_at<=?", Timestamp.from(dueTo));
        List<TaskResponse> items = repository.list(where.toString(), args, page, size).stream().map(t -> redact(actor, t)).toList();
        return new PageResponse<>(items, page, size, repository.count(where.toString(), args));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(ActorContext actor, UUID id) { return redact(actor, find(actor, id, false)); }

    @Transactional
    public TaskResponse create(ActorContext incoming, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        requireCreator(actor);
        JsonInput in = JsonInput.of(body, "customerId", "leadId", "opportunityId", "ownerUserId", "title", "description", "priority", "dueAt");
        UUID customerId = optionalUuid(in, "customerId"), leadId = optionalUuid(in, "leadId");
        requireSubjectXor(customerId, leadId);
        requireSubject(actor, true, customerId, leadId);
        requireOpenLead(actor.organizationId(), leadId);
        UUID opportunityId = optionalUuid(in, "opportunityId"); validateOpportunity(actor.organizationId(), customerId, leadId, opportunityId);
        UUID requestedOwner = optionalUuid(in, "ownerUserId");
        boolean manager = workAccess.hasRolePermission(actor, RoleCode.MANAGER, PermissionCode.TASK_MANAGE);
        UUID owner = resolveCreateOwner(actor, manager, requestedOwner, customerId, leadId);
        String title = CrmInput.text(in, "title", 220, true, false);
        String description = CrmInput.text(in, "description", TEXT_LIMIT, false, true);
        String priority = priority(CrmInput.text(in, "priority", 20, true, false));
        Instant dueAt = CrmInput.instant(in, "dueAt");
        UUID id = UUID.randomUUID(); Instant now = clock.instant();
        repository.insert(id, actor.organizationId(), customerId, leadId, opportunityId, owner, actor.userId(), title, description, priority, dueAt, now);
        audit.record(actor, "TASK_CREATED", "CRM_TASK", id, null, Map.of("status", "OPEN", "priority", priority), now);
        return redact(actor, find(actor, id, true));
    }
    private void requireOpenLead(UUID organizationId, UUID leadId) {
        if (leadId == null) return;
        String status = jdbc.queryForObject("SELECT status FROM leads WHERE organization_id=? AND id=? FOR UPDATE", String.class, organizationId, leadId);
        if (Set.of("CONVERTED", "DISQUALIFIED").contains(status)) throw ApiRequestException.conflict("LEAD_CLOSED");
    }

    @Transactional
    public TaskResponse patch(ActorContext incoming, UUID id, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        TaskResponse before = find(actor, id, true); requireMutable(before);
        JsonInput in = JsonInput.of(body, "title", "description", "priority", "dueAt", "expectedUpdatedAt");
        if (!CrmInput.instant(in, "expectedUpdatedAt").equals(before.updatedAt())) throw ApiRequestException.conflict("TASK_CHANGED");
        if (!in.has("title") && !in.has("description") && !in.has("priority") && !in.has("dueAt")) throw JsonInput.invalid("body", "Cần ít nhất một trường cần cập nhật.");
        String title = in.has("title") ? CrmInput.text(in, "title", 220, true, false) : before.title();
        String description = in.has("description") ? CrmInput.text(in, "description", TEXT_LIMIT, false, true) : before.description();
        String priority = in.has("priority") ? priority(CrmInput.text(in, "priority", 20, true, false)) : before.priority();
        Instant dueAt = in.has("dueAt") ? CrmInput.instant(in, "dueAt") : before.dueAt();
        List<String> changed = changedFields(before, title, description, priority, dueAt);
        if (changed.isEmpty()) return redact(actor, before);
        Instant now = next(before.updatedAt());
        repository.update(actor.organizationId(), id, title, description, priority, dueAt, now);
        audit.record(actor, "TASK_UPDATED", "CRM_TASK", id, safeTask(before), Map.of("changedFields", changed), now);
        return redact(actor, find(actor, id, true));
    }

    @Transactional
    public TaskResponse assign(ActorContext incoming, UUID id, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        boolean manager = workAccess.hasRolePermission(actor, RoleCode.MANAGER, PermissionCode.TASK_MANAGE);
        boolean admin = workAccess.hasRolePermission(actor, RoleCode.ADMIN, PermissionCode.TASK_MANAGE);
        if (!manager && !admin) FoundationAccess.denyPermission();
        TaskResponse before = find(actor, id, true); requireMutable(before);
        JsonInput in = JsonInput.of(body, "ownerUserId", "expectedUpdatedAt");
        if (!CrmInput.instant(in, "expectedUpdatedAt").equals(before.updatedAt())) throw ApiRequestException.conflict("TASK_CHANGED");
        UUID ownerId = CrmInput.uuid(in, "ownerUserId");
        if (!eligibleOwner(actor.organizationId(), ownerId, before.customerId(), before.leadId())
                || (manager && !admin && !activeManagedTeamMember(actor.organizationId(), actor.userId(), ownerId)))
            throw ApiRequestException.invalidOwner();
        if (ownerId.equals(before.ownerUserId())) return redact(actor, before);
        Instant now = next(before.updatedAt()); repository.assign(actor.organizationId(), id, ownerId, now);
        audit.record(actor, "TASK_ASSIGNED", "CRM_TASK", id, Map.of("ownerUserId", before.ownerUserId()), Map.of("ownerUserId", ownerId), now);
        return redact(actor, find(actor, id, true));
    }

    @Transactional
    public TaskResponse transition(ActorContext incoming, UUID id, JsonNode body, String command) {
        ActorContext actor = access.lockAndRefresh(incoming);
        TaskResponse before = find(actor, id, true);
        JsonInput in = JsonInput.of(body, command.equals("complete") ? new String[]{"expectedUpdatedAt", "result"}
                : command.equals("cancel") ? new String[]{"expectedUpdatedAt", "reason"} : new String[]{"expectedUpdatedAt"});
        if (!CrmInput.instant(in, "expectedUpdatedAt").equals(before.updatedAt())) throw ApiRequestException.conflict("TASK_CHANGED");
        String status, result = before.result(), reason = before.cancellationReason(); Instant completedAt = before.completedAt();
        switch (command) {
            case "start" -> {
                requireMutable(before);
                if (!before.status().equals("OPEN")) throw ApiRequestException.conflict("TASK_STATE_CONFLICT");
                status = "IN_PROGRESS"; result = null; reason = null; completedAt = null;
            }
            case "complete" -> {
                requireMutable(before);
                if (!Set.of("OPEN", "IN_PROGRESS").contains(before.status())) throw ApiRequestException.conflict("TASK_STATE_CONFLICT");
                status = "DONE"; result = CrmInput.text(in, "result", TEXT_LIMIT, true, false); reason = null; completedAt = clock.instant();
            }
            case "cancel" -> {
                requireMutable(before);
                if (!Set.of("OPEN", "IN_PROGRESS").contains(before.status())) throw ApiRequestException.conflict("TASK_STATE_CONFLICT");
                status = "CANCELLED"; result = null; reason = CrmInput.text(in, "reason", TEXT_LIMIT, true, false); completedAt = null;
            }
            default -> throw new IllegalArgumentException("Unknown task command");
        }
        Instant now = next(before.updatedAt()); repository.transition(actor.organizationId(), id, status, result, reason, completedAt, now);
        String action = switch (command) { case "start" -> "TASK_STARTED"; case "complete" -> "TASK_COMPLETED"; default -> "TASK_CANCELLED"; };
        audit.record(actor, action, "CRM_TASK", id, Map.of("status", before.status()), Map.of("status", status), now);
        return redact(actor, find(actor, id, true));
    }

    private TaskResponse find(ActorContext actor, UUID id, boolean write) {
        CrmAccess.Scope scope = recordScope(actor, write);
        String where = "t.organization_id=? AND " + scope.where();
        List<Object> args = new ArrayList<>(); args.add(actor.organizationId()); args.addAll(scope.args());
        return repository.find(where, args, id).orElseGet(() -> { FoundationAccess.denyScope(); return null; });
    }

    private CrmAccess.Scope recordScope(ActorContext actor, boolean write) {
        CrmAccess.Scope base = workAccess.subjectScope(actor, PermissionCode.TASK_MANAGE, write, "t", "t.customer_id", "t.lead_id");
        boolean teamManager = workAccess.hasRolePermission(actor, RoleCode.MANAGER, PermissionCode.TASK_MANAGE);
        boolean admin = workAccess.hasRolePermission(actor, RoleCode.ADMIN, PermissionCode.TASK_MANAGE);
        boolean dataReader = !write && actor.hasRole(RoleCode.DATA_STAFF);
        if (teamManager || admin || dataReader) return base;
        List<Object> args = new ArrayList<>(base.args()); args.add(actor.userId());
        return new CrmAccess.Scope("(" + base.where() + ") AND t.owner_user_id=?", List.copyOf(args));
    }

    private void requireSubject(ActorContext actor, boolean write, UUID customerId, UUID leadId) {
        CrmAccess.Scope scope = workAccess.subjectScope(actor, PermissionCode.TASK_MANAGE, write, "s", "s.customer_id", "s.lead_id");
        String table = customerId != null ? "customers" : "leads"; UUID subjectId = customerId != null ? customerId : leadId;
        if (jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE organization_id=? AND id=? AND archived_at IS NULL", Integer.class, actor.organizationId(), subjectId) == 0) FoundationAccess.denyScope();
        String sql = "SELECT count(*) FROM (SELECT CAST(? AS UUID) AS organization_id,"
                + "CAST(? AS UUID) AS customer_id,CAST(? AS UUID) AS lead_id) s WHERE " + scope.where();
        List<Object> args = new ArrayList<>(); args.add(actor.organizationId()); args.add(customerId); args.add(leadId); args.addAll(scope.args());
        if (jdbc.queryForObject(sql, Integer.class, args.toArray()) == 0) FoundationAccess.denyScope();
    }

    private UUID resolveCreateOwner(ActorContext actor, boolean manager, UUID requestedOwner, UUID customerId, UUID leadId) {
        boolean sales = workAccess.hasRolePermission(actor, RoleCode.SALES_REP, PermissionCode.TASK_MANAGE);
        boolean care = workAccess.hasRolePermission(actor, RoleCode.CUSTOMER_CARE, PermissionCode.TASK_MANAGE);
        if (manager) {
            if (requestedOwner == null) throw JsonInput.invalid("ownerUserId", "Vui lòng điền đầy đủ các trường bắt buộc.");
            UUID owner = requestedOwner;
            if (!eligibleOwner(actor.organizationId(), owner, customerId, leadId)
                    || !activeManagedTeamMember(actor.organizationId(), actor.userId(), owner)) throw ApiRequestException.invalidOwner();
            return owner;
        }
        if (!sales && !care) FoundationAccess.denyPermission();
        if (requestedOwner != null && !requestedOwner.equals(actor.userId())) FoundationAccess.denyScope();
        if (!eligibleOwner(actor.organizationId(), actor.userId(), customerId, leadId)) FoundationAccess.denyScope();
        return actor.userId();
    }

    private boolean eligibleOwner(UUID organizationId, UUID ownerId, UUID customerId, UUID leadId) {
        Integer active = jdbc.queryForObject("""
                SELECT count(*) FROM users u WHERE u.organization_id=? AND u.id=? AND u.status='ACTIVE' AND u.archived_at IS NULL
                  AND EXISTS (SELECT 1 FROM user_role_grants g JOIN roles r ON r.id=g.role_id AND r.organization_id=g.organization_id
                    WHERE g.organization_id=u.organization_id AND g.user_id=u.id AND r.status='ACTIVE' AND r.code IN ('SALES_REP','CUSTOMER_CARE')
                      AND g.granted_at<=? AND (g.revoked_at IS NULL OR g.revoked_at>?))
                """, Integer.class, organizationId, ownerId, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        if (active == 0) return false;
        boolean customer = customerId != null;
        CrmAccess.Scope saleScope = crm.subjectRoleScope(ownerId, RoleCode.SALES_REP, !customer);
        String table = customer ? "customers c" : "leads l", alias = customer ? "c" : "l";
        String idColumn = customer ? "c.id" : "l.id";
        List<Object> args = new ArrayList<>(); args.add(organizationId); args.add(customer ? customerId : leadId); args.addAll(saleScope.args());
        Integer salesEligible = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + alias + ".organization_id=? AND " + idColumn + "=? AND " + alias + ".archived_at IS NULL AND (" + saleScope.where() + ")", Integer.class, args.toArray());
        if (salesEligible > 0 && hasActiveRole(organizationId, ownerId, RoleCode.SALES_REP)) return true;
        return customer && workAccess.activeCareAssignment(organizationId, customerId, ownerId)
                && hasActiveRole(organizationId, ownerId, RoleCode.CUSTOMER_CARE);
    }

    private boolean hasActiveRole(UUID organizationId, UUID userId, RoleCode role) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.queryForObject("""
                SELECT count(*) FROM user_role_grants g JOIN roles r ON r.id=g.role_id AND r.organization_id=g.organization_id
                WHERE g.organization_id=? AND g.user_id=? AND r.code=? AND r.status='ACTIVE' AND g.granted_at<=?
                  AND (g.revoked_at IS NULL OR g.revoked_at>?)
                """, Integer.class, organizationId, userId, role.name(), now, now) > 0;
    }

    private boolean activeManagedTeamMember(UUID organizationId, UUID managerId, UUID userId) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.queryForObject("""
                SELECT count(*) FROM team_memberships tm
                JOIN teams t ON t.id=tm.team_id AND t.organization_id=tm.organization_id
                WHERE tm.organization_id=? AND tm.user_id=? AND t.manager_user_id=?
                  AND tm.status='ACTIVE' AND tm.joined_at<=? AND (tm.left_at IS NULL OR tm.left_at>?)
                  AND t.status='ACTIVE'
                """, Integer.class, organizationId, userId, managerId, now, now) > 0;
    }

    private void validateOpportunity(UUID organizationId, UUID customerId, UUID leadId, UUID opportunityId) {
        if (opportunityId == null) return;
        String column = customerId != null ? "customer_id" : "lead_id"; UUID subjectId = customerId != null ? customerId : leadId;
        if (jdbc.queryForObject("SELECT count(*) FROM opportunities WHERE organization_id=? AND id=? AND " + column + "=?", Integer.class, organizationId, opportunityId, subjectId) == 0)
            throw JsonInput.invalid("opportunityId", "Cơ hội phải thuộc cùng khách hàng hoặc Lead.");
    }

    private void requireCreator(ActorContext actor) {
        if (!actor.hasPermission(PermissionCode.TASK_MANAGE)) FoundationAccess.denyPermission();
        if (!workAccess.hasRolePermission(actor, RoleCode.MANAGER, PermissionCode.TASK_MANAGE)
                && !workAccess.hasRolePermission(actor, RoleCode.SALES_REP, PermissionCode.TASK_MANAGE)
                && !workAccess.hasRolePermission(actor, RoleCode.CUSTOMER_CARE, PermissionCode.TASK_MANAGE)) FoundationAccess.denyPermission();
    }
    private static void requireMutable(TaskResponse task) { if (Set.of("DONE", "CANCELLED").contains(task.status())) throw ApiRequestException.conflict("TASK_STATE_CONFLICT"); }
    private static UUID optionalUuid(JsonInput in, String field) { return in.missingOrBlank(field) ? null : in.uuid(field); }
    private static void requireSubjectXor(UUID customer, UUID lead) { if ((customer == null) == (lead == null)) throw JsonInput.invalid("customerId", "Chọn đúng một Customer hoặc Lead."); }
    private static String priority(String value) { String v = value.toUpperCase(Locale.ROOT); if (!PRIORITIES.contains(v)) throw JsonInput.invalid("priority", "Mức ưu tiên không hợp lệ."); return v; }
    private static String enumFilter(String field, String value, Set<String> allowed) { String v = value.strip().toUpperCase(Locale.ROOT); if (!allowed.contains(v)) throw JsonInput.invalid(field, "Giá trị lọc không hợp lệ."); return v; }
    private static void add(StringBuilder where, List<Object> args, String predicate, Object value) { where.append(" AND ").append(predicate); args.add(value); }
    private TaskResponse redact(ActorContext actor, TaskResponse value) {
        boolean canReadTaskContent = workAccess.hasRolePermission(actor, RoleCode.ADMIN, PermissionCode.TASK_MANAGE)
                || workAccess.hasRolePermission(actor, RoleCode.MANAGER, PermissionCode.TASK_MANAGE)
                || workAccess.hasRolePermission(actor, RoleCode.SALES_REP, PermissionCode.TASK_MANAGE)
                || workAccess.hasRolePermission(actor, RoleCode.CUSTOMER_CARE, PermissionCode.TASK_MANAGE);
        if (actor.hasRole(RoleCode.DATA_STAFF) && !canReadTaskContent) value = value.redacted();
        return actor.hasPermission(PermissionCode.OPPORTUNITY_READ) ? value : value.withoutOpportunityTitle();
    }
    private static List<String> changedFields(TaskResponse before, String title, String description, String priority, Instant dueAt) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(before.title(), title)) changed.add("title");
        if (!Objects.equals(before.description(), description)) changed.add("description");
        if (!Objects.equals(before.priority(), priority)) changed.add("priority");
        if (!Objects.equals(before.dueAt(), dueAt)) changed.add("dueAt");
        return changed;
    }
    private static Map<String, Object> safeTask(TaskResponse value) { return Map.of("status", value.status(), "ownerUserId", value.ownerUserId(), "priority", value.priority()); }
    private Instant next(Instant previous) { Instant candidate = clock.instant(); return candidate.isAfter(previous) ? candidate : previous.plusMillis(1); }
}
