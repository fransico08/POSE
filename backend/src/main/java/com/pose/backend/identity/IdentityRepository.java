package com.pose.backend.identity;

import com.fasterxml.jackson.databind.*;
import com.pose.backend.auth.AuthAccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

@Repository
public class IdentityRepository {
    private final JdbcTemplate jdbc;
    private final AuthAccountRepository accounts;
    private final ObjectMapper mapper;
    private final Clock clock;
    public IdentityRepository(JdbcTemplate jdbc,AuthAccountRepository accounts,ObjectMapper mapper,Clock clock) {
        this.jdbc=jdbc; this.accounts=accounts; this.mapper=mapper; this.clock=clock;
    }
    public Optional<UserResponse> find(UUID org,UUID id) {
        return jdbc.query("SELECT * FROM users WHERE organization_id=? AND id=?", (r,n)->{
            var account=accounts.findById(org,id);
            return new UserResponse(id,r.getString("email"),r.getString("full_name"),r.getString("status"),readJson(r.getString("metadata")),
                    account.map(a->a.roles().stream().map(Enum::name).sorted().toList()).orElse(List.of()),
                    account.map(a->a.permissions().stream().map(PermissionCode::code).sorted().toList()).orElse(List.of()),
                    teamIds(org,id));
        },org,id).stream().findFirst();
    }
    public List<UUID> teamIds(UUID org,UUID user) {
        Timestamp now=Timestamp.from(clock.instant());
        return jdbc.queryForList("""
                SELECT DISTINCT t.id FROM teams t JOIN team_memberships m ON m.team_id=t.id
                WHERE t.organization_id=? AND m.organization_id=t.organization_id AND m.user_id=?
                  AND t.status='ACTIVE' AND m.status='ACTIVE' AND m.joined_at<=?
                  AND (m.left_at IS NULL OR m.left_at>?) ORDER BY t.id
                """,UUID.class,org,user,now,now);
    }
    public record Scope(String sql,List<Object> args) {}
    public Scope scope(ActorContext actor,String search) {
        String sql="u.organization_id=?";
        List<Object> args=new ArrayList<>(List.of(actor.organizationId()));
        if(!actor.hasRole(RoleCode.ADMIN)) {
            if(actor.hasRole(RoleCode.MANAGER)) {
                sql+="""
                         AND EXISTS (SELECT 1 FROM team_memberships m JOIN teams t ON t.id=m.team_id
                         WHERE m.user_id=u.id AND m.organization_id=u.organization_id AND t.organization_id=u.organization_id
                           AND t.status='ACTIVE' AND t.manager_user_id=? AND m.status='ACTIVE'
                           AND m.joined_at<=? AND (m.left_at IS NULL OR m.left_at>?))
                        """;
                args.add(actor.userId()); args.add(Timestamp.from(clock.instant())); args.add(Timestamp.from(clock.instant()));
            } else { sql+=" AND u.id=?"; args.add(actor.userId()); }
        }
        if(search!=null && !search.isBlank()) {
            if(search.length()>254) throw com.pose.backend.api.JsonInput.invalid("search","Search dài tối đa 254 ký tự.");
            sql+=" AND (LOWER(u.full_name) LIKE ? OR u.normalized_email LIKE ?)";
            String value="%"+search.strip().toLowerCase(Locale.ROOT)+"%"; args.add(value); args.add(value);
        }
        return new Scope(sql,args);
    }
    public long count(Scope scope) { return jdbc.queryForObject("SELECT count(*) FROM users u WHERE "+scope.sql(),Long.class,scope.args().toArray()); }
    public List<UserResponse> list(Scope scope,int page,int size) {
        List<Object> args=new ArrayList<>(scope.args()); args.add(size); args.add((long)page*size);
        return jdbc.queryForList("SELECT u.id FROM users u WHERE "+scope.sql()+" ORDER BY u.full_name,u.id LIMIT ? OFFSET ?",UUID.class,args.toArray())
                .stream().map(id->find((UUID)scope.args().getFirst(),id).orElseThrow()).toList();
    }
    public UUID roleId(UUID org,String code) {
        try { RoleCode.fromCode(code); } catch(IllegalArgumentException ex) { throw com.pose.backend.api.JsonInput.invalid("roleCodes","Role không được hỗ trợ."); }
        var ids=jdbc.queryForList("SELECT id FROM roles WHERE organization_id=? AND code=? AND status='ACTIVE'",UUID.class,org,code);
        if(ids.size()!=1) throw com.pose.backend.api.JsonInput.invalid("roleCodes","Role không ACTIVE trong organization.");
        return ids.getFirst();
    }
    public List<UUID> roleIds(UUID org,List<String> codes) { return codes.stream().map(code->roleId(org,code)).toList(); }
    public JsonNode readJson(String value) {
        try { JsonNode result=mapper.readTree(value); return result.isTextual()?mapper.readTree(result.asText()):result; }
        catch(Exception ex) { throw new IllegalStateException("Invalid persisted JSON",ex); }
    }
}
