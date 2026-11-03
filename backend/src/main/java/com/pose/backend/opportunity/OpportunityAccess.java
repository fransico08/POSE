package com.pose.backend.opportunity;

import com.pose.backend.api.ApiRequestException;
import com.pose.backend.api.JsonInput;
import com.pose.backend.crm.CrmAccess;
import com.pose.backend.identity.*;
import com.pose.backend.offering.OfferingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

@Component
public class OpportunityAccess {
    private final JdbcTemplate jdbc;
    private final CrmAccess crm;
    private final OfferingRepository offerings;
    private final Clock clock;

    public OpportunityAccess(JdbcTemplate jdbc, CrmAccess crm, OfferingRepository offerings, Clock clock) {
        this.jdbc = jdbc;
        this.crm = crm;
        this.offerings = offerings;
        this.clock = clock;
    }

    public CrmAccess.Scope scope(ActorContext actor, boolean write) {
        boolean permitted = write ? actor.hasPermission(PermissionCode.OPPORTUNITY_WRITE)
                : actor.hasPermission(PermissionCode.OPPORTUNITY_READ)
                || actor.hasPermission(PermissionCode.OPPORTUNITY_WRITE);
        if (!permitted) FoundationAccess.denyPermission();

        List<String> branches = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        Timestamp now = Timestamp.from(clock.instant());
        if (role(actor, RoleCode.ADMIN, write)) branches.add("1=1");
        if (role(actor, RoleCode.MANAGER, write)) {
            branches.add("""
                    (EXISTS (SELECT 1 FROM opportunity_lines ml
                       WHERE ml.organization_id=o.organization_id AND ml.opportunity_id=o.id)
                     AND NOT EXISTS (
                       SELECT 1 FROM opportunity_lines ml
                       JOIN offerings mo ON mo.id=ml.offering_id AND mo.organization_id=ml.organization_id
                       LEFT JOIN teams mt ON mt.id=mo.team_id AND mt.organization_id=mo.organization_id
                       WHERE ml.organization_id=o.organization_id AND ml.opportunity_id=o.id
                         AND (mt.id IS NULL OR mt.status<>'ACTIVE' OR mt.manager_user_id<>?)
                     ))
                    """);
            args.add(actor.userId());
        }
        if (role(actor, RoleCode.SALES_REP, write)) {
            branches.add("""
                    (o.owner_user_id=?
                     AND EXISTS (SELECT 1 FROM opportunity_lines sl
                       WHERE sl.organization_id=o.organization_id AND sl.opportunity_id=o.id)
                     AND NOT EXISTS (
                       SELECT 1 FROM opportunity_lines sl
                       JOIN offerings so ON so.id=sl.offering_id AND so.organization_id=sl.organization_id
                       WHERE sl.organization_id=o.organization_id AND sl.opportunity_id=o.id
                         AND (so.status<>'ACTIVE' OR NOT EXISTS (
                           SELECT 1 FROM sales_assignments sa
                           JOIN team_memberships tm ON tm.organization_id=sa.organization_id
                             AND tm.user_id=sa.sales_user_id AND tm.team_id=so.team_id
                           JOIN teams st ON st.id=tm.team_id AND st.organization_id=tm.organization_id
                           WHERE sa.organization_id=so.organization_id AND sa.offering_id=so.id
                             AND sa.sales_user_id=? AND sa.status='ACTIVE' AND sa.revoked_at IS NULL
                             AND sa.effective_from<=? AND (sa.effective_to IS NULL OR sa.effective_to>=?)
                             AND tm.status='ACTIVE' AND tm.joined_at<=?
                             AND (tm.left_at IS NULL OR tm.left_at>?) AND st.status='ACTIVE'
                         ))
                     ))
                    """);
            args.add(actor.userId());
            args.add(actor.userId());
            args.addAll(List.of(now, now, now, now));
        }
        if (!write && role(actor, RoleCode.CUSTOMER_CARE, false)) {
            branches.add("""
                    (o.customer_id IS NOT NULL AND EXISTS (
                      SELECT 1 FROM customer_assignments ca
                      WHERE ca.organization_id=o.organization_id AND ca.customer_id=o.customer_id
                        AND ca.user_id=? AND ca.assignment_type='CARE' AND ca.status='ACTIVE'
                        AND ca.effective_from<=? AND (ca.effective_to IS NULL OR ca.effective_to>=?)
                    ))
                    """);
            args.addAll(List.of(actor.userId(), now, now));
        }
        if (branches.isEmpty()) FoundationAccess.denyScope();
        List<Object> all = new ArrayList<>();
        all.add(actor.organizationId());
        all.addAll(args);
        return new CrmAccess.Scope("o.organization_id=? AND (" + String.join(" OR ", branches) + ")", all);
    }

