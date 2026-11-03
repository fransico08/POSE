package com.pose.backend.opportunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.crm.CrmAccess;
import com.pose.backend.crm.CrmInput;
import com.pose.backend.identity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class OpportunityService {
    private static final Set<String> STAGES = Set.of("NEW", "QUALIFIED", "PROPOSAL", "WON", "LOST");
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999999999.99");
    private static final int MAX_LINES = 50;

    private final OpportunityRepository opportunities;
    private final OpportunityAccess opportunityAccess;
    private final CrmAccess crm;
    private final FoundationAccess access;
    private final FoundationAuditRepository audit;
    private final Clock clock;

    public OpportunityService(OpportunityRepository opportunities, OpportunityAccess opportunityAccess,
                             CrmAccess crm, FoundationAccess access, FoundationAuditRepository audit, Clock clock) {
        this.opportunities = opportunities;
        this.opportunityAccess = opportunityAccess;
        this.crm = crm;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<OpportunityResponse> list(ActorContext actor, int page, int size, String search,
                                                  String stage, UUID ownerUserId) {
        PageResponse.validate(page, size);
        CrmAccess.Scope scope = opportunityAccess.scope(actor, false);
        StringBuilder where = new StringBuilder(scope.where());
        List<Object> args = new ArrayList<>(scope.args());
        if (search != null && !search.isBlank()) {
            String input = search.strip();
            if (input.length() > 200) throw JsonInput.invalid("search", "Tối đa 200 ký tự.");
            String pattern = like(input.toLowerCase(Locale.ROOT));
            where.append(" AND (lower(o.title) LIKE ? ESCAPE '!' OR lower(COALESCE(c.display_name,l.display_name)) LIKE ? ESCAPE '!'")
                    .append(" OR EXISTS (SELECT 1 FROM opportunity_lines sl JOIN offerings so ON so.id=sl.offering_id AND so.organization_id=sl.organization_id")
                    .append(" WHERE sl.organization_id=o.organization_id AND sl.opportunity_id=o.id AND (lower(so.name) LIKE ? ESCAPE '!' OR lower(so.code) LIKE ? ESCAPE '!'))) ");
            args.addAll(List.of(pattern, pattern, pattern, pattern));
        }
        if (stage != null) {
            requireStage(stage, "stage");
            where.append(" AND o.stage=?");
            args.add(stage);
        }
        if (ownerUserId != null) {
            if (!actor.hasRole(RoleCode.ADMIN) && !actor.hasRole(RoleCode.MANAGER)
                    && !ownerUserId.equals(actor.userId())) FoundationAccess.denyScope();
            where.append(" AND o.owner_user_id=?");
            args.add(ownerUserId);
        }
        long total = opportunities.count(where.toString(), args);
        List<OpportunityRepository.StoredOpportunity> rows = opportunities.list(where.toString(), args, page, size);
        return new PageResponse<>(responses(rows), page, size, total);
    }

    @Transactional(readOnly = true)
    public OpportunityResponse get(ActorContext actor, UUID id) {
        return find(actor, id, false);
    }

    @Transactional
    public OpportunityResponse create(ActorContext incoming, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        JsonInput in = JsonInput.of(body, "title", "customerId", "ownerUserId", "expectedCloseDate", "notes", "lines", "stage");
        String title = text(in, "title", 160, true);
        UUID customerId = CrmInput.uuid(in, "customerId");
        UUID requestedOwner = in.has("ownerUserId") && !in.missingOrBlank("ownerUserId") ? in.uuid("ownerUserId") : null;
        LocalDate expectedCloseDate = date(in, "expectedCloseDate", true);
        requireNotPast(expectedCloseDate, "expectedCloseDate", "Ngày dự kiến chốt phải từ hôm nay trở đi.");
        String notes = optionalText(in, "notes", 4000);
        if (in.has("stage") && !"NEW".equals(readText(in, "stage", 30, true))) {
            throw JsonInput.invalid("stage", "Cơ hội mới phải bắt đầu ở giai đoạn Mới.");
        }
        List<OpportunityLineInput> lines = lines(in.getBodyNode(), "lines");
        opportunityAccess.requireAccessibleCustomer(actor, customerId);
        UUID ownerUserId = opportunityAccess.ownerForCreate(actor, requestedOwner, lines);
        BigDecimal expectedValue = total(lines);
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        opportunities.insert(id, actor.organizationId(), customerId, null, ownerUserId, title,
                expectedCloseDate, expectedValue, notes, lines, now);
        audit.record(actor, "OPPORTUNITY_CREATED", "OPPORTUNITY", id, null,
                snapshot(customerId, ownerUserId, "NEW", expectedValue, null, null, lines.size()), now);
        return find(actor, id, false);
    }

    @Transactional
    public OpportunityResponse update(ActorContext incoming, UUID id, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        OpportunityResponse beforeResponse = find(actor, id, true);
        CrmAccess.Scope writeScope = opportunityAccess.scope(actor, true);
        OpportunityRepository.StoredOpportunity before = opportunities.find(writeScope.where(), writeScope.args(), id)
                .orElseGet(() -> { FoundationAccess.denyScope(); return null; });
        if (beforeResponse.updatedAt() == null) throw ApiRequestException.notFound();

        JsonInput in = JsonInput.of(body, "title", "customerId", "ownerUserId", "expectedCloseDate", "notes",
                "lines", "stage", "closingReason", "actualValue", "actualCloseDate", "expectedUpdatedAt");
        Instant expectedUpdatedAt = CrmInput.instant(in, "expectedUpdatedAt");
        if (!expectedUpdatedAt.equals(before.updatedAt())) throw ApiRequestException.conflict("OPPORTUNITY_CHANGED");
        if (Set.of("WON", "LOST").contains(before.stage())) throw ApiRequestException.conflict("OPPORTUNITY_LOCKED");

        String title = in.has("title") ? text(in, "title", 160, true) : before.title();
        UUID customerId = before.customerId();
        UUID leadId = before.leadId();
        if (in.has("customerId")) {
            customerId = CrmInput.uuid(in, "customerId");
            leadId = null;
            opportunityAccess.requireAccessibleCustomer(actor, customerId);
        }
        UUID ownerUserId = in.has("ownerUserId") ? CrmInput.uuid(in, "ownerUserId") : before.ownerUserId();
        LocalDate expectedCloseDate = in.has("expectedCloseDate")
                ? date(in, "expectedCloseDate", true) : before.expectedCloseDate();
        if (in.has("expectedCloseDate")) requireNotPast(expectedCloseDate, "expectedCloseDate", "Ngày dự kiến chốt phải từ hôm nay trở đi.");
        String notes = in.has("notes") ? optionalText(in, "notes", 4000) : before.notes();
        String stage = in.has("stage") ? stage(readText(in, "stage", 30, true), "stage") : before.stage();
        if (!allowedTransition(before.stage(), stage)) throw ApiRequestException.conflict("OPPORTUNITY_STAGE_CONFLICT");
        if (expectedCloseDate == null && (!Objects.equals(stage, before.stage()) || in.has("expectedCloseDate"))) {
            throw JsonInput.invalid("expectedCloseDate", "Vui lòng nhập ngày dự kiến chốt.");
        }

        List<OpportunityLineResponse> oldLines = beforeResponse.lines();
        List<OpportunityLineInput> replacementLines = in.has("lines") ? lines(in.getBodyNode(), "lines") : null;
        List<UUID> resultingOfferingIds = replacementLines == null
                ? oldLines.stream().map(OpportunityLineResponse::offeringId).toList()
                : replacementLines.stream().map(OpportunityLineInput::offeringId).toList();
        boolean linesChanged = replacementLines != null && !sameLines(oldLines, replacementLines);
        if (in.has("ownerUserId") || linesChanged) {
            opportunityAccess.validateOwnerAndOfferings(actor, ownerUserId, resultingOfferingIds);
        }

        String closingReason = null;
        Instant closedAt = null;
        BigDecimal actualValue = null;
        LocalDate actualCloseDate = null;
        if ("WON".equals(stage)) {
            closingReason = text(in, "closingReason", 4000, true);
            actualValue = money(in.getBodyNode().get("actualValue"), "actualValue", true);
            actualCloseDate = date(in, "actualCloseDate", true);
            requireNotFuture(actualCloseDate, "actualCloseDate", "Ngày chốt thực tế không thể ở tương lai.");
            closedAt = clock.instant();
        } else if ("LOST".equals(stage)) {
            closingReason = text(in, "closingReason", 4000, true);
            if (in.has("actualValue") || in.has("actualCloseDate")) {
                throw JsonInput.invalid(in.has("actualValue") ? "actualValue" : "actualCloseDate",
                        "Thông tin giá trị chốt chỉ áp dụng cho cơ hội Thành công.");
            }
            closedAt = clock.instant();
        } else if (in.has("closingReason") || in.has("actualValue") || in.has("actualCloseDate")) {
            throw JsonInput.invalid("stage", "Chỉ nhập thông tin chốt khi chuyển cơ hội sang Thành công hoặc Thất bại.");
        }

        if (customerId == null && leadId == null || customerId != null && leadId != null) {
            throw JsonInput.invalid("customerId", "Cơ hội phải gắn với đúng một khách hàng.");
        }
        BigDecimal expectedValue = linesChanged ? total(replacementLines) : before.expectedValue();
        UUID primaryOffering = replacementLines == null ? oldLines.getFirst().offeringId() : replacementLines.getFirst().offeringId();
        boolean changed = !Objects.equals(title, before.title()) || !Objects.equals(customerId, before.customerId())
                || !Objects.equals(leadId, before.leadId()) || !Objects.equals(ownerUserId, before.ownerUserId())
                || !Objects.equals(expectedCloseDate, before.expectedCloseDate()) || !Objects.equals(notes, before.notes())
                || !Objects.equals(stage, before.stage()) || linesChanged;
        if (!changed) return beforeResponse;

        Instant now = clock.instant();
        opportunities.update(actor.organizationId(), id, customerId, leadId, ownerUserId, primaryOffering, title, stage,
                expectedValue, expectedCloseDate, closingReason, closedAt, notes, actualValue, actualCloseDate, now);
        if (linesChanged) opportunities.replaceLines(actor.organizationId(), id, replacementLines, now);
        String action = !Objects.equals(stage, before.stage()) ? "OPPORTUNITY_STAGE_CHANGED" : "OPPORTUNITY_UPDATED";
        audit.record(actor, action, "OPPORTUNITY", id,
                snapshot(before.customerId(), before.ownerUserId(), before.stage(), before.expectedValue(),
                        before.actualValue(), before.actualCloseDate(), oldLines.size()),
                snapshot(customerId, ownerUserId, stage, expectedValue, actualValue, actualCloseDate,
                        linesChanged ? replacementLines.size() : oldLines.size()), now);
        return find(actor, id, false);
    }

    @Transactional
    public OpportunityResponse reopen(ActorContext incoming, UUID id, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        if (!crm.roleWith(actor, RoleCode.MANAGER, PermissionCode.OPPORTUNITY_REOPEN)
                || !actor.hasPermission(PermissionCode.OPPORTUNITY_WRITE)) FoundationAccess.denyPermission();
        JsonInput in = JsonInput.of(body, "expectedUpdatedAt");
        Instant expectedUpdatedAt = CrmInput.instant(in, "expectedUpdatedAt");
        CrmAccess.Scope writeScope = opportunityAccess.scope(actor, true);
        OpportunityRepository.StoredOpportunity before = opportunities.find(writeScope.where(), writeScope.args(), id)
                .orElseGet(() -> { FoundationAccess.denyScope(); return null; });
        if (!expectedUpdatedAt.equals(before.updatedAt())) throw ApiRequestException.conflict("OPPORTUNITY_CHANGED");
        if (!Set.of("WON", "LOST").contains(before.stage())) throw ApiRequestException.conflict("OPPORTUNITY_STATE_CONFLICT");
        Instant now = clock.instant();
        OpportunityResponse current = find(actor, id, true);
        UUID primaryOffering = current.lines().getFirst().offeringId();
        opportunities.update(actor.organizationId(), id, before.customerId(), before.leadId(), before.ownerUserId(),
                primaryOffering, before.title(), "QUALIFIED", before.expectedValue(), before.expectedCloseDate(),
                null, null, before.notes(), null, null, now);
        audit.record(actor, "OPPORTUNITY_REOPENED", "OPPORTUNITY", id,
                snapshot(before.customerId(), before.ownerUserId(), before.stage(), before.expectedValue(),
                        before.actualValue(), before.actualCloseDate(), current.lines().size()),
                snapshot(before.customerId(), before.ownerUserId(), "QUALIFIED", before.expectedValue(),
                        null, null, current.lines().size()), now);
        return find(actor, id, false);
    }

    private OpportunityResponse find(ActorContext actor, UUID id, boolean write) {
        CrmAccess.Scope scope = opportunityAccess.scope(actor, write);
        OpportunityRepository.StoredOpportunity row = opportunities.find(scope.where(), scope.args(), id)
                .orElseGet(() -> { FoundationAccess.denyScope(); return null; });
        return responses(List.of(row)).getFirst();
    }

    private List<OpportunityResponse> responses(List<OpportunityRepository.StoredOpportunity> rows) {
        Map<UUID, List<OpportunityLineResponse>> lines = opportunities.lines(
                rows.isEmpty() ? null : rows.getFirst().organizationId(), rows.stream().map(OpportunityRepository.StoredOpportunity::id).toList());
        return rows.stream().map(row -> row.response(lines.getOrDefault(row.id(), List.of()))).toList();
    }

    private static List<OpportunityLineInput> lines(JsonNode body, String field) {
        JsonNode values = body == null ? null : body.get(field);
        if (values == null || !values.isArray() || values.size() == 0 || values.size() > MAX_LINES) {
            throw JsonInput.invalid(field, "Cơ hội cần từ 1 đến " + MAX_LINES + " dòng sản phẩm/dịch vụ.");
        }
        List<OpportunityLineInput> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String prefix = field + "[" + index + "]";
            JsonInput line = JsonInput.of(values.get(index), "offeringId", "quantity", "unitPrice", "discountPercent");
            UUID offeringId;
            try { offeringId = CrmInput.uuid(line, "offeringId"); }
            catch (ApiRequestException ex) { throw JsonInput.invalid(prefix + ".offeringId", "Vui lòng chọn sản phẩm/dịch vụ hợp lệ."); }
            JsonNode quantityNode = values.get(index).get("quantity");
            if (quantityNode == null || !quantityNode.isIntegralNumber() || !quantityNode.canConvertToInt()
                    || quantityNode.intValue() < 1 || quantityNode.intValue() > 1_000_000) {
                throw JsonInput.invalid(prefix + ".quantity", "Số lượng phải là số nguyên từ 1 đến 1.000.000.");
            }
            BigDecimal price = money(values.get(index).get("unitPrice"), prefix + ".unitPrice", true);
            BigDecimal discount = percentage(values.get(index).get("discountPercent"), prefix + ".discountPercent");
            BigDecimal lineValue = price.multiply(BigDecimal.valueOf(quantityNode.intValue()))
                    .multiply(BigDecimal.valueOf(100).subtract(discount))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            validateMoneyRange(lineValue, prefix + ".unitPrice");
            result.add(new OpportunityLineInput(offeringId, quantityNode.intValue(), price, discount, lineValue));
        }
        return List.copyOf(result);
    }

    private static BigDecimal total(List<OpportunityLineInput> lines) {
        BigDecimal total = lines.stream().map(OpportunityLineInput::lineValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        validateMoneyRange(total, "lines");
        return total;
    }

    private static BigDecimal money(JsonNode value, String field, boolean required) {
        if (value == null || value.isNull()) {
            if (required) throw JsonInput.invalid(field, "Vui lòng nhập giá trị.");
            return null;
        }
        if (!value.isNumber()) throw JsonInput.invalid(field, "Giá trị phải là số không âm.");
        BigDecimal result = value.decimalValue();
        if (result.signum() < 0 || result.stripTrailingZeros().scale() > 2) {
            throw JsonInput.invalid(field, "Giá trị phải không âm và có tối đa hai chữ số thập phân.");
        }
        result = result.setScale(2, RoundingMode.UNNECESSARY);
        validateMoneyRange(result, field);
        return result;
    }

    private static BigDecimal percentage(JsonNode value, String field) {
        if (value == null || !value.isNumber()) throw JsonInput.invalid(field, "Chiết khấu phải là số từ 0 đến 100.");
        BigDecimal result = value.decimalValue();
        if (result.signum() < 0 || result.compareTo(BigDecimal.valueOf(100)) > 0
                || result.stripTrailingZeros().scale() > 2) {
            throw JsonInput.invalid(field, "Chiết khấu phải từ 0% đến 100%, tối đa hai chữ số thập phân.");
        }
        return result.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static void validateMoneyRange(BigDecimal value, String field) {
        if (value.abs().compareTo(MAX_MONEY) > 0) throw JsonInput.invalid(field, "Giá trị vượt quá giới hạn cho phép.");
    }

    private LocalDate date(JsonInput in, String field, boolean required) {
        if (in.missingOrBlank(field)) {
            if (required) throw JsonInput.invalid(field, "Vui lòng nhập ngày.");
            return null;
        }
        try { return LocalDate.parse(readText(in, field, 10, true)); }
        catch (DateTimeParseException ex) { throw JsonInput.invalid(field, "Ngày phải theo định dạng ISO-8601 yyyy-MM-dd."); }
    }

    private static String text(JsonInput in, String field, int max, boolean required) {
        String value = readText(in, field, max, required);
        if (value == null || value.isBlank()) throw JsonInput.invalid(field, "Không được để trống.");
        return value;
    }

    private static String optionalText(JsonInput in, String field, int max) {
        if (!in.has(field) || in.getBodyNode().get(field).isNull()) return null;
        JsonNode node = in.getBodyNode().get(field);
        if (!node.isTextual()) throw JsonInput.invalid(field, "Phải là chuỗi.");
        String value = node.asText().strip();
        if (value.isBlank()) return null;
        if (value.length() > max) throw JsonInput.invalid(field, "Tối đa " + max + " ký tự.");
        return value;
    }

    private static String readText(JsonInput in, String field, int max, boolean required) {
        if (in.missingOrBlank(field)) {
            if (required) throw JsonInput.invalid(field, "Thiếu trường bắt buộc.");
            return null;
        }
        return in.text(field, max, required, false);
    }

    private static String stage(String value, String field) {
        if (!STAGES.contains(value)) throw JsonInput.invalid(field, "Giai đoạn cơ hội không hợp lệ.");
        return value;
    }

    private static void requireStage(String value, String field) { stage(value, field); }

    private static boolean allowedTransition(String current, String next) {
        if (current.equals(next)) return true;
        return switch (current) {
            case "NEW" -> "QUALIFIED".equals(next);
            case "QUALIFIED" -> "PROPOSAL".equals(next);
            case "PROPOSAL" -> Set.of("WON", "LOST").contains(next);
            default -> false;
        };
    }

    private void requireNotPast(LocalDate date, String field, String message) {
        if (date.isBefore(LocalDate.now(clock.withZone(ZoneOffset.UTC)))) throw JsonInput.invalid(field, message);
    }

    private void requireNotFuture(LocalDate date, String field, String message) {
        if (date.isAfter(LocalDate.now(clock.withZone(ZoneOffset.UTC)))) throw JsonInput.invalid(field, message);
    }

    private static String like(String value) {
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static boolean sameLines(List<OpportunityLineResponse> oldLines, List<OpportunityLineInput> newLines) {
        if (oldLines.size() != newLines.size()) return false;
        for (int index = 0; index < oldLines.size(); index++) {
            OpportunityLineResponse old = oldLines.get(index);
            OpportunityLineInput next = newLines.get(index);
            if (!old.offeringId().equals(next.offeringId()) || old.quantity() != next.quantity()
                    || old.unitPrice() == null || old.discountPercent() == null
                    || old.unitPrice().compareTo(next.unitPrice()) != 0
                    || old.discountPercent().compareTo(next.discountPercent()) != 0
                    || old.legacyValueOnly()) return false;
        }
        return true;
    }

    private static AuditSnapshot snapshot(UUID customerId, UUID ownerUserId, String stage, BigDecimal expectedValue,
                                          BigDecimal actualValue, LocalDate actualCloseDate, int lineCount) {
        return new AuditSnapshot(customerId, ownerUserId, stage, expectedValue, actualValue, actualCloseDate, lineCount);
    }

    private record AuditSnapshot(UUID customerId, UUID ownerUserId, String stage, BigDecimal expectedValue,
                                 BigDecimal actualValue, LocalDate actualCloseDate, int lineCount) {
    }
}
