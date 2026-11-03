package com.pose.backend.lead;

import com.pose.backend.crm.CrmJdbc;
import com.pose.backend.crm.ContactInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.time.Instant;
import java.sql.Timestamp;

@Repository
public class LeadRepository {
    private final JdbcTemplate jdbc;
    private static final String FROM=" FROM leads l JOIN users u ON u.id=l.owner_user_id AND u.organization_id=l.organization_id JOIN offerings o ON o.id=l.offering_id AND o.organization_id=l.organization_id ";
    private static final RowMapper<LeadResponse> MAPPER=(r,n)->new LeadResponse(r.getObject("id",UUID.class),r.getString("display_name"),r.getString("email"),r.getString("phone"),r.getString("source"),r.getString("notes"),r.getObject("owner_user_id",UUID.class),r.getString("owner_name"),r.getObject("offering_id",UUID.class),r.getString("offering_code"),r.getString("offering_name"),r.getString("status"),CrmJdbc.instant(r,"qualified_at"),r.getObject("converted_customer_id",UUID.class),CrmJdbc.instant(r,"converted_at"),r.getString("disqualification_reason"),CrmJdbc.instant(r,"created_at"),CrmJdbc.instant(r,"updated_at"));
    private static final String SELECT="SELECT l.*,u.full_name AS owner_name,o.code AS offering_code,o.name AS offering_name";
    public LeadRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public long count(String where,List<Object> args) { return jdbc.queryForObject("SELECT count(*)"+FROM+" WHERE "+where,Long.class,args.toArray()); }
    public List<LeadResponse> list(String where,List<Object> args,int page,int size) {
        var a=new ArrayList<>(args); a.add(size); a.add((long)page*size);
        return jdbc.query(SELECT+FROM+" WHERE "+where+" ORDER BY l.updated_at DESC,l.id LIMIT ? OFFSET ?",MAPPER,a.toArray());
    }
    public Optional<LeadResponse> find(String where,List<Object> args,UUID id) {
        var a=new ArrayList<>(args); a.add(id);
        return jdbc.query(SELECT+FROM+" WHERE "+where+" AND l.id=?",MAPPER,a.toArray()).stream().findFirst();
    }
    public void insert(UUID id,UUID organization,UUID owner,UUID offering,String name,ContactInput contact,String source,String notes,Instant now) {
        jdbc.update("""
                INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,phone,normalized_phone,status,source,notes,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,'NEW',?,?,?,?)
                """,id,organization,owner,offering,name,contact.email(),contact.normalizedEmail(),contact.phone(),contact.normalizedPhone(),source,notes,Timestamp.from(now),Timestamp.from(now));
    }
    public void update(UUID organization,UUID id,String name,ContactInput contact,String source,String notes,UUID offering,Instant now) {
        jdbc.update("""
                UPDATE leads SET display_name=?,email=?,normalized_email=?,phone=?,normalized_phone=?,source=?,notes=?,offering_id=?,updated_at=?
                WHERE organization_id=? AND id=?
                """,name,contact.email(),contact.normalizedEmail(),contact.phone(),contact.normalizedPhone(),source,notes,offering,Timestamp.from(now),organization,id);
    }
    public void transition(UUID organization,UUID id,String status,Instant qualifiedAt,String reason,Instant now) {
        jdbc.update("UPDATE leads SET status=?,qualified_at=?,disqualification_reason=?,updated_at=? WHERE organization_id=? AND id=?",
                status,qualifiedAt==null?null:Timestamp.from(qualifiedAt),reason,Timestamp.from(now),organization,id);
    }
}
