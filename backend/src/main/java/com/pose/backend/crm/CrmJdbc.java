package com.pose.backend.crm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.identity.FoundationAccess;
import com.pose.backend.identity.PermissionCode;
import com.pose.backend.identity.RoleCode;
import com.pose.backend.salesassignment.SalesAssignmentCandidateResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CrmJdbc {
    private final JdbcTemplate jdbc;
    public CrmJdbc(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public static Instant instant(ResultSet rs, String field) throws SQLException {
        Timestamp value=rs.getTimestamp(field); return value==null?null:value.toInstant();
    }
    public List<UUID> duplicateIds(String table, UUID org, String email, String phone, UUID exclude, boolean lead) {
        StringBuilder where=new StringBuilder("organization_id=?"); List<Object> args=new ArrayList<>(); args.add(org);
        // Closed Leads do not block a returning contact; only open, non-archived Leads count as duplicates.
        if (lead) where.append(" AND archived_at IS NULL AND status NOT IN ('DISQUALIFIED','CONVERTED')");
        if (exclude!=null) { where.append(" AND id<>?"); args.add(exclude); }
        List<String> terms=new ArrayList<>();
        if (email!=null) { terms.add("normalized_email=?"); args.add(email); }
        if (phone!=null) { terms.add("normalized_phone=?"); args.add(phone); }
        if (terms.isEmpty()) return List.of();
        where.append(" AND (").append(String.join(" OR ",terms)).append(")");
        return jdbc.queryForList("SELECT id FROM "+table+" WHERE "+where+" ORDER BY id",UUID.class,args.toArray());
    }
    public List<SalesAssignmentCandidateResponse> owners(ActorContext actor, CrmAccess access, boolean lead) {
        PermissionCode permission = lead ? PermissionCode.LEAD_READ : PermissionCode.CUSTOMER_READ;
        if ((!actor.hasRole(RoleCode.ADMIN) && !actor.hasRole(RoleCode.MANAGER)) || !actor.hasPermission(permission)) FoundationAccess.denyPermission();
        CrmAccess.Scope scope = access.scope(actor, lead, false);
        String table = lead ? "leads l" : "customers c", alias = lead ? "l" : "c";
        String sql = "SELECT id,full_name FROM (SELECT DISTINCT u.id,u.full_name FROM " + table
                + " JOIN users u ON u.id=" + alias + ".owner_user_id AND u.organization_id=" + alias + ".organization_id WHERE "
                + scope.where() + ") owners ORDER BY lower(full_name),full_name,id";
        return jdbc.query(sql, (rs, row) -> new SalesAssignmentCandidateResponse(rs.getObject("id", UUID.class), rs.getString("full_name")), scope.args().toArray());
    }
    private static String like(String value) { return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"; }
    public static void search(StringBuilder where, List<Object> args, String search, String alias) {
        if (search==null || search.isBlank()) return;
        String input=search.strip(); if(input.length()>200) throw com.pose.backend.api.JsonInput.invalid("search","Tối đa 200 ký tự.");
        String value=like(input.toLowerCase(Locale.ROOT));
        where.append(" AND (lower(").append(alias).append(".display_name) LIKE ? ESCAPE '!' OR lower(")
                .append(alias).append(".email) LIKE ? ESCAPE '!'");
        args.addAll(List.of(value,value));
        if (input.matches("[+0-9\\s.()\\-]+") && input.matches(".*[0-9].*")) {
            String phone = input.replaceAll("[\\s.()\\-]", "");
            if (phone.startsWith("0")) phone = "+84" + phone.substring(1);
            else if (phone.startsWith("84")) phone = "+" + phone;
            where.append(" OR ").append(alias).append(".normalized_phone LIKE ? ESCAPE '!'");
            args.add(like(phone));
        }
        where.append(")");
    }
}
