package com.pose.backend.crm;

import com.pose.backend.identity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

/** Authorization predicates for Interaction and CRM Task, built from CRM subject scope. */
@Component
public class CrmWorkAccess {
    private final CrmAccess crm;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CrmWorkAccess(CrmAccess crm, JdbcTemplate jdbc, Clock clock) {
        this.crm = crm;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public CrmAccess.Scope subjectScope(ActorContext actor, PermissionCode permission, boolean write,
                                        String recordAlias, String customerIdColumn, String leadIdColumn) {
        if (!actor.hasPermission(permission)) FoundationAccess.denyPermission();
        List<String> predicates = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        addSubjectRole(actor, RoleCode.ADMIN, permission, write, predicates, args,
                recordAlias, customerIdColumn, leadIdColumn);
        addSubjectRole(actor, RoleCode.MANAGER, permission, write, predicates, args,
                recordAlias, customerIdColumn, leadIdColumn);
        addSubjectRole(actor, RoleCode.SALES_REP, permission, write, predicates, args,
                recordAlias, customerIdColumn, leadIdColumn);
        addCare(actor, permission, write, predicates, args, recordAlias, customerIdColumn, leadIdColumn);
        if (!write && role(actor, RoleCode.DATA_STAFF, permission, false)) predicates.add("1=1");
        if (predicates.isEmpty()) FoundationAccess.denyScope();
        return new CrmAccess.Scope("(" + String.join(" OR ", predicates) + ")", List.copyOf(args));
    }

    private void addSubjectRole(ActorContext actor, RoleCode role, PermissionCode permission, boolean write,
                                List<String> predicates, List<Object> args, String recordAlias,
                                String customerIdColumn, String leadIdColumn) {
        if (!role(actor, role, permission, write)) return;
        if (role == RoleCode.ADMIN) {
            predicates.add("1=1");
            return;
        }
        addSubjectFragment(actor.userId(), role, false, predicates, args, recordAlias, customerIdColumn);
        addSubjectFragment(actor.userId(), role, true, predicates, args, recordAlias, leadIdColumn);
    }

    private void addSubjectFragment(UUID user, RoleCode role, boolean lead, List<String> predicates,
                                    List<Object> args, String recordAlias, String subjectColumn) {
        CrmAccess.Scope fragment = crm.subjectRoleScope(user, role, lead);
        String table = lead ? "leads l" : "customers c";
        String alias = lead ? "l" : "c";
        predicates.add("EXISTS (SELECT 1 FROM " + table + " WHERE " + alias + ".organization_id="
                + recordAlias + ".organization_id AND " + alias + ".id=" + subjectColumn
                + " AND " + alias + ".archived_at IS NULL AND (" + fragment.where() + "))");
        args.addAll(fragment.args());
    }

    private void addCare(ActorContext actor, PermissionCode permission, boolean write, List<String> predicates,
                         List<Object> args, String recordAlias, String customerIdColumn, String leadIdColumn) {
        if (!role(actor, RoleCode.CUSTOMER_CARE, permission, write)) return;
        Timestamp now = Timestamp.from(clock.instant());
        predicates.add("EXISTS (SELECT 1 FROM customers c WHERE c.organization_id=" + recordAlias
                + ".organization_id AND c.id=" + customerIdColumn + " AND c.archived_at IS NULL"
                + " AND EXISTS (SELECT 1 FROM customer_assignments ca WHERE ca.organization_id=c.organization_id"
                + " AND ca.customer_id=c.id AND ca.user_id=? AND ca.assignment_type='CARE' AND ca.status='ACTIVE'"
                + " AND ca.effective_from<=? AND (ca.effective_to IS NULL OR ca.effective_to>=?)))");
        args.addAll(List.of(actor.userId(), now, now));
        // Care assignments are Customer-based; a Lead-only record is outside Customer Care scope.
    }

    public boolean hasRolePermission(ActorContext actor, RoleCode role, PermissionCode permission) {
        return crm.roleWith(actor, role, permission);
    }

    public CrmAccess.Scope taskScope(ActorContext actor, boolean write) {
        CrmAccess.Scope base = subjectScope(actor, PermissionCode.TASK_MANAGE, write, "t", "t.customer_id", "t.lead_id");
        List<Object> args = new ArrayList<>(base.args());
        String where = base.where();
        if (hasRolePermission(actor, RoleCode.CUSTOMER_CARE, PermissionCode.TASK_MANAGE)) {
            where = "(" + where + " OR (t.owner_user_id=? AND ("
                    + "EXISTS (SELECT 1 FROM customers c WHERE c.organization_id=t.organization_id AND c.id=t.customer_id AND c.archived_at IS NULL)"
                    + " OR EXISTS (SELECT 1 FROM leads l WHERE l.organization_id=t.organization_id AND l.id=t.lead_id AND l.archived_at IS NULL))))";
            args.add(actor.userId());
        }
        boolean manager = hasRolePermission(actor, RoleCode.MANAGER, PermissionCode.TASK_MANAGE);
        boolean admin = hasRolePermission(actor, RoleCode.ADMIN, PermissionCode.TASK_MANAGE);
        boolean dataReader = !write && actor.hasRole(RoleCode.DATA_STAFF);
        if (!manager && !admin && !dataReader) {
            where = "(" + where + ") AND t.owner_user_id=?";
            args.add(actor.userId());
        }
        return new CrmAccess.Scope(where, List.copyOf(args));
    }

    public CrmAccess.Scope managerSubjectScope(UUID managerId, String recordAlias, String customerIdColumn, String leadIdColumn) {
        List<String> predicates = new ArrayList<>(); List<Object> args = new ArrayList<>();
        addSubjectFragment(managerId, RoleCode.MANAGER, false, predicates, args, recordAlias, customerIdColumn);
        addSubjectFragment(managerId, RoleCode.MANAGER, true, predicates, args, recordAlias, leadIdColumn);
        return new CrmAccess.Scope("(" + String.join(" OR ", predicates) + ")", List.copyOf(args));
    }

    public boolean activeCareAssignment(UUID organizationId, UUID customerId, UUID userId) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.queryForObject("""
                SELECT count(*) FROM customer_assignments
                WHERE organization_id=? AND customer_id=? AND user_id=? AND assignment_type='CARE'
                  AND status='ACTIVE' AND effective_from<=? AND (effective_to IS NULL OR effective_to>=?)
                """, Integer.class, organizationId, customerId, userId, now, now) > 0;
    }

    private boolean role(ActorContext actor, RoleCode role, PermissionCode permission, boolean write) {
        return write ? crm.roleWith(actor, role, permission) : actor.hasRole(role);
    }
}
