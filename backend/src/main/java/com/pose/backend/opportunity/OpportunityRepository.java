package com.pose.backend.opportunity;

import com.pose.backend.crm.CrmJdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class OpportunityRepository {
    private final JdbcTemplate jdbc;
    public OpportunityRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String FROM=" FROM opportunities op JOIN users u ON u.id=op.owner_user_id AND u.organization_id=op.organization_id LEFT JOIN customers c ON c.id=op.customer_id AND c.organization_id=op.organization_id LEFT JOIN leads l ON l.id=op.lead_id AND l.organization_id=op.organization_id ";
    private static String money(ResultSet rs,String column) throws SQLException { BigDecimal value=rs.getBigDecimal(column); return value==null?null:value.toPlainString(); }
    private OpportunityResponse map(ResultSet rs,int n) throws SQLException {
        UUID id=rs.getObject("id",UUID.class), customer=rs.getObject("customer_id",UUID.class), lead=rs.getObject("lead_id",UUID.class);
        return new OpportunityResponse(id,rs.getString("title"),rs.getString("stage"),money(rs,"expected_value"),rs.getString("currency_code"),rs.getObject("expected_close_date",LocalDate.class),rs.getString("notes"),
                customer==null?null:new OpportunityResponse.Subject(customer,rs.getString("customer_name")),lead==null?null:new OpportunityResponse.Subject(lead,rs.getString("lead_name")),new OpportunityResponse.Subject(rs.getObject("owner_user_id",UUID.class),rs.getString("owner_name")),
                lines(rs.getObject("organization_id",UUID.class),id),money(rs,"final_value"),rs.getObject("actual_close_date",LocalDate.class),rs.getString("closing_reason"),CrmJdbc.instant(rs,"closed_at"),CrmJdbc.instant(rs,"created_at"),CrmJdbc.instant(rs,"updated_at"));
    }
    private static final String SELECT="SELECT op.*,u.full_name AS owner_name,c.display_name AS customer_name,l.display_name AS lead_name";
    public Optional<OpportunityResponse> find(String where,List<Object> args,UUID id) {
        var a=new ArrayList<>(args); a.add(id);
        return jdbc.query(SELECT+FROM+" WHERE "+where+" AND op.id=?",this::map,a.toArray()).stream().findFirst();
    }
    public long count(String where,List<Object> args) { return jdbc.queryForObject("SELECT count(*)"+FROM+" WHERE "+where,Long.class,args.toArray()); }
    public List<OpportunityResponse> list(String where,List<Object> args,int page,int size) {
        var a=new ArrayList<>(args); a.add(size); a.add((long)page*size);
        return jdbc.query(SELECT+FROM+" WHERE "+where+" ORDER BY op.updated_at DESC,op.id LIMIT ? OFFSET ?",this::map,a.toArray());
    }
    public List<OpportunityResponse.Line> lines(UUID org,UUID id) {
        return jdbc.query("SELECT ol.*,o.code,o.name FROM opportunity_lines ol JOIN offerings o ON o.organization_id=ol.organization_id AND o.id=ol.offering_id WHERE ol.organization_id=? AND ol.opportunity_id=? ORDER BY ol.position",(rs,n)->new OpportunityResponse.Line(rs.getObject("offering_id",UUID.class),rs.getString("code"),rs.getString("name"),money(rs,"quantity"),money(rs,"unit_price"),money(rs,"discount_amount"),money(rs,"line_total")),org,id);
    }
    public void insert(UUID id,UUID org,UUID customer,UUID owner,String title,String stage,LocalDate close,String notes,List<InputLine> lines,Instant now) {
        jdbc.update("INSERT INTO opportunities(id,organization_id,customer_id,offering_id,owner_user_id,title,stage,expected_value,currency_code,expected_close_date,notes,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?, 'VND',?,?,?,?)",id,org,customer,lines.getFirst().offeringId(),owner,title,stage,total(lines),close,notes,Timestamp.from(now),Timestamp.from(now));
        replaceLines(org,id,lines,now);
    }
    public void update(UUID org,UUID id,String title,String stage,LocalDate close,String notes,List<InputLine> lines,Instant now) {
        jdbc.update("UPDATE opportunities SET title=?,stage=?,expected_close_date=?,notes=?,offering_id=?,expected_value=?,updated_at=? WHERE organization_id=? AND id=?",title,stage,close,notes,lines.getFirst().offeringId(),total(lines),Timestamp.from(now),org,id);
        replaceLines(org,id,lines,now);
    }
    private BigDecimal total(List<InputLine> lines) { return lines.stream().map(InputLine::total).reduce(BigDecimal.ZERO,BigDecimal::add); }
    private void replaceLines(UUID org,UUID id,List<InputLine> lines,Instant now) {
        jdbc.update("DELETE FROM opportunity_lines WHERE organization_id=? AND opportunity_id=?",org,id);
        int position=0; for(InputLine line:lines) jdbc.update("INSERT INTO opportunity_lines(id,organization_id,opportunity_id,offering_id,position,quantity,unit_price,discount_amount,line_total,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),org,id,line.offeringId(),++position,line.quantity(),line.price(),line.discount(),line.total(),Timestamp.from(now),Timestamp.from(now));
    }
    public void close(UUID org,UUID id,String stage,BigDecimal finalValue,LocalDate actualDate,String reason,Instant now) {
        jdbc.update("UPDATE opportunities SET stage=?,final_value=?,actual_close_date=?,closing_reason=?,closed_at=?,updated_at=? WHERE organization_id=? AND id=?",stage,finalValue,actualDate,reason,Timestamp.from(now),Timestamp.from(now),org,id);
    }
    public void reopen(UUID org,UUID id,Instant now) { jdbc.update("UPDATE opportunities SET stage='QUALIFIED',final_value=NULL,actual_close_date=NULL,closing_reason=NULL,closed_at=NULL,updated_at=? WHERE organization_id=? AND id=?",Timestamp.from(now),org,id); }
    public record InputLine(UUID offeringId,BigDecimal quantity,BigDecimal price,BigDecimal discount,BigDecimal total) {}
}
