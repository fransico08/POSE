package com.pose.backend.crm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
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
        if (lead) where.append(" AND archived_at IS NULL");
        if (exclude!=null) { where.append(" AND id<>?"); args.add(exclude); }
        List<String> terms=new ArrayList<>();
        if (email!=null) { terms.add("normalized_email=?"); args.add(email); }
        if (phone!=null) { terms.add("normalized_phone=?"); args.add(phone); }
        if (terms.isEmpty()) return List.of();
        where.append(" AND (").append(String.join(" OR ",terms)).append(")");
        return jdbc.queryForList("SELECT id FROM "+table+" WHERE "+where+" ORDER BY id",UUID.class,args.toArray());
    }
    public static void search(StringBuilder where, List<Object> args, String search, String alias) {
        if (search==null || search.isBlank()) return;
        String input=search.strip(); if(input.length()>200) throw com.pose.backend.api.JsonInput.invalid("search","Tối đa 200 ký tự.");
        String value="%"+input.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        where.append(" AND (lower(").append(alias).append(".display_name) LIKE ? ESCAPE '!' OR lower(")
                .append(alias).append(".email) LIKE ? ESCAPE '!' OR ").append(alias).append(".phone LIKE ? ESCAPE '!')");
        args.addAll(List.of(value,value,value));
    }
}
