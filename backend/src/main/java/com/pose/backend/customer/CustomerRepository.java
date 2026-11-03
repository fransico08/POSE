package com.pose.backend.customer;

import com.pose.backend.crm.CrmJdbc;
import com.pose.backend.crm.ContactInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.time.Instant;
import java.sql.Timestamp;

@Repository
public class CustomerRepository {
    private final JdbcTemplate jdbc;
    private static final String FROM=" FROM customers c JOIN users u ON u.id=c.owner_user_id AND u.organization_id=c.organization_id LEFT JOIN offerings o ON o.id=c.related_offering_id AND o.organization_id=c.organization_id ";
    private static final String SELECT="SELECT c.*,u.full_name AS owner_name,o.code AS offering_code,o.name AS offering_name";
    private static final RowMapper<CustomerResponse> MAPPER=(r,n)->new CustomerResponse(r.getObject("id",UUID.class),r.getString("display_name"),r.getString("email"),r.getString("phone"),r.getString("customer_type"),r.getString("address"),r.getString("notes"),r.getObject("owner_user_id",UUID.class),r.getString("owner_name"),r.getObject("related_offering_id",UUID.class),r.getString("offering_code"),r.getString("offering_name"),r.getString("status"),CrmJdbc.instant(r,"created_at"),CrmJdbc.instant(r,"updated_at"));
    public CustomerRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public long count(String where,List<Object> args) { return jdbc.queryForObject("SELECT count(*)"+FROM+" WHERE "+where,Long.class,args.toArray()); }
    public List<CustomerResponse> list(String where,List<Object> args,int page,int size) {
        var a=new ArrayList<>(args); a.add(size); a.add((long)page*size);
        return jdbc.query(SELECT+FROM+" WHERE "+where+" ORDER BY c.updated_at DESC,c.id LIMIT ? OFFSET ?",MAPPER,a.toArray());
    }
    public Optional<CustomerResponse> find(String where,List<Object> args,UUID id) {
        var a=new ArrayList<>(args); a.add(id);
        return jdbc.query(SELECT+FROM+" WHERE "+where+" AND c.id=?",MAPPER,a.toArray()).stream().findFirst();
    }
    public void insert(UUID id,UUID organization,UUID owner,String name,ContactInput contact,String type,String address,String notes,UUID offering,Instant now) {
        jdbc.update("""
                INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,phone,normalized_phone,status,customer_type,address,notes,related_offering_id,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,'ACTIVE',?,?,?,?,?,?)
                """,id,organization,owner,name,contact.email(),contact.normalizedEmail(),contact.phone(),contact.normalizedPhone(),type,address,notes,offering,Timestamp.from(now),Timestamp.from(now));
    }
    public void insertInitialAssignment(UUID id,UUID organization,UUID customer,UUID owner,UUID actor,Instant now) {
        jdbc.update("""
                INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at)
                VALUES (?,?,?,?,'SALES',?,'ACTIVE',?,'INITIAL',?)
                """,id,organization,customer,owner,Timestamp.from(now),actor,Timestamp.from(now));
    }
    public void update(UUID organization,UUID id,String name,ContactInput contact,String type,String address,String notes,UUID offering,Instant now) {
        jdbc.update("""
                UPDATE customers SET display_name=?,email=?,normalized_email=?,phone=?,normalized_phone=?,customer_type=?,address=?,notes=?,related_offering_id=?,updated_at=?
                WHERE organization_id=? AND id=?
                """,name,contact.email(),contact.normalizedEmail(),contact.phone(),contact.normalizedPhone(),type,address,notes,offering,Timestamp.from(now),organization,id);
    }
}
