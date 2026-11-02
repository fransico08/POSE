package com.pose.backend.salesassignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.ApiRequestException;
import com.pose.backend.api.JsonInput;
import com.pose.backend.api.PageResponse;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.auth.AuthAccountRepository;
import com.pose.backend.auth.AuthenticatedAccount;
import com.pose.backend.identity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class SalesAssignmentService {
    private static final int OPEN_WORK_LIMIT = 50;
    private static final Set<String> ASSIGNMENT_STATUSES = Set.of("ACTIVE", "EXPIRED", "REVOKED");
    private static final Set<String> EFFECTIVE_STATES = Set.of("CURRENT", "UPCOMING", "EXPIRED", "REVOKED");

    private final SalesAssignmentRepository assignments;
    private final AuthAccountRepository accounts;
    private final FoundationAccess access;
    private final AuthorizationPolicy policy;
    private final FoundationAuditRepository audit;
    private final Clock clock;

    public SalesAssignmentService(SalesAssignmentRepository assignments, AuthAccountRepository accounts,
                                  FoundationAccess access, AuthorizationPolicy policy,
                                  FoundationAuditRepository audit, Clock clock) {
        this.assignments = assignments;
        this.accounts = accounts;
        this.access = access;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesAssignmentResponse> list(ActorContext actor, int page, int size, String search,
                                                       UUID teamId, UUID offeringId, String salesSearch,
                                                       String status, String validity) {
        PageResponse.validate(page, size);
        requireManager(actor);
        Instant now = clock.instant();
        QueryScope scope = listScope(actor, teamId, offeringId, search, salesSearch, status, validity, now);
        long total = assignments.count(scope.where(), scope.args());
        List<SalesAssignmentResponse> rows = assignments.list(scope.where(), scope.args(), page, size).stream()
                .map(row -> response(row, now)).toList();
        return new PageResponse<>(rows, page, size, total);
    }

    @Transactional(readOnly = true)
    public SalesAssignmentResponse get(ActorContext actor, UUID id) {
        requireManager(actor);
        return response(findInScope(actor, id), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<SalesAssignmentCandidateResponse> candidates(ActorContext actor, UUID offeringId) {
        requireManager(actor);
        Instant now = clock.instant();
        SalesAssignmentRepository.OfferingScope offering = findOfferingInScope(actor, offeringId);
        requireAssignableOffering(offering);
        return assignments.candidates(actor.organizationId(), offering.teamId(), now);
    }

    @Transactional(readOnly = true)
    public SalesAssignmentOpenWorkResponse openWork(ActorContext actor, UUID id) {
        requireManager(actor);
        var assignment = findInScope(actor, id);
        return new SalesAssignmentOpenWorkResponse(assignments.countOpenWork(assignment),
                assignments.openWork(assignment));
    }

    @Transactional
    public SalesAssignmentResponse create(ActorContext incoming, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        requireManager(actor);
        JsonInput input = JsonInput.of(body, "offeringId", "salesUserId", "effectiveFrom", "effectiveTo");
        UUID offeringId = input.uuid("offeringId");
        UUID salesUserId = input.uuid("salesUserId");
        Instant effectiveFrom = instant(input.text("effectiveFrom", 80, true, false), "effectiveFrom");
        Instant effectiveTo = input.text("effectiveTo", 80, false, true) == null
                ? null : instant(input.text("effectiveTo", 80, false, true), "effectiveTo");
        validateDates(effectiveFrom, effectiveTo);

        Instant now = clock.instant();
        SalesAssignmentRepository.OfferingScope offering = findOfferingInScope(actor, offeringId);
        requireAssignableOffering(offering);
        requireEligibleSales(actor.organizationId(), offering.teamId(), salesUserId, now);

        if (assignments.hasOverlappingAssignment(actor.organizationId(), offeringId, salesUserId,
                effectiveFrom, effectiveTo, null)) throw ApiRequestException.conflict("SALES_ASSIGNMENT_OVERLAP");

        UUID id = UUID.randomUUID();
        assignments.insert(id, actor.organizationId(), offeringId, salesUserId, actor.userId(),
                effectiveFrom, effectiveTo, now);
        SalesAssignmentResponse result = response(findInScope(actor, id), now);
        audit.record(actor, "SALES_ASSIGNMENT_CREATED", "SALES_ASSIGNMENT", id, null, result, now);
        return result;
    }

    @Transactional
    public SalesAssignmentResponse extend(ActorContext incoming, UUID id, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        requireManager(actor);
        JsonInput input = JsonInput.of(body, "effectiveTo", "expectedUpdatedAt");
        if (!input.has("effectiveTo")) throw JsonInput.invalid("effectiveTo", "Thiếu ngày kết thúc mới.");
        Instant effectiveTo = input.text("effectiveTo", 80, false, true) == null
                ? null : instant(input.text("effectiveTo", 80, false, true), "effectiveTo");
        Instant expectedUpdatedAt = instant(input.text("expectedUpdatedAt", 80, true, false), "expectedUpdatedAt");
        Instant now = clock.instant();
        var before = findInScope(actor, id);
        if (!before.updatedAt().equals(expectedUpdatedAt)) throw ApiRequestException.conflict("SALES_ASSIGNMENT_CHANGED");
        if ("REVOKED".equals(before.status()) || before.revokedAt() != null) {
            throw ApiRequestException.conflict("SALES_ASSIGNMENT_REVOKED");
        }
        validateDates(before.effectiveFrom(), effectiveTo);
        if (effectiveTo != null && effectiveTo.isBefore(now)) {
            throw JsonInput.invalid("effectiveTo", "Ngày kết thúc mới phải từ thời điểm hiện tại trở đi.");
        }
        if (before.effectiveTo() == null && effectiveTo != null
                || before.effectiveTo() != null && effectiveTo != null && effectiveTo.isBefore(before.effectiveTo())) {
            throw JsonInput.invalid("effectiveTo", "Chỉ có thể kéo dài thời hạn hoặc bỏ ngày kết thúc.");
        }
        if (Objects.equals(before.effectiveTo(), effectiveTo)) return response(before, now);

        if (assignments.hasOverlappingAssignment(actor.organizationId(), before.offeringId(), before.salesUserId(),
                before.effectiveFrom(), effectiveTo, id)) throw ApiRequestException.conflict("SALES_ASSIGNMENT_OVERLAP");

        assignments.extend(actor.organizationId(), id, effectiveTo, now);
        SalesAssignmentResponse result = response(findInScope(actor, id), now);
        audit.record(actor, "SALES_ASSIGNMENT_EXTENDED", "SALES_ASSIGNMENT", id,
                response(before, now), result, now);
        return result;
    }

    @Transactional
    public SalesAssignmentResponse revoke(ActorContext incoming, UUID id) {
        ActorContext actor = access.lockAndRefresh(incoming);
        requireManager(actor);
        Instant now = clock.instant();
        var before = findInScope(actor, id);
        if ("REVOKED".equals(before.status()) || before.revokedAt() != null) {
            throw ApiRequestException.conflict("SALES_ASSIGNMENT_REVOKED");
        }
        if (assignments.countOpenWork(before) > 0) {
            throw ApiRequestException.conflict("SALES_ASSIGNMENT_OPEN_WORK");
        }
        assignments.revoke(actor.organizationId(), id, now);
        SalesAssignmentResponse result = response(findInScope(actor, id), now);
        audit.record(actor, "SALES_ASSIGNMENT_REVOKED", "SALES_ASSIGNMENT", id,
                response(before, now), result, now);
        return result;
    }

    @Transactional
    public int expireDueAssignments() {
        Instant now = clock.instant();
        List<SalesAssignmentRepository.StoredAssignment> due = assignments.expiredBatch(now, 100);
        int expired = 0;
        for (var assignment : due) {
            if (assignments.markExpired(assignment.organizationId(), assignment.id(), now) == 0) continue;
            Map<String, Object> before = Map.of(
                    "status", assignment.status(), "effectiveFrom", assignment.effectiveFrom(),
                    "effectiveTo", assignment.effectiveTo(), "salesUserId", assignment.salesUserId(),
                    "offeringId", assignment.offeringId());
            Map<String, Object> after = new LinkedHashMap<>(before);
            after.put("status", "EXPIRED");
            after.put("updatedAt", now);
            audit.recordSystem(assignment.organizationId(), "sales-assignment-expiry", "SALES_ASSIGNMENT_EXPIRED",
                    "SALES_ASSIGNMENT", assignment.id(), before, after, now);
            expired++;
        }
        return expired;
    }

    private QueryScope listScope(ActorContext actor, UUID teamId, UUID offeringId, String search,
                                 String salesSearch, String status, String validity, Instant now) {
        StringBuilder where = new StringBuilder("sa.organization_id=?");
        List<Object> args = new ArrayList<>(List.of(actor.organizationId()));
        if (actor.hasRole(RoleCode.MANAGER) && !actor.hasRole(RoleCode.ADMIN)) {
            List<UUID> managedTeams = actor.managedTeamIds().stream().sorted().toList();
            if (managedTeams.isEmpty()) {
                where.append(" AND 1=0");
            } else {
                where.append(" AND o.team_id IN (").append(FoundationAccess.placeholders(managedTeams.size())).append(')');
                args.addAll(managedTeams);
            }
        }
        if (teamId != null) {
            policy.requireTeamScope(actor, actor.organizationId(), teamId);
            where.append(" AND o.team_id=?");
            args.add(teamId);
        }
        if (offeringId != null) {
            findOfferingInScope(actor, offeringId);
            where.append(" AND sa.offering_id=?");
            args.add(offeringId);
        }
        appendSearch(where, args, search, "(lower(o.name) LIKE ? ESCAPE '!' OR lower(o.code) LIKE ? ESCAPE '!'");
        if (salesSearch != null && !salesSearch.isBlank()) {
            String value = searchValue(salesSearch, "salesSearch");
            where.append(" AND (lower(u.full_name) LIKE ? ESCAPE '!' OR lower(u.email) LIKE ? ESCAPE '!')");
            args.add(value);
            args.add(value);
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.strip().toUpperCase(Locale.ROOT);
            if (!ASSIGNMENT_STATUSES.contains(normalized)) throw JsonInput.invalid("status", "Trạng thái phân công không hợp lệ.");
            where.append(" AND sa.status=?");
            args.add(normalized);
        }
        if (validity != null && !validity.isBlank()) appendValidity(where, args, validity, now);
        return new QueryScope(where.toString(), args);
    }

    private void appendSearch(StringBuilder where, List<Object> args, String search, String expression) {
        if (search == null || search.isBlank()) return;
        String value = searchValue(search, "search");
        where.append(" AND ").append(expression).append(" OR lower(u.full_name) LIKE ? ESCAPE '!')");
        args.add(value);
        args.add(value);
        args.add(value);
    }

    private String searchValue(String raw, String field) {
        String trimmed = raw.strip();
        if (trimmed.length() > 200) throw JsonInput.invalid(field, "Từ khóa dài tối đa 200 ký tự.");
        String escaped = trimmed.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private void appendValidity(StringBuilder where, List<Object> args, String raw, Instant now) {
        String validity = raw.strip().toUpperCase(Locale.ROOT);
        if (!EFFECTIVE_STATES.contains(validity)) throw JsonInput.invalid("validity", "Hiệu lực không hợp lệ.");
        var timestamp = java.sql.Timestamp.from(now);
        switch (validity) {
            case "CURRENT" -> {
                where.append(" AND sa.status='ACTIVE' AND sa.effective_from<=? AND (sa.effective_to IS NULL OR sa.effective_to>=?)");
                args.add(timestamp);
                args.add(timestamp);
            }
            case "UPCOMING" -> {
                where.append(" AND sa.status='ACTIVE' AND sa.effective_from>?");
                args.add(timestamp);
            }
            case "EXPIRED" -> {
                where.append(" AND (sa.status='EXPIRED' OR (sa.status='ACTIVE' AND sa.effective_to IS NOT NULL AND sa.effective_to<?))");
                args.add(timestamp);
            }
            case "REVOKED" -> where.append(" AND (sa.status='REVOKED' OR sa.revoked_at IS NOT NULL)");
            default -> throw new IllegalStateException("Unsupported effective state");
        }
    }

    private SalesAssignmentRepository.OfferingScope findOfferingInScope(ActorContext actor, UUID offeringId) {
        SalesAssignmentRepository.OfferingScope offering = assignments.findOffering(actor.organizationId(), offeringId)
                .orElseGet(() -> {
                    FoundationAccess.denyScope();
                    throw new IllegalStateException("Scope denial must stop execution");
                });
        if (offering.teamId() == null) throw ApiRequestException.conflict("OFFERING_TEAM_REQUIRED");
        policy.requireTeamScope(actor, actor.organizationId(), offering.teamId());
        return offering;
    }

    private void requireAssignableOffering(SalesAssignmentRepository.OfferingScope offering) {
        if (!"ACTIVE".equals(offering.status())) throw JsonInput.invalid("offeringId", "Chỉ phân công sản phẩm hoặc dịch vụ đang hoạt động.");
        if (!"ACTIVE".equals(offering.teamStatus())) throw ApiRequestException.conflict("TEAM_INACTIVE");
    }

    private void requireEligibleSales(UUID organizationId, UUID teamId, UUID salesUserId, Instant now) {
        Optional<AuthenticatedAccount> account = accounts.findById(organizationId, salesUserId);
        if (account.isEmpty()) FoundationAccess.denyScope();
        AuthenticatedAccount sales = account.get();
        if (!sales.active() || !sales.roles().contains(RoleCode.SALES_REP)
                || !sales.permissions().contains(PermissionCode.OFFERING_READ)) {
            throw JsonInput.invalid("salesUserId", "Chọn Nhân viên kinh doanh đang hoạt động và có quyền xem Offering.");
        }
        if (!assignments.isActiveTeamMember(organizationId, teamId, salesUserId, now)) FoundationAccess.denyScope();
    }

    private SalesAssignmentRepository.StoredAssignment findInScope(ActorContext actor, UUID id) {
        SalesAssignmentRepository.StoredAssignment assignment = assignments.find(actor.organizationId(), id)
                .orElseGet(() -> {
                    FoundationAccess.denyScope();
                    throw new IllegalStateException("Scope denial must stop execution");
                });
        if (assignment.teamId() == null) FoundationAccess.denyScope();
        policy.requireTeamScope(actor, actor.organizationId(), assignment.teamId());
        return assignment;
    }

    private void requireManager(ActorContext actor) {
        policy.requirePermission(actor, PermissionCode.SALES_ASSIGNMENT_MANAGE);
        if (!actor.hasRole(RoleCode.ADMIN) && !actor.hasRole(RoleCode.MANAGER)) FoundationAccess.denyPermission();
    }

    private void validateDates(Instant effectiveFrom, Instant effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw JsonInput.invalid("effectiveTo", "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.");
        }
    }

    private Instant instant(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw JsonInput.invalid(field, "Thời điểm phải ở định dạng ISO-8601 UTC.");
        }
    }

    private SalesAssignmentResponse response(SalesAssignmentRepository.StoredAssignment row, Instant now) {
        String state;
        if ("REVOKED".equals(row.status()) || row.revokedAt() != null) state = "REVOKED";
        else if ("EXPIRED".equals(row.status()) || row.effectiveTo() != null && row.effectiveTo().isBefore(now)) state = "EXPIRED";
        else if (row.effectiveFrom().isAfter(now)) state = "UPCOMING";
        else state = "CURRENT";
        return new SalesAssignmentResponse(row.id(), row.offeringId(), row.offeringCode(), row.offeringName(),
                row.teamId(), row.teamName(), row.salesUserId(), row.salesUserName(), row.status(), state,
                row.effectiveFrom(), row.effectiveTo(), row.revokedAt(), row.createdAt(), row.updatedAt());
    }

    private record QueryScope(String where, List<Object> args) {
    }
}
