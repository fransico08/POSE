package com.pose.backend.interaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.crm.*;
import com.pose.backend.identity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class InteractionService {
    private static final Set<String> VISIBILITIES = Set.of("CARE_VISIBLE", "SALES_ONLY");
    private static final int TEXT_LIMIT = 4000;
    private final InteractionRepository repository;
    private final CrmWorkAccess workAccess;
    private final FoundationAccess access;
    private final FoundationAuditRepository audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public InteractionService(InteractionRepository repository, CrmWorkAccess workAccess, FoundationAccess access,
                              FoundationAuditRepository audit, JdbcTemplate jdbc, Clock clock) {
        this.repository = repository; this.workAccess = workAccess; this.access = access;
        this.audit = audit; this.jdbc = jdbc; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<InteractionResponse> list(ActorContext actor, int page, int size, UUID customerId,
            UUID leadId, UUID opportunityId, String type, String channel, String visibility,
            Instant occurredFrom, Instant occurredTo) {
        PageResponse.validate(page, size);
        if (customerId != null && leadId != null) throw JsonInput.invalid("customerId", "Chỉ lọc theo Customer hoặc Lead.");
        if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) throw JsonInput.invalid("occurredFrom", "Thời điểm bắt đầu phải trước thời điểm kết thúc.");
        CrmAccess.Scope scope = workAccess.subjectScope(actor, PermissionCode.INTERACTION_READ, false, "i", "i.customer_id", "i.lead_id");
        StringBuilder where = new StringBuilder("i.organization_id=? AND ").append(scope.where());
        List<Object> args = new ArrayList<>(); args.add(actor.organizationId()); args.addAll(scope.args());
        applyVisibilityScope(actor, where);
        if (customerId != null) add(where, args, "i.customer_id=?", customerId);
        if (leadId != null) add(where, args, "i.lead_id=?", leadId);
        if (opportunityId != null) add(where, args, "i.opportunity_id=?", opportunityId);
        if (type != null) add(where, args, "i.type=?", codeFilter("type", type, 50));
        if (channel != null) add(where, args, "i.channel=?", codeFilter("channel", channel, 50));
        if (visibility != null) add(where, args, "i.visibility=?", visibility(visibility));
        if (occurredFrom != null) add(where, args, "i.occurred_at>=?", Timestamp.from(occurredFrom));
        if (occurredTo != null) add(where, args, "i.occurred_at<=?", Timestamp.from(occurredTo));
        List<InteractionResponse> items = repository.list(where.toString(), args, page, size).stream().map(i -> redact(actor, i)).toList();
        return new PageResponse<>(items, page, size, repository.count(where.toString(), args));
    }

    @Transactional(readOnly = true)
    public InteractionResponse get(ActorContext actor, UUID id) {
        return redact(actor, find(actor, id, false));
    }

    @Transactional
    public InteractionResponse create(ActorContext incoming, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        requireWriter(actor, true);
        JsonInput in = JsonInput.of(body, "customerId", "leadId", "opportunityId", "type", "channel",
                "subject", "content", "outcome", "visibility", "occurredAt");
        UUID customerId = optionalUuid(in, "customerId"), leadId = optionalUuid(in, "leadId");
        requireSubjectXor(customerId, leadId);
        requireSubject(actor, PermissionCode.INTERACTION_WRITE, true, customerId, leadId);
        requireOpenLead(actor.organizationId(), leadId);
        UUID opportunityId = optionalUuid(in, "opportunityId");
        validateOpportunity(actor.organizationId(), customerId, leadId, opportunityId);
        String type = code(CrmInput.text(in, "type", 50, true, false), "type", 50);
        String channel = code(CrmInput.text(in, "channel", 50, true, false), "channel", 50);
        String subject = CrmInput.text(in, "subject", 220, true, false);
        String content = CrmInput.text(in, "content", TEXT_LIMIT, true, false);
        String outcome = CrmInput.text(in, "outcome", TEXT_LIMIT, false, true);
        String visibility = visibility(CrmInput.text(in, "visibility", 20, true, false));
        requireVisibilityWrite(actor, visibility);
        Instant occurredAt = CrmInput.instant(in, "occurredAt");
        UUID id = UUID.randomUUID(); Instant now = clock.instant();
        repository.insert(id, actor.organizationId(), customerId, leadId, opportunityId, actor.userId(), type,
                channel, subject, content, outcome, visibility, occurredAt, now);
        audit.record(actor, "INTERACTION_CREATED", "INTERACTION", id, null,
                Map.of("type", type, "channel", channel, "visibility", visibility), now);
        return redact(actor, find(actor, id, true));
    }
    private void requireOpenLead(UUID organizationId, UUID leadId) {
        if (leadId == null) return;
        String status = jdbc.queryForObject("SELECT status FROM leads WHERE organization_id=? AND id=? FOR UPDATE", String.class, organizationId, leadId);
        if (Set.of("CONVERTED", "DISQUALIFIED").contains(status)) throw ApiRequestException.conflict("LEAD_CLOSED");
    }

    @Transactional
    public InteractionResponse patch(ActorContext incoming, UUID id, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        requireWriter(actor, false);
        InteractionResponse before = find(actor, id, true);
        JsonInput in = JsonInput.of(body, "type", "channel", "subject", "content", "outcome", "visibility", "occurredAt", "expectedUpdatedAt");
        in.requireAny();
        if (!CrmInput.instant(in, "expectedUpdatedAt").equals(before.updatedAt())) throw ApiRequestException.conflict("INTERACTION_CHANGED");
        String type = in.has("type") ? code(CrmInput.text(in, "type", 50, true, false), "type", 50) : before.type();
        String channel = in.has("channel") ? code(CrmInput.text(in, "channel", 50, true, false), "channel", 50) : before.channel();
        String subject = in.has("subject") ? CrmInput.text(in, "subject", 220, true, false) : before.subject();
        String content = in.has("content") ? CrmInput.text(in, "content", TEXT_LIMIT, true, false) : before.content();
        String outcome = in.has("outcome") ? CrmInput.text(in, "outcome", TEXT_LIMIT, false, true) : before.outcome();
        String visibility = in.has("visibility") ? visibility(CrmInput.text(in, "visibility", 20, true, false)) : before.visibility();
        requireVisibilityWrite(actor, visibility);
        Instant occurredAt = in.has("occurredAt") ? CrmInput.instant(in, "occurredAt") : before.occurredAt();
        List<String> changed = changedFields(before, type, channel, subject, content, outcome, visibility, occurredAt);
        if (changed.isEmpty()) return redact(actor, before);
        Instant now = next(before.updatedAt());
        repository.update(actor.organizationId(), id, type, channel, subject, content, outcome, visibility, occurredAt, now);
        audit.record(actor, "INTERACTION_UPDATED", "INTERACTION", id, safeSnapshot(before),
                safeSnapshot(type, channel, visibility, occurredAt, changed), now);
        return redact(actor, find(actor, id, true));
    }

    private InteractionResponse find(ActorContext actor, UUID id, boolean write) {
        CrmAccess.Scope scope = workAccess.subjectScope(actor, write ? PermissionCode.INTERACTION_WRITE : PermissionCode.INTERACTION_READ,
                write, "i", "i.customer_id", "i.lead_id");
        String where = "i.organization_id=? AND " + scope.where();
        List<Object> args = new ArrayList<>(); args.add(actor.organizationId()); args.addAll(scope.args());
        StringBuilder scopedWhere = new StringBuilder(where);
        applyVisibilityScope(actor, scopedWhere);
        return repository.find(scopedWhere.toString(), args, id).orElseGet(() -> { FoundationAccess.denyScope(); return null; });
    }

    private void requireSubject(ActorContext actor, PermissionCode permission, boolean write, UUID customerId, UUID leadId) {
        CrmAccess.Scope scope = workAccess.subjectScope(actor, permission, write, "s", "s.customer_id", "s.lead_id");
        String table = customerId != null ? "customers" : "leads";
        UUID subjectId = customerId != null ? customerId : leadId;
        int exists = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE organization_id=? AND id=? AND archived_at IS NULL",
                Integer.class, actor.organizationId(), subjectId);
        if (exists == 0) FoundationAccess.denyScope();
        String sql = "SELECT count(*) FROM (SELECT CAST(? AS UUID) AS organization_id,"
                + "CAST(? AS UUID) AS customer_id,CAST(? AS UUID) AS lead_id) s WHERE " + scope.where();
        List<Object> args = new ArrayList<>();
        args.add(actor.organizationId()); args.add(customerId); args.add(leadId); args.addAll(scope.args());
        if (jdbc.queryForObject(sql, Integer.class, args.toArray()) == 0) FoundationAccess.denyScope();
    }

    private void validateOpportunity(UUID organizationId, UUID customerId, UUID leadId, UUID opportunityId) {
        if (opportunityId == null) return;
        String column = customerId != null ? "customer_id" : "lead_id";
        UUID subjectId = customerId != null ? customerId : leadId;
        Integer count = jdbc.queryForObject("SELECT count(*) FROM opportunities WHERE organization_id=? AND id=? AND " + column + "=?",
                Integer.class, organizationId, opportunityId, subjectId);
        if (count == 0) throw JsonInput.invalid("opportunityId", "Cơ hội phải thuộc cùng khách hàng hoặc Lead.");
    }

    private static UUID optionalUuid(JsonInput in, String field) { return in.missingOrBlank(field) ? null : in.uuid(field); }
    private static void requireSubjectXor(UUID customer, UUID lead) {
        if ((customer == null) == (lead == null)) throw JsonInput.invalid("customerId", "Chọn đúng một Customer hoặc Lead.");
    }
    private void requireWriter(ActorContext actor, boolean create) {
        if (!actor.hasPermission(PermissionCode.INTERACTION_WRITE)) FoundationAccess.denyPermission();
        boolean allowed = workAccess.hasRolePermission(actor, RoleCode.MANAGER, PermissionCode.INTERACTION_WRITE)
                || workAccess.hasRolePermission(actor, RoleCode.SALES_REP, PermissionCode.INTERACTION_WRITE)
                || workAccess.hasRolePermission(actor, RoleCode.CUSTOMER_CARE, PermissionCode.INTERACTION_WRITE)
                || (!create && workAccess.hasRolePermission(actor, RoleCode.ADMIN, PermissionCode.INTERACTION_WRITE));
        if (!allowed) FoundationAccess.denyPermission();
    }

    private void requireVisibilityWrite(ActorContext actor, String visibility) {
        if (visibility.equals("SALES_ONLY") && !hasSensitiveRead(actor)) FoundationAccess.denyPermission();
    }

    private void applyVisibilityScope(ActorContext actor, StringBuilder where) {
        if (!hasSensitiveRead(actor) && !dataStaffOnly(actor)) where.append(" AND i.visibility='CARE_VISIBLE'");
    }

    private boolean dataStaffOnly(ActorContext actor) {
        if (!actor.hasRole(RoleCode.DATA_STAFF)) return false;
        return !workAccess.hasRolePermission(actor, RoleCode.ADMIN, PermissionCode.INTERACTION_READ)
                && !workAccess.hasRolePermission(actor, RoleCode.MANAGER, PermissionCode.INTERACTION_READ)
                && !workAccess.hasRolePermission(actor, RoleCode.SALES_REP, PermissionCode.INTERACTION_READ)
                && !workAccess.hasRolePermission(actor, RoleCode.CUSTOMER_CARE, PermissionCode.INTERACTION_READ);
    }

    private boolean hasSensitiveRead(ActorContext actor) {
        return workAccess.hasRolePermission(actor, RoleCode.ADMIN, PermissionCode.INTERACTION_SENSITIVE_READ)
                || workAccess.hasRolePermission(actor, RoleCode.MANAGER, PermissionCode.INTERACTION_SENSITIVE_READ)
                || workAccess.hasRolePermission(actor, RoleCode.SALES_REP, PermissionCode.INTERACTION_SENSITIVE_READ);
    }

    private InteractionResponse redact(ActorContext actor, InteractionResponse response) {
        if (dataStaffOnly(actor) || (response.visibility().equals("SALES_ONLY") && !hasSensitiveRead(actor))) response = response.redacted();
        if (!actor.hasPermission(PermissionCode.OPPORTUNITY_READ)) response = response.withoutOpportunityTitle();
        return response;
    }
    private static String visibility(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!VISIBILITIES.contains(normalized)) throw JsonInput.invalid("visibility", "Phân quyền hiển thị không hợp lệ.");
        return normalized;
    }
    private static String code(String value, String field, int max) {
        String normalized = value.toUpperCase(Locale.ROOT);
        if (normalized.length() > max || !normalized.matches("[A-Z][A-Z0-9_]*")) throw JsonInput.invalid(field, "Dùng mã gồm chữ in hoa, số hoặc dấu gạch dưới.");
        return normalized;
    }
    private static String codeFilter(String field, String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.strip().matches("[A-Za-z][A-Za-z0-9_]*")) throw JsonInput.invalid(field, "Giá trị lọc không hợp lệ.");
        return value.strip().toUpperCase(Locale.ROOT);
    }
    private static void add(StringBuilder where, List<Object> args, String sql, Object value) { where.append(" AND ").append(sql); args.add(value); }
    private static List<String> changedFields(InteractionResponse before, String type, String channel, String subject,
            String content, String outcome, String visibility, Instant occurredAt) {
        List<String> result = new ArrayList<>();
        if (!Objects.equals(before.type(), type)) result.add("type");
        if (!Objects.equals(before.channel(), channel)) result.add("channel");
        if (!Objects.equals(before.subject(), subject)) result.add("subject");
        if (!Objects.equals(before.content(), content)) result.add("content");
        if (!Objects.equals(before.outcome(), outcome)) result.add("outcome");
        if (!Objects.equals(before.visibility(), visibility)) result.add("visibility");
        if (!Objects.equals(before.occurredAt(), occurredAt)) result.add("occurredAt");
        return result;
    }
    private static Map<String, Object> safeSnapshot(InteractionResponse value) {
        return safeSnapshot(value.type(), value.channel(), value.visibility(), value.occurredAt(), List.of());
    }
    private static Map<String, Object> safeSnapshot(String type, String channel, String visibility, Instant occurredAt, List<String> changedFields) {
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("type", type); safe.put("channel", channel); safe.put("visibility", visibility);
        safe.put("occurredAt", occurredAt.toString());
        if (!changedFields.isEmpty()) safe.put("changedFields", List.copyOf(changedFields));
        return safe;
    }
    private Instant next(Instant previous) { Instant candidate = clock.instant(); return candidate.isAfter(previous) ? candidate : previous.plusMillis(1); }
}
