package com.pose.backend.team;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.auth.AuthAccountRepository;
import com.pose.backend.identity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

@Service
public class TeamService {
    private final JdbcTemplate jdbc;
    private final TeamRepository teams;
    private final AuthAccountRepository accounts;
    private final FoundationAccess access;
    private final AuthorizationPolicy policy;
    private final FoundationAuditRepository audit;
    private final Clock clock;
    public TeamService(JdbcTemplate jdbc, TeamRepository teams, AuthAccountRepository accounts, FoundationAccess access,
                       AuthorizationPolicy policy, FoundationAuditRepository audit, Clock clock) {
        this.jdbc=jdbc; this.teams=teams; this.accounts=accounts; this.access=access; this.policy=policy; this.audit=audit; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public PageResponse<TeamResponse> list(ActorContext actor,int page,int size) {
        PageResponse.validate(page,size);
        var scope=teams.scope(actor);
        return new PageResponse<>(teams.list(scope,page,size,clock.instant()),page,size,teams.count(scope));
    }
    @Transactional(readOnly=true)
    public TeamResponse get(ActorContext actor,UUID id) {
        var result=teams.find(actor.organizationId(),id,clock.instant());
        if(result.isEmpty()) FoundationAccess.denyScope();
        policy.requireTeamScope(actor,actor.organizationId(),id);
        return result.orElseThrow();
    }
    @Transactional
    public TeamResponse create(ActorContext incoming,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming);
        policy.requirePermission(actor,PermissionCode.ORGANIZATION_TEAM_MANAGE);
        if(!actor.hasRole(RoleCode.ADMIN) && !actor.hasRole(RoleCode.MANAGER)) FoundationAccess.denyPermission();
        JsonInput input=JsonInput.of(body,"code","name","description","managerUserId","departmentId");
        String code=input.text("code",60,true,false).toUpperCase(Locale.ROOT);
        if(!code.matches("[A-Z0-9][A-Z0-9_-]{0,59}")) throw JsonInput.invalid("code","Code chỉ chứa chữ, số, dấu _ hoặc -.");
        String name=input.text("name",160,true,false), description=input.text("description",2000,false,true);
        UUID manager=input.uuid("managerUserId"),department=input.uuid("departmentId");
        var managerAccount=accounts.findById(actor.organizationId(),manager);
        if(managerAccount.isEmpty() || !managerAccount.get().active() || !managerAccount.get().roles().contains(RoleCode.MANAGER))
            throw JsonInput.invalid("managerUserId","Manager phải ACTIVE và có role MANAGER trong organization hiện tại.");
        if(!actor.hasRole(RoleCode.ADMIN) && !manager.equals(actor.userId())) FoundationAccess.denyScope();
        if(jdbc.queryForObject("SELECT count(*) FROM departments WHERE id=? AND organization_id=? AND status='ACTIVE'",Integer.class,department,actor.organizationId())!=1)
            throw JsonInput.invalid("departmentId","Department phải ACTIVE và thuộc organization hiện tại.");
        if(jdbc.queryForObject("SELECT count(*) FROM teams WHERE organization_id=? AND UPPER(code)=?",Integer.class,actor.organizationId(),code)>0)
            throw ApiRequestException.conflict("TEAM_CODE_EXISTS");
        UUID id=UUID.randomUUID();
        Timestamp now=Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO teams(id,organization_id,code,name,description,manager_user_id,department_id,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'ACTIVE',?,?)",
                id,actor.organizationId(),code,name,description,manager,department,now,now);
        TeamResponse result=teams.find(actor.organizationId(),id,clock.instant()).orElseThrow();
        audit.record(actor,"TEAM_CREATED","TEAM",id,null,result,clock.instant());
        return result;
    }
    @Transactional
    public TeamResponse patch(ActorContext incoming,UUID id,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming);
        requireManagement(actor);
        TeamResponse before=get(actor,id);
        JsonInput input=JsonInput.of(body,"name","description","status"); input.requireAny();
        String name=input.has("name")?input.text("name",160,true,false):before.name();
        String description=input.has("description")?input.text("description",2000,false,true):before.description();
        String status=input.has("status")?input.text("status",20,true,false):before.status();
        if(!Set.of("ACTIVE","INACTIVE").contains(status)) throw JsonInput.invalid("status","Chỉ nhận ACTIVE hoặc INACTIVE.");
        if("ACTIVE".equals(status)) {
            var manager=accounts.findById(actor.organizationId(),before.managerUserId());
            if(manager.isEmpty() || !manager.get().active() || !manager.get().roles().contains(RoleCode.MANAGER))
                throw ApiRequestException.conflict("TEAM_MANAGER_INACTIVE");
        }
        if(!Objects.equals(name,before.name()) || !Objects.equals(description,before.description()) || !status.equals(before.status())) {
            jdbc.update("UPDATE teams SET name=?,description=?,status=?,updated_at=? WHERE id=? AND organization_id=?",
                    name,description,status,Timestamp.from(clock.instant()),id,actor.organizationId());
            var after=teams.find(actor.organizationId(),id,clock.instant()).orElseThrow();
            audit.record(actor,"TEAM_UPDATED","TEAM",id,before,after,clock.instant());
            return after;
        }
        return before;
    }
    @Transactional
    public List<TeamResponse.Member> members(ActorContext incoming,UUID id,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming); requireManagement(actor);
        TeamResponse before=get(actor,id);
        if(!"ACTIVE".equals(before.status())) throw ApiRequestException.conflict("TEAM_INACTIVE");
        List<UUID> requested=JsonInput.of(body,"memberUserIds").uuids("memberUserIds",true);
        for(UUID user:requested) {
            var target=accounts.findById(actor.organizationId(),user);
            if(target.isEmpty() || !target.get().active()) throw JsonInput.invalid("memberUserIds","Member phải ACTIVE và cùng organization.");
            if(!actor.hasRole(RoleCode.ADMIN)) policy.requireUserRead(actor,actor.organizationId(),user,target.get().teamIds());
        }
        Set<UUID> current=new HashSet<>(before.members().stream().map(TeamResponse.Member::id).toList());
        if(current.equals(new HashSet<>(requested))) return before.members();
        Timestamp now=Timestamp.from(clock.instant());
        // End obsolete rows, including scheduled memberships; retain their history.
        var stored=jdbc.queryForList("SELECT DISTINCT user_id FROM team_memberships WHERE organization_id=? AND team_id=? AND status='ACTIVE'",UUID.class,actor.organizationId(),id);
        for(UUID old:stored) if(!requested.contains(old))
            jdbc.update("UPDATE team_memberships SET status='ENDED',left_at=CASE WHEN joined_at>? THEN joined_at ELSE ? END WHERE organization_id=? AND team_id=? AND user_id=? AND status='ACTIVE'",
                    now,now,actor.organizationId(),id,old);
        for(UUID added:requested) if(!current.contains(added)) {
            // A future/expired row for this member must not silently become a second active assignment.
            jdbc.update("UPDATE team_memberships SET status='ENDED',left_at=CASE WHEN joined_at>? THEN joined_at ELSE ? END WHERE organization_id=? AND team_id=? AND user_id=? AND status='ACTIVE'",
                    now,now,actor.organizationId(),id,added);
            jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                    UUID.randomUUID(),actor.organizationId(),id,added,now);
        }
        var result=teams.members(actor.organizationId(),id,clock.instant());
        audit.record(actor,"TEAM_MEMBERS_CHANGED","TEAM",id,before.members(),result,clock.instant());
        return result;
    }
    private void requireManagement(ActorContext actor) {
        policy.requirePermission(actor,PermissionCode.ORGANIZATION_TEAM_MANAGE);
        if(!actor.hasRole(RoleCode.ADMIN) && !actor.hasRole(RoleCode.MANAGER)) FoundationAccess.denyPermission();
    }
}
