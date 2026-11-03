package com.pose.backend.crm;

import com.pose.backend.api.ApiRequestException;
import com.pose.backend.identity.*;
import com.pose.backend.offering.OfferingRepository;
import com.pose.backend.salesassignment.SalesAssignmentCandidateResponse;
import com.pose.backend.salesassignment.SalesAssignmentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

@Component
public class CrmAccess {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final OfferingRepository offerings;
    private final SalesAssignmentRepository assignments;
    public CrmAccess(JdbcTemplate jdbc, Clock clock, OfferingRepository offerings, SalesAssignmentRepository assignments) {
        this.jdbc=jdbc; this.clock=clock; this.offerings=offerings; this.assignments=assignments;
    }
    public boolean roleWith(ActorContext actor, RoleCode role, PermissionCode permission) {
        if (!actor.hasRole(role) || !actor.hasPermission(permission)) return false;
        Timestamp now=Timestamp.from(clock.instant());
        return jdbc.queryForObject("""
                SELECT count(*) FROM user_role_grants g JOIN roles r ON r.id=g.role_id AND r.organization_id=g.organization_id
                JOIN role_permissions rp ON rp.role_id=r.id JOIN permissions p ON p.id=rp.permission_id
                WHERE g.organization_id=? AND g.user_id=? AND r.code=? AND r.status='ACTIVE' AND p.code=?
                  AND g.granted_at<=? AND (g.revoked_at IS NULL OR g.revoked_at>?)
                """,Integer.class,actor.organizationId(),actor.userId(),role.name(),permission.code(),now,now)>0;
    }
    public Scope scope(ActorContext actor, boolean lead, boolean write) {
        PermissionCode permission=lead ? (write?PermissionCode.LEAD_WRITE:PermissionCode.LEAD_READ)
                : (write?PermissionCode.CUSTOMER_WRITE:PermissionCode.CUSTOMER_READ);
        if (!actor.hasPermission(permission)) FoundationAccess.denyPermission();
        List<String> parts=new ArrayList<>(); List<Object> args=new ArrayList<>();
        Timestamp now=Timestamp.from(clock.instant()); String alias=lead?"l":"c";
        if (scopeRole(actor,RoleCode.ADMIN,permission,write)) parts.add("1=1");
        if (scopeRole(actor,RoleCode.MANAGER,permission,write)) {
            if (lead) {
                parts.add("EXISTS (SELECT 1 FROM offerings mo JOIN teams mt ON mt.id=mo.team_id AND mt.organization_id=mo.organization_id WHERE mo.id=l.offering_id AND mo.organization_id=l.organization_id AND mt.status='ACTIVE' AND mt.manager_user_id=?)");
                args.add(actor.userId());
            } else {
                parts.add("""
                        EXISTS (SELECT 1 FROM team_memberships tm JOIN teams t ON t.id=tm.team_id AND t.organization_id=tm.organization_id
                          WHERE tm.organization_id=c.organization_id AND t.status='ACTIVE' AND t.manager_user_id=?
                            AND tm.status='ACTIVE' AND tm.joined_at<=? AND (tm.left_at IS NULL OR tm.left_at>?)
                            AND (tm.user_id=c.owner_user_id OR EXISTS (SELECT 1 FROM customer_assignments ca
                              WHERE ca.organization_id=c.organization_id AND ca.customer_id=c.id AND ca.user_id=tm.user_id
                                AND ca.status='ACTIVE' AND ca.effective_from<=? AND (ca.effective_to IS NULL OR ca.effective_to>=?))))
                        """);
                args.addAll(List.of(actor.userId(),now,now,now,now));
            }
        }
        if (scopeRole(actor,RoleCode.SALES_REP,permission,write)
                && (!lead || actor.hasPermission(PermissionCode.OFFERING_READ))) {
            if (lead) {
                parts.add("l.owner_user_id=? AND EXISTS (SELECT 1 FROM offerings o WHERE o.id=l.offering_id AND o.organization_id=l.organization_id AND " + OfferingRepository.SALES_READ_SCOPE + ")");
                args.addAll(List.of(actor.userId(),actor.userId(),now,now,now,now));
            } else {
                parts.add("(c.owner_user_id=? OR EXISTS (SELECT 1 FROM customer_assignments ca WHERE ca.organization_id=c.organization_id AND ca.customer_id=c.id AND ca.user_id=? AND ca.assignment_type='SALES' AND ca.status='ACTIVE' AND ca.effective_from<=? AND (ca.effective_to IS NULL OR ca.effective_to>=?)))");
                args.addAll(List.of(actor.userId(),actor.userId(),now,now));
            }
        }
        if (!lead && !write && actor.hasRole(RoleCode.CUSTOMER_CARE)) {
            parts.add("EXISTS (SELECT 1 FROM customer_assignments ca WHERE ca.organization_id=c.organization_id AND ca.customer_id=c.id AND ca.user_id=? AND ca.assignment_type='CARE' AND ca.status='ACTIVE' AND ca.effective_from<=? AND (ca.effective_to IS NULL OR ca.effective_to>=?))");
            args.addAll(List.of(actor.userId(),now,now));
        }
        if (parts.isEmpty()) FoundationAccess.denyScope();
        List<Object> all=new ArrayList<>(); all.add(actor.organizationId()); all.addAll(args);
        return new Scope(alias+".organization_id=? AND "+alias+".archived_at IS NULL AND ("+String.join(" OR ",parts)+")",all);
    }
    public UUID ownerForCreate(ActorContext actor, boolean lead, UUID offeringId, UUID requestedOwner) {
        PermissionCode permission=lead?PermissionCode.LEAD_WRITE:PermissionCode.CUSTOMER_WRITE;
        if (!actor.hasPermission(permission)) FoundationAccess.denyPermission();
        boolean manager=roleWith(actor,RoleCode.MANAGER,permission);
        boolean sales=roleWith(actor,RoleCode.SALES_REP,permission);
        if (!manager && !sales) FoundationAccess.denyPermission();
        UUID owner;
        if (sales && (requestedOwner == null || requestedOwner.equals(actor.userId()))
                && actor.hasPermission(PermissionCode.OFFERING_READ)
                && offerings.salesAssignmentScope(actor.organizationId(),offeringId,actor.userId(),clock.instant()).isPresent()) {
            return actor.userId();
        }
        if (manager && requestedOwner != null) {
            requireManagedOffering(actor, offeringId, false);
            owner=requestedOwner;
            if (!eligibleOwner(actor.organizationId(),owner,offeringId)) throw ApiRequestException.invalidOwner();
        } else if (sales) {
            if (requestedOwner != null && !requestedOwner.equals(actor.userId())) FoundationAccess.denyScope();
            owner=actor.userId();
            if (!actor.hasPermission(PermissionCode.OFFERING_READ)
                    || offerings.salesAssignmentScope(actor.organizationId(),offeringId,owner,clock.instant()).isEmpty()) FoundationAccess.denyScope();
        } else throw com.pose.backend.api.JsonInput.invalid("ownerUserId","Vui lòng điền đầy đủ các trường bắt buộc");
        return owner;
    }
    public void requireCreateRole(ActorContext actor, boolean lead) {
        PermissionCode permission=lead?PermissionCode.LEAD_WRITE:PermissionCode.CUSTOMER_WRITE;
        if (!roleWith(actor,RoleCode.MANAGER,permission) && !roleWith(actor,RoleCode.SALES_REP,permission)) FoundationAccess.denyPermission();
    }
    private boolean scopeRole(ActorContext actor,RoleCode role,PermissionCode permission,boolean write) {
        return write ? roleWith(actor,role,permission) : actor.hasRole(role);
    }
    public boolean eligibleOwner(UUID organization, UUID owner, UUID offering) {
        Timestamp now=Timestamp.from(clock.instant());
        int n=jdbc.queryForObject("""
                SELECT count(*) FROM users u JOIN user_role_grants g ON g.user_id=u.id AND g.organization_id=u.organization_id
                JOIN roles r ON r.id=g.role_id AND r.organization_id=g.organization_id
                WHERE u.id=? AND u.organization_id=? AND u.status='ACTIVE' AND u.archived_at IS NULL
                  AND r.code='SALES_REP' AND r.status='ACTIVE' AND g.granted_at<=? AND (g.revoked_at IS NULL OR g.revoked_at>?)
                  AND EXISTS (SELECT 1 FROM user_role_grants og JOIN roles orole ON orole.id=og.role_id AND orole.organization_id=og.organization_id
                    JOIN role_permissions rp ON rp.role_id=orole.id JOIN permissions p ON p.id=rp.permission_id
                    WHERE og.organization_id=u.organization_id AND og.user_id=u.id AND orole.status='ACTIVE'
                      AND p.code='offering.read' AND og.granted_at<=? AND (og.revoked_at IS NULL OR og.revoked_at>?))
                """,Integer.class,owner,organization,now,now,now,now);
        return n>0 && offerings.salesAssignmentScope(organization,offering,owner,clock.instant()).isPresent();
    }
    public void requireManagedOffering(ActorContext actor, UUID offering, boolean allowAdmin) {
        boolean admin=allowAdmin && actor.hasRole(RoleCode.ADMIN);
        if (!managedOffering(actor,offering,admin)) FoundationAccess.denyScope();
    }
    public boolean managedOffering(ActorContext actor, UUID offering, boolean admin) {
        Integer n=jdbc.queryForObject("""
                SELECT count(*) FROM offerings o JOIN teams t ON t.id=o.team_id AND t.organization_id=o.organization_id
                WHERE o.organization_id=? AND o.id=? AND o.status='ACTIVE' AND t.status='ACTIVE'
                  AND (?=TRUE OR t.manager_user_id=?)
                """,Integer.class,actor.organizationId(),offering,admin,actor.userId());
        return n>0;
    }
    public List<SalesAssignmentCandidateResponse> candidates(ActorContext actor, UUID offering, boolean lead) {
        PermissionCode read=lead?PermissionCode.LEAD_READ:PermissionCode.CUSTOMER_READ;
        if (!actor.hasPermission(read)) FoundationAccess.denyPermission();
        boolean admin=actor.hasRole(RoleCode.ADMIN);
        if (!admin && !actor.hasRole(RoleCode.MANAGER)) FoundationAccess.denyPermission();
        var rows=jdbc.query("""
                SELECT o.team_id,o.status FROM offerings o LEFT JOIN teams t ON t.id=o.team_id AND t.organization_id=o.organization_id
                WHERE o.organization_id=? AND o.id=? AND (?=TRUE OR (t.status='ACTIVE' AND t.manager_user_id=?))
                """,(rs,n)->new CandidateOffering(rs.getObject("team_id",UUID.class),rs.getString("status")),
                actor.organizationId(),offering,admin,actor.userId());
        if (rows.isEmpty()) { FoundationAccess.denyScope(); }
        CandidateOffering selected=rows.getFirst();
        if (selected.teamId()==null || !"ACTIVE".equals(selected.status())) return List.of();
        UUID team=selected.teamId();
        return assignments.candidates(actor.organizationId(),team,clock.instant()).stream()
                .filter(candidate -> eligibleOwner(actor.organizationId(),candidate.id(),offering)).toList();
    }
    private record CandidateOffering(UUID teamId,String status) {}
    public record Scope(String where, List<Object> args) {}
}