    public UUID ownerForCreate(ActorContext actor, UUID requestedOwner, List<OpportunityLineInput> lines) {
        boolean sales = crm.roleWith(actor, RoleCode.SALES_REP, PermissionCode.OPPORTUNITY_WRITE);
        boolean manager = crm.roleWith(actor, RoleCode.MANAGER, PermissionCode.OPPORTUNITY_WRITE);
        boolean admin = crm.roleWith(actor, RoleCode.ADMIN, PermissionCode.OPPORTUNITY_WRITE);
        if (!sales && !manager && !admin) FoundationAccess.denyPermission();
        UUID owner = requestedOwner;
        if (owner == null && sales) owner = actor.userId();
        if (owner == null) throw JsonInput.invalid("ownerUserId", "Vui lòng chọn nhân viên kinh doanh.");
        validateOwnerAndLines(actor, owner, lines, manager, admin, sales);
        return owner;
    }

    public void validateOwnerAndLines(ActorContext actor, UUID owner, List<OpportunityLineInput> lines) {
        boolean sales = crm.roleWith(actor, RoleCode.SALES_REP, PermissionCode.OPPORTUNITY_WRITE);
        boolean manager = crm.roleWith(actor, RoleCode.MANAGER, PermissionCode.OPPORTUNITY_WRITE);
        boolean admin = crm.roleWith(actor, RoleCode.ADMIN, PermissionCode.OPPORTUNITY_WRITE);
        if (!sales && !manager && !admin) FoundationAccess.denyPermission();
        validateOwnerAndLines(actor, owner, lines, manager, admin, sales);
    }

    public void validateOwnerAndOfferings(ActorContext actor, UUID owner, List<UUID> offeringIds) {
        List<OpportunityLineInput> lines = offeringIds.stream()
                .map(id -> new OpportunityLineInput(id, 1, java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)).toList();
        validateOwnerAndLines(actor, owner, lines);
    }

    private void validateOwnerAndLines(ActorContext actor, UUID owner, List<OpportunityLineInput> lines,
                                       boolean manager, boolean admin, boolean sales) {
        if (sales && !manager && !admin && !actor.userId().equals(owner)) FoundationAccess.denyScope();
        for (OpportunityLineInput line : lines) {
            var found = offerings.find(actor.organizationId(), line.offeringId());
            if (found.isEmpty()) FoundationAccess.denyScope();
            var offering = found.get();
            if (!"ACTIVE".equals(offering.status()) || offering.teamId() == null) FoundationAccess.denyScope();
            if (manager && !admin) crm.requireManagedOffering(actor, line.offeringId(), false);
            if (manager || admin) {
                if (!crm.eligibleOwner(actor.organizationId(), owner, line.offeringId())) {
                    throw ApiRequestException.invalidOwner();
                }
            } else if (!actor.userId().equals(owner)
                    || offerings.salesAssignmentScope(actor.organizationId(), line.offeringId(), owner, clock.instant()).isEmpty()) {
                FoundationAccess.denyScope();
            }
        }
    }

    public void requireAccessibleCustomer(ActorContext actor, UUID customerId) {
        CrmAccess.Scope customerScope = crm.scope(actor, false, false);
        List<Object> args = new ArrayList<>(customerScope.args());
        args.add(customerId);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM customers c WHERE " + customerScope.where()
                + " AND c.id=? AND c.status='ACTIVE'", Integer.class, args.toArray());
        if (count == null || count == 0) FoundationAccess.denyScope();
    }

    private boolean role(ActorContext actor, RoleCode role, boolean write) {
        PermissionCode permission = write ? PermissionCode.OPPORTUNITY_WRITE : PermissionCode.OPPORTUNITY_READ;
        if (write) return crm.roleWith(actor, role, permission);
        return actor.hasRole(role) && (actor.hasPermission(PermissionCode.OPPORTUNITY_READ)
                || actor.hasPermission(PermissionCode.OPPORTUNITY_WRITE));
    }
}
