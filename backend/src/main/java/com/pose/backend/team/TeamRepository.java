package com.pose.backend.team;

import com.pose.backend.identity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class TeamRepository {
    private final JdbcTemplate jdbc;
    public TeamRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<TeamResponse> find(UUID org, UUID id, Instant now) {
        return jdbc.query("SELECT * FROM teams WHERE organization_id=? AND id=?", (r, n) ->
                new TeamResponse(r.getObject("id", UUID.class), r.getString("code"), r.getString("name"),
                        r.getString("description"), r.getString("status"), r.getObject("department_id", UUID.class),
                        r.getObject("manager_user_id", UUID.class), members(org, id, now)), org, id).stream().findFirst();
    }
    public List<TeamResponse.Member> members(UUID org, UUID team, Instant now) {
        return jdbc.query("""
                SELECT DISTINCT u.id,u.full_name,u.status FROM team_memberships m JOIN users u ON u.id=m.user_id
                WHERE m.organization_id=? AND u.organization_id=m.organization_id AND m.team_id=?
                  AND m.status='ACTIVE' AND u.status='ACTIVE' AND u.archived_at IS NULL
                  AND m.joined_at<=? AND (m.left_at IS NULL OR m.left_at>?)
                ORDER BY u.full_name,u.id
                """, (r, n) -> new TeamResponse.Member(r.getObject("id",UUID.class),r.getString("full_name"),r.getString("status")),
                org,team,Timestamp.from(now),Timestamp.from(now));
    }
    public record Scope(String sql, List<Object> args) {}
    public Scope scope(ActorContext actor) {
        List<Object> args = new ArrayList<>(List.of(actor.organizationId()));
        if (actor.hasRole(RoleCode.ADMIN)) return new Scope("organization_id=?",args);
        Set<UUID> ids = actor.hasRole(RoleCode.MANAGER) ? actor.managedTeamIds() : actor.teamIds();
        if (ids.isEmpty()) return new Scope("organization_id=? AND 1=0",args);
        args.addAll(ids);
        return new Scope("organization_id=? AND id IN (" + FoundationAccess.placeholders(ids.size()) + ")",args);
    }
    public long count(Scope scope) { return jdbc.queryForObject("SELECT count(*) FROM teams WHERE " + scope.sql(),Long.class,scope.args().toArray()); }
    public List<TeamResponse> list(Scope scope, int page, int size, Instant now) {
        List<Object> args = new ArrayList<>(scope.args()); args.add(size); args.add((long)page*size);
        var ids = jdbc.queryForList("SELECT id FROM teams WHERE " + scope.sql() + " ORDER BY name,id LIMIT ? OFFSET ?",UUID.class,args.toArray());
        UUID org = (UUID)scope.args().getFirst();
        return ids.stream().map(id -> find(org,id,now).orElseThrow()).toList();
    }
}
