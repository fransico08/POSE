package com.pose.backend.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.audit.FoundationAuditRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

@Service
public class RoleService {
    public record RoleSummary(String code,String displayName) {}
    public record PermissionSummary(String code,String description) {}
    private final JdbcTemplate jdbc;
    private final FoundationAccess access;
    private final IdentityRepository identity;
    private final FoundationAuditRepository audit;
    private final Clock clock;
    public RoleService(JdbcTemplate jdbc,FoundationAccess access,IdentityRepository identity,FoundationAuditRepository audit,Clock clock) {
        this.jdbc=jdbc; this.access=access; this.identity=identity; this.audit=audit; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<RoleSummary> roles(ActorContext actor) {
        return jdbc.query("SELECT code,display_name FROM roles WHERE organization_id=? AND status='ACTIVE' ORDER BY code",
                (r,n)->new RoleSummary(r.getString("code"),r.getString("display_name")),actor.organizationId());
    }
    @Transactional(readOnly=true)
    public List<PermissionSummary> permissions(ActorContext actor) {
        access.requireAdmin(actor,PermissionCode.IDENTITY_ROLE_MANAGE);
        return jdbc.query("SELECT code,description FROM permissions ORDER BY code",(r,n)->new PermissionSummary(r.getString("code"),r.getString("description")));
    }
    @Transactional(readOnly=true)
    public List<String> rolePermissions(ActorContext actor,String code) {
        access.requireAdmin(actor,PermissionCode.IDENTITY_ROLE_MANAGE);
        return permissionsFor(identity.roleId(actor.organizationId(),code));
    }
    private List<String> permissionsFor(UUID role) {
        return jdbc.queryForList("SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id=rp.permission_id WHERE rp.role_id=? ORDER BY p.code",String.class,role);
    }
    @Transactional
    public List<String> setPermissions(ActorContext incoming,String code,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming);
        access.requireAdmin(actor,PermissionCode.IDENTITY_ROLE_MANAGE);
        UUID role=identity.roleId(actor.organizationId(),code);
        List<String> codes=JsonInput.of(body,"permissionCodes").strings("permissionCodes",true);
        for(String permission:codes) {
            try { PermissionCode.fromCode(permission); } catch(IllegalArgumentException ex) { throw JsonInput.invalid("permissionCodes","Permission không được hỗ trợ."); }
            if(!"ADMIN".equals(code) && permission.equals(PermissionCode.IDENTITY_ROLE_MANAGE.code())) FoundationAccess.denyPermission();
        }
        List<String> before=permissionsFor(role);
        if(new HashSet<>(before).equals(new HashSet<>(codes))) return before;
        // Changing a held role changes the caller's effective permissions indirectly.
        if(actor.roles().stream().anyMatch(r->r.name().equals(code))) FoundationAccess.denySelf();
        Timestamp now=Timestamp.from(clock.instant());
        List<UUID> ids=new ArrayList<>();
        for(String permission:codes) {
            var matches=jdbc.queryForList("SELECT id FROM permissions WHERE code=?",UUID.class,permission);
            if(matches.size()!=1) throw JsonInput.invalid("permissionCodes","Permission chưa được cấu hình.");
            ids.add(matches.getFirst());
        }
        jdbc.update("DELETE FROM role_permissions WHERE role_id=?",role);
        for(UUID permission:ids) jdbc.update("INSERT INTO role_permissions(role_id,permission_id,granted_at) VALUES (?,?,?)",role,permission,now);
        List<String> after=permissionsFor(role);
        audit.record(actor,"ROLE_PERMISSIONS_CHANGED","ROLE",role,before,after,clock.instant());
        return after;
    }
}
