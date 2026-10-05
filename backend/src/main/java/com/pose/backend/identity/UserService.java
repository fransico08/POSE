package com.pose.backend.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.audit.FoundationAuditRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

@Service
public class UserService {
    private final JdbcTemplate jdbc;
    private final IdentityRepository users;
    private final FoundationAccess access;
    private final AuthorizationPolicy policy;
    private final FoundationAuditRepository audit;
    private final PasswordEncoder passwords;
    private final Clock clock;
    public UserService(JdbcTemplate jdbc,IdentityRepository users,FoundationAccess access,AuthorizationPolicy policy,
                       FoundationAuditRepository audit,PasswordEncoder passwords,Clock clock) {
        this.jdbc=jdbc; this.users=users; this.access=access; this.policy=policy; this.audit=audit; this.passwords=passwords; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public PageResponse<UserResponse> list(ActorContext actor,int page,int size,String search) {
        policy.requirePermission(actor,PermissionCode.IDENTITY_USER_READ); PageResponse.validate(page,size);
        var scope=users.scope(actor,search);
        return new PageResponse<>(users.list(scope,page,size),page,size,users.count(scope));
    }
    @Transactional(readOnly=true)
    public UserResponse get(ActorContext actor,UUID id) {
        UserResponse result=scoped(actor,id);
        policy.requireUserRead(actor,actor.organizationId(),id,Set.copyOf(result.teamIds()));
        return result;
    }
    private UserResponse scoped(ActorContext actor,UUID id) {
        var value=users.find(actor.organizationId(),id);
        if(value.isEmpty()) FoundationAccess.denyScope();
        return value.orElseThrow();
    }
    @Transactional
    public UserResponse create(ActorContext incoming,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming);
        access.requireAdmin(actor,PermissionCode.IDENTITY_USER_MANAGE);
        access.requireAdmin(actor,PermissionCode.IDENTITY_ROLE_MANAGE);
        JsonInput input=JsonInput.of(body,"email","fullName","initialPassword","roleCodes","teamIds");
        String email=input.text("email",254,true,false).toLowerCase(Locale.ROOT);
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw JsonInput.invalid("email","Email không hợp lệ.");
        String fullName=input.text("fullName",160,true,false);
        JsonNode pwd=body.get("initialPassword");
        if(pwd==null || !pwd.isTextual() || pwd.asText().isBlank() || pwd.asText().length()<12 || pwd.asText().getBytes(StandardCharsets.UTF_8).length>72)
            throw JsonInput.invalid("initialPassword","Password cần ít nhất 12 ký tự và tối đa 72 byte UTF-8.");
        List<String> codes=input.strings("roleCodes",false);
        List<UUID> roles=users.roleIds(actor.organizationId(),codes);
        List<UUID> teamIds=input.uuids("teamIds",false);
        for(UUID team:teamIds) if(jdbc.queryForObject("SELECT count(*) FROM teams WHERE id=? AND organization_id=? AND status='ACTIVE'",Integer.class,team,actor.organizationId())!=1)
            throw JsonInput.invalid("teamIds","Team phải ACTIVE và thuộc organization.");
        if(jdbc.queryForObject("SELECT count(*) FROM users WHERE organization_id=? AND LOWER(email)=?",Integer.class,actor.organizationId(),email)>0)
            throw ApiRequestException.conflict("USER_EMAIL_EXISTS");
        UUID id=UUID.randomUUID(); Timestamp now=Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO users(id,organization_id,email,normalized_email,full_name,status,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?)",
                id,actor.organizationId(),email,email,fullName,now,now);
        jdbc.update("INSERT INTO credentials(user_id,password_hash,password_changed_at,updated_at) VALUES (?,?,?,?)",id,passwords.encode(pwd.asText()),now,now);
        for(UUID role:roles) addRole(actor,id,role,now);
        for(UUID team:teamIds) jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                UUID.randomUUID(),actor.organizationId(),team,id,now);
        var result=scoped(actor,id);
        audit.record(actor,"USER_CREATED","USER",id,null,result,clock.instant());
        return result;
    }
    @Transactional
    public UserResponse patch(ActorContext incoming,UUID id,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming);
        access.requireAdmin(actor,PermissionCode.IDENTITY_USER_MANAGE);
        UserResponse before=scoped(actor,id);
        JsonInput input=JsonInput.of(body,"fullName","status","metadata"); input.requireAny();
        String name=input.has("fullName")?input.text("fullName",160,true,false):before.fullName();
        String status=input.has("status")?input.text("status",20,true,false):before.status();
        if(!Set.of("ACTIVE","INACTIVE","ARCHIVED").contains(status)) throw JsonInput.invalid("status","Status không hợp lệ.");
        JsonNode metadata=input.has("metadata")?input.metadata("metadata"):before.metadata();
        if(input.has("status") && !status.equals(before.status())) {
            if(id.equals(actor.userId())) FoundationAccess.denySelf();
            if(!"ACTIVE".equals(status)) requireNoActiveManagedTeam(actor.organizationId(),id);
            if(before.roles().contains("ADMIN") && !"ACTIVE".equals(status)) requireOtherAdmin(actor.organizationId(),id);
        }
        if(!name.equals(before.fullName()) || !status.equals(before.status()) || !metadata.equals(before.metadata())) {
            Timestamp now=Timestamp.from(clock.instant());
            jdbc.update("UPDATE users SET full_name=?,status=?,metadata=CAST(? AS jsonb),archived_at=CASE WHEN ?='ARCHIVED' THEN COALESCE(archived_at,?) ELSE NULL END,updated_at=? WHERE id=? AND organization_id=?",
                    name,status,metadata.toString(),status,now,now,id,actor.organizationId());
            if(!"ACTIVE".equals(status)) jdbc.update("UPDATE auth_sessions SET revoked_at=? WHERE organization_id=? AND user_id=? AND revoked_at IS NULL",now,actor.organizationId(),id);
            var after=scoped(actor,id);
            audit.record(actor,"USER_UPDATED","USER",id,before,after,clock.instant());
            return after;
        }
        return before;
    }
    @Transactional
    public UserResponse roles(ActorContext incoming,UUID id,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming);
        policy.requireRoleAdministration(actor,actor.organizationId(),id);
        UserResponse before=scoped(actor,id);
        List<String> codes=JsonInput.of(body,"roleCodes").strings("roleCodes",false);
        List<UUID> roleIds=users.roleIds(actor.organizationId(),codes);
        Timestamp now=Timestamp.from(clock.instant());
        var existing=jdbc.queryForList("SELECT DISTINCT role_id FROM user_role_grants WHERE organization_id=? AND user_id=? AND (revoked_at IS NULL OR revoked_at>?)",UUID.class,actor.organizationId(),id,now);
        if(new HashSet<>(before.roles()).equals(new HashSet<>(codes)) && new HashSet<>(existing).equals(new HashSet<>(roleIds))) return before;
        if(before.roles().contains("ADMIN") && !codes.contains("ADMIN")) requireOtherAdmin(actor.organizationId(),id);
        if(before.roles().contains("MANAGER") && !codes.contains("MANAGER")) requireNoActiveManagedTeam(actor.organizationId(),id);
        // Preserve grant history; end grants omitted from the new role set.
        for(UUID role:existing) if(!roleIds.contains(role)) jdbc.update("UPDATE user_role_grants SET revoked_at=CASE WHEN granted_at>? THEN granted_at ELSE ? END WHERE organization_id=? AND user_id=? AND role_id=? AND (revoked_at IS NULL OR revoked_at>?)",
                now,now,actor.organizationId(),id,role,now);
        for(int n=0;n<roleIds.size();n++) if(!before.roles().contains(codes.get(n))) {
            UUID role=roleIds.get(n);
            jdbc.update("UPDATE user_role_grants SET revoked_at=CASE WHEN granted_at>? THEN granted_at ELSE ? END WHERE organization_id=? AND user_id=? AND role_id=? AND (revoked_at IS NULL OR revoked_at>?)",
                    now,now,actor.organizationId(),id,role,now);
            addRole(actor,id,role,now);
        }
        var after=scoped(actor,id);
        audit.record(actor,"USER_ROLES_CHANGED","USER",id,before,after,clock.instant());
        return after;
    }
    private void addRole(ActorContext actor,UUID user,UUID role,Timestamp now) {
        jdbc.update("INSERT INTO user_role_grants(id,organization_id,user_id,role_id,granted_by_user_id,granted_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),actor.organizationId(),user,role,actor.userId(),now);
    }
    private void requireNoActiveManagedTeam(UUID org,UUID user) {
        if(jdbc.queryForObject("SELECT count(*) FROM teams WHERE organization_id=? AND manager_user_id=? AND status='ACTIVE'",Integer.class,org,user)>0)
            throw ApiRequestException.conflict("ACTIVE_TEAM_MANAGER_REQUIRED");
    }
    private void requireOtherAdmin(UUID org,UUID user) {
        Timestamp now=Timestamp.from(clock.instant());
        if(jdbc.queryForObject("""
                SELECT count(DISTINCT u.id) FROM users u JOIN user_role_grants g ON g.user_id=u.id AND g.organization_id=u.organization_id
                JOIN roles r ON r.id=g.role_id AND r.organization_id=u.organization_id
                WHERE u.organization_id=? AND u.id<>? AND u.status='ACTIVE' AND u.archived_at IS NULL
                  AND r.code='ADMIN' AND r.status='ACTIVE' AND g.granted_at<=? AND (g.revoked_at IS NULL OR g.revoked_at>?)
                """,Integer.class,org,user,now,now)==0) throw ApiRequestException.conflict("LAST_ACTIVE_ADMIN_REQUIRED");
    }
}
