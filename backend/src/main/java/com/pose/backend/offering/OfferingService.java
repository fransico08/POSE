package com.pose.backend.offering;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.ApiRequestException;
import com.pose.backend.api.JsonInput;
import com.pose.backend.api.PageResponse;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.identity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class OfferingService {
    private static final String DEFAULT_CURRENCY = "VND";
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999999999.99");
    private static final Set<String> KINDS = Set.of("PRODUCT", "SERVICE");

    private final OfferingRepository offerings;
    private final FoundationAccess access;
    private final AuthorizationPolicy policy;
    private final FoundationAuditRepository audit;
    private final Clock clock;

    public OfferingService(OfferingRepository offerings, FoundationAccess access,
                           AuthorizationPolicy policy, FoundationAuditRepository audit, Clock clock) {
        this.offerings = offerings;
        this.access = access;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<OfferingResponse> list(ActorContext actor, int page, int size, String search,
                                               String kind, UUID categoryId, Boolean active, UUID teamId) {
        PageResponse.validate(page, size);
        ReadMode mode = requireReadMode(actor);
        Instant now = clock.instant();
        StringBuilder where = new StringBuilder("o.organization_id=?");
        List<Object> args = new ArrayList<>(List.of(actor.organizationId()));

        if (mode == ReadMode.MANAGER) {
            List<UUID> managedTeams = actor.managedTeamIds().stream().sorted().toList();
            if (managedTeams.isEmpty()) {
                where.append(" AND 1=0");
            } else {
                where.append(" AND o.team_id IN (").append(IdentityPlaceholders.of(managedTeams.size())).append(')');
                args.addAll(managedTeams);
            }
        } else if (mode == ReadMode.SALES) {
            where.append(" AND ").append(OfferingRepository.SALES_READ_SCOPE);
            args.add(actor.userId());
            args.add(Timestamp.from(now));
            args.add(Timestamp.from(now));
            args.add(Timestamp.from(now));
            args.add(Timestamp.from(now));
            if (teamId != null) throw JsonInput.invalid("teamId", "Nhân viên kinh doanh không lọc danh mục theo team.");
        }

        if (search != null && !search.isBlank()) {
            String normalizedSearch = search.strip();
            if (normalizedSearch.length() > 200) throw JsonInput.invalid("search", "Từ khóa dài tối đa 200 ký tự.");
            String escaped = normalizedSearch.toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            where.append(" AND (lower(o.name) LIKE ? ESCAPE '!' OR lower(o.code) LIKE ? ESCAPE '!')");
            args.add("%" + escaped + "%");
            args.add("%" + escaped + "%");
        }
        if (kind != null && !kind.isBlank()) {
            String normalizedKind = kind.strip().toUpperCase(Locale.ROOT);
            if (!KINDS.contains(normalizedKind)) throw JsonInput.invalid("kind", "Loại chỉ nhận PRODUCT hoặc SERVICE.");
            where.append(" AND o.kind=?");
            args.add(normalizedKind);
        }
        if (categoryId != null) {
            where.append(" AND o.category_id=?");
            args.add(categoryId);
        }
        if (active != null) {
            where.append(active ? " AND o.status='ACTIVE'" : " AND o.status IN ('INACTIVE','DRAFT')");
        }
        if (teamId != null) {
            if (mode == ReadMode.MANAGER) policy.requireTeamScope(actor, actor.organizationId(), teamId);
            if (mode == ReadMode.ADMIN && offerings.teamStatus(actor.organizationId(), teamId).isEmpty()) {
                FoundationAccess.denyScope();
            }
            where.append(" AND o.team_id=?");
            args.add(teamId);
        }

        String predicate = where.toString();
        long total = offerings.count(predicate, args);
        List<OfferingRepository.StoredOffering> rows = offerings.list(predicate, args, page, size);
        Map<UUID, OfferingRepository.StoredPrice> prices = offerings.currentPrices(actor.organizationId(),
                rows.stream().map(OfferingRepository.StoredOffering::id).toList(), now);
        List<OfferingResponse> items = rows.stream().map(row -> response(row, prices.get(row.id()))).toList();
        return new PageResponse<>(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public OfferingResponse get(ActorContext actor, UUID id) {
        ReadMode mode = requireReadMode(actor);
        Instant now = clock.instant();
        OfferingRepository.StoredOffering row = findInOrganization(actor.organizationId(), id);
        requireRecordReadScope(actor, mode, row, now);
        return response(row, offerings.findCurrentPrice(actor.organizationId(), id, now).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<OfferingCategoryResponse> categories(ActorContext actor) {
        requireManage(actor);
        return offerings.categories(actor.organizationId());
    }

    @Transactional
    public OfferingResponse create(ActorContext incoming, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        requireManage(actor);
        JsonInput input = JsonInput.of(body, "name", "kind", "categoryId", "teamId", "description", "unit", "referencePrice", "active");
        String name = input.text("name", 200, true, false);
        String kind = normalizeKind(input.text("kind", 20, true, false));
        UUID categoryId = input.uuid("categoryId");
        UUID teamId = input.uuid("teamId");
        String description = input.text("description", 4000, false, true);
        String unit = input.text("unit", 80, false, true);
        BigDecimal price = decimal(body, "referencePrice", true);
        boolean active = requiredBoolean(body, "active");
        Instant now = clock.instant();

        requireActiveCategory(actor.organizationId(), categoryId);
        requireWritableTeam(actor, teamId);
        UUID id = UUID.randomUUID();
        if (active && offerings.duplicateActiveName(actor.organizationId(), kind, normalizeName(name), id)) {
            throw ApiRequestException.conflict("OFFERING_NAME_EXISTS");
        }
        String code = "OFF-" + id.toString().toUpperCase(Locale.ROOT);
        offerings.create(actor.organizationId(), id, categoryId, teamId, code, name, description, kind, unit,
                active ? "ACTIVE" : "INACTIVE", now);
        offerings.insertPrice(actor.organizationId(), id, price, DEFAULT_CURRENCY, unit, now, null);

        OfferingResponse result = response(findInOrganization(actor.organizationId(), id),
                offerings.findCurrentPrice(actor.organizationId(), id, now).orElse(null));
        audit.record(actor, "OFFERING_CREATED", "OFFERING", id, null, result, now);
        return result;
    }

    @Transactional
    public OfferingResponse update(ActorContext incoming, UUID id, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        requireManage(actor);
        JsonInput input = JsonInput.of(body, "name", "kind", "categoryId", "teamId", "description", "unit", "referencePrice", "expectedUpdatedAt");
        requireUpdateFields(body);
        Instant now = clock.instant();
        OfferingRepository.StoredOffering beforeRow = findInOrganization(actor.organizationId(), id);
        requireRecordManageScope(actor, beforeRow);
        OfferingResponse before = response(beforeRow, offerings.findCurrentPrice(actor.organizationId(), id, now).orElse(null));

        Instant expectedUpdatedAt = instant(input.text("expectedUpdatedAt", 40, true, false), "expectedUpdatedAt");
        if (!expectedUpdatedAt.equals(before.updatedAt())) throw ApiRequestException.conflict("OFFERING_CHANGED");
        String name = input.text("name", 200, true, false);
        String kind = normalizeKind(input.text("kind", 20, true, false));
        UUID categoryId = input.uuid("categoryId");
        UUID teamId = input.uuid("teamId");
        String description = input.text("description", 4000, false, true);
        String unit = input.text("unit", 80, false, true);
        BigDecimal newPrice = decimal(body, "referencePrice", true);

        requireActiveCategory(actor.organizationId(), categoryId);
        requireWritableTeam(actor, teamId);
        boolean offeringIdentityChanged = !normalizeName(beforeRow.name()).equals(normalizeName(name))
                || !beforeRow.kind().equals(kind);
        if ("ACTIVE".equals(beforeRow.status()) && offeringIdentityChanged
                && offerings.duplicateActiveName(actor.organizationId(), kind, normalizeName(name), id)) {
            throw ApiRequestException.conflict("OFFERING_NAME_EXISTS");
        }

        OfferingRepository.StoredPrice currentPrice = offerings.findCurrentPrice(actor.organizationId(), id, now).orElse(null);
        boolean priceChanged = currentPrice == null || currentPrice.amount().compareTo(newPrice) != 0
                || !Objects.equals(beforeRow.unit(), unit);
        boolean changed = !Objects.equals(beforeRow.name(), name)
                || !Objects.equals(beforeRow.kind(), kind)
                || !Objects.equals(beforeRow.categoryId(), categoryId)
                || !Objects.equals(beforeRow.teamId(), teamId)
                || !Objects.equals(beforeRow.description(), description)
                || !Objects.equals(beforeRow.unit(), unit)
                || priceChanged;
        if (!changed) return before;

        offerings.update(actor.organizationId(), id, categoryId, teamId, name, description, kind, unit, now);
        if (priceChanged) updatePrice(actor.organizationId(), id, newPrice, unit, currentPrice, now);
        OfferingResponse after = response(findInOrganization(actor.organizationId(), id),
                offerings.findCurrentPrice(actor.organizationId(), id, now).orElse(null));
        audit.record(actor, "OFFERING_UPDATED", "OFFERING", id, before, after, now);
        return after;
    }

    @Transactional
    public OfferingResponse activate(ActorContext incoming, UUID id) {
        return setActive(incoming, id, true);
    }

    @Transactional
    public OfferingResponse deactivate(ActorContext incoming, UUID id) {
        return setActive(incoming, id, false);
    }

    private OfferingResponse setActive(ActorContext incoming, UUID id, boolean active) {
        ActorContext actor = access.lockAndRefresh(incoming);
        requireManage(actor);
        Instant now = clock.instant();
        OfferingRepository.StoredOffering beforeRow = findInOrganization(actor.organizationId(), id);
        requireRecordManageScope(actor, beforeRow);
        boolean wasActive = "ACTIVE".equals(beforeRow.status());
        OfferingResponse before = response(beforeRow, offerings.findCurrentPrice(actor.organizationId(), id, now).orElse(null));
        if (wasActive == active) return before;

        if (active) {
            if (beforeRow.teamId() == null) throw ApiRequestException.conflict("OFFERING_TEAM_REQUIRED");
            requireActiveCategory(actor.organizationId(), beforeRow.categoryId());
            requireWritableTeam(actor, beforeRow.teamId());
            if (offerings.duplicateActiveName(actor.organizationId(), beforeRow.kind(), normalizeName(beforeRow.name()), id)) {
                throw ApiRequestException.conflict("OFFERING_NAME_EXISTS");
            }
        }

        offerings.updateStatus(actor.organizationId(), id, active ? "ACTIVE" : "INACTIVE", now);
        OfferingResponse after = response(findInOrganization(actor.organizationId(), id),
                offerings.findCurrentPrice(actor.organizationId(), id, now).orElse(null));
        audit.record(actor, active ? "OFFERING_ACTIVATED" : "OFFERING_DEACTIVATED", "OFFERING", id, before, after, now);
        return after;
    }

    private void updatePrice(UUID organizationId, UUID offeringId, BigDecimal amount, String unit,
                             OfferingRepository.StoredPrice currentPrice, Instant now) {
        List<OfferingRepository.StoredPrice> rows = offerings.lockPriceRows(organizationId, offeringId);
        List<OfferingRepository.StoredPrice> currentRows = rows.stream()
                .filter(row -> !row.effectiveFrom().isAfter(now)
                        && (row.effectiveTo() == null || row.effectiveTo().isAfter(now)))
                .toList();
        if (currentRows.size() > 1) throw ApiRequestException.conflict("OFFERING_PRICE_TIMELINE_CONFLICT");
        OfferingRepository.StoredPrice current = currentRows.isEmpty() ? null : currentRows.getFirst();
        if (currentPrice != null && (current == null || !Objects.equals(currentPrice.id(), current.id()))) {
            throw ApiRequestException.conflict("OFFERING_PRICE_TIMELINE_CONFLICT");
        }
        if (current != null) offerings.closePrice(organizationId, offeringId, current.id(), now);

        Instant nextStart = rows.stream().map(OfferingRepository.StoredPrice::effectiveFrom)
                .filter(start -> start.isAfter(now)).min(Comparator.naturalOrder()).orElse(null);
        String currency = current == null ? DEFAULT_CURRENCY : current.currencyCode();
        offerings.insertPrice(organizationId, offeringId, amount, currency, unit, now, nextStart);
    }

    private OfferingRepository.StoredOffering findInOrganization(UUID organizationId, UUID id) {
        return offerings.find(organizationId, id).orElseGet(() -> {
            FoundationAccess.denyScope();
            throw new IllegalStateException("Scope denial must stop execution");
        });
    }

    private void requireRecordReadScope(ActorContext actor, ReadMode mode,
                                       OfferingRepository.StoredOffering offering, Instant now) {
        if (mode == ReadMode.ADMIN) return;
        if (mode == ReadMode.MANAGER) {
            requireRecordManageScope(actor, offering);
            return;
        }
        if (!"ACTIVE".equals(offering.status())
                || offerings.salesAssignmentScope(actor.organizationId(), offering.id(), actor.userId(), now).isEmpty()) {
            FoundationAccess.denyScope();
        }
    }

    private void requireRecordManageScope(ActorContext actor, OfferingRepository.StoredOffering offering) {
        if (actor.hasRole(RoleCode.ADMIN)) return;
        if (!actor.hasRole(RoleCode.MANAGER)) FoundationAccess.denyPermission();
        if (offering.teamId() == null) FoundationAccess.denyScope();
        policy.requireTeamScope(actor, actor.organizationId(), offering.teamId());
    }

    private void requireWritableTeam(ActorContext actor, UUID teamId) {
        policy.requireTeamScope(actor, actor.organizationId(), teamId);
        String status = offerings.teamStatus(actor.organizationId(), teamId)
                .orElseThrow(() -> JsonInput.invalid("teamId", "Team phải tồn tại trong organization hiện tại."));
        if (!"ACTIVE".equals(status)) throw ApiRequestException.conflict("TEAM_INACTIVE");
    }

    private void requireActiveCategory(UUID organizationId, UUID categoryId) {
        String status = offerings.categoryStatus(organizationId, categoryId).orElse(null);
        if (!"ACTIVE".equals(status)) {
            throw JsonInput.invalid("categoryId", "Category phải đang hoạt động trong organization hiện tại.");
        }
    }

    private ReadMode requireReadMode(ActorContext actor) {
        if (actor.hasRole(RoleCode.ADMIN) && actor.hasPermission(PermissionCode.OFFERING_MANAGE)) return ReadMode.ADMIN;
        if (actor.hasRole(RoleCode.MANAGER) && actor.hasPermission(PermissionCode.OFFERING_MANAGE)) return ReadMode.MANAGER;
        if (actor.hasRole(RoleCode.SALES_REP) && actor.hasPermission(PermissionCode.OFFERING_READ)) return ReadMode.SALES;
        if (!actor.hasPermission(PermissionCode.OFFERING_MANAGE)
                && !actor.hasPermission(PermissionCode.OFFERING_READ)) FoundationAccess.denyPermission();
        FoundationAccess.denyPermission();
        throw new IllegalStateException("Permission denial must stop execution");
    }

    private void requireManage(ActorContext actor) {
        policy.requirePermission(actor, PermissionCode.OFFERING_MANAGE);
        if (!actor.hasRole(RoleCode.ADMIN) && !actor.hasRole(RoleCode.MANAGER)) FoundationAccess.denyPermission();
    }

    private OfferingResponse response(OfferingRepository.StoredOffering row, OfferingRepository.StoredPrice price) {
        return new OfferingResponse(row.id(), row.code(), row.name(), row.kind(), row.categoryId(), row.categoryName(),
                row.description(), row.unit(), price == null ? null : price.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                price == null ? null : price.currencyCode(), price == null ? null : price.pricingUnit(),
                price == null ? null : price.effectiveFrom(), "ACTIVE".equals(row.status()), row.teamId(),
                row.teamName(), row.createdAt(), row.updatedAt());
    }

    private static String normalizeKind(String value) {
        String kind = value.toUpperCase(Locale.ROOT);
        if (!KINDS.contains(kind)) throw JsonInput.invalid("kind", "Loại chỉ nhận PRODUCT hoặc SERVICE.");
        return kind;
    }

    private static String normalizeName(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean requiredBoolean(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isBoolean()) throw JsonInput.invalid(field, "Phải là giá trị true hoặc false.");
        return value.booleanValue();
    }

    private static BigDecimal decimal(JsonNode body, String field, boolean required) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            if (!required) return null;
            throw JsonInput.invalid(field, "Đây là trường bắt buộc.");
        }
        if (!value.isTextual() && !value.isNumber()) throw JsonInput.invalid(field, "Phải là số thập phân hợp lệ.");
        String source = value.asText().strip();
        if (source.isEmpty() || source.length() > 40) throw JsonInput.invalid(field, "Giá phải là số thập phân hợp lệ.");
        try {
            BigDecimal amount = new BigDecimal(source).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() < 0 || amount.compareTo(MAX_PRICE) > 0 || amount.precision() > 18) {
                throw JsonInput.invalid(field, "Giá phải từ 0 đến 9999999999999999.99 và tối đa 2 chữ số thập phân.");
            }
            return amount;
        } catch (NumberFormatException | ArithmeticException ex) {
            throw JsonInput.invalid(field, "Giá phải từ 0 đến 9999999999999999.99 và tối đa 2 chữ số thập phân.");
        }
    }

    private static Instant instant(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw JsonInput.invalid(field, "Thời điểm cập nhật không hợp lệ.");
        }
    }

    private static void requireUpdateFields(JsonNode body) {
        for (String field : List.of("name", "kind", "categoryId", "teamId", "referencePrice", "expectedUpdatedAt")) {
            if (!body.has(field)) throw JsonInput.invalid(field, "Thiếu trường bắt buộc.");
        }
    }

    private enum ReadMode { ADMIN, MANAGER, SALES }

    private static final class IdentityPlaceholders {
        private static String of(int count) { return String.join(",", Collections.nCopies(count, "?")); }
    }
}
