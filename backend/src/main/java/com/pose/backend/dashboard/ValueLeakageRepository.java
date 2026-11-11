package com.pose.backend.dashboard;

import com.pose.backend.dashboard.DashboardRepository.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Queries only the facts needed for DEC-21; authorization predicates are supplied by services. */
@Repository
public class ValueLeakageRepository {
    private final JdbcTemplate jdbc;
    public ValueLeakageRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private record Query(String where,List<Object> args) {}
    private static Timestamp ts(Instant time) { return Timestamp.from(time); }

    private Query leads(Scope leads,Scope interactions,Instant asOf,long hours) {
        var args=new ArrayList<Object>(leads.args()); args.add(ts(asOf.minusSeconds(hours*3600)));
        args.addAll(interactions.args()); args.add(ts(asOf));
        return new Query(leads.where()+" AND l.status IN ('NEW','QUALIFIED') AND l.created_at<=?"
                +" AND NOT EXISTS (SELECT 1 FROM interactions i WHERE i.organization_id=l.organization_id AND i.lead_id=l.id AND ("
                +interactions.where()+") AND i.occurred_at<=?)",args);
    }
    public long overdueLeadCount(Scope leads,Scope interactions,Instant asOf,long hours) {
        Query q=leads(leads,interactions,asOf,hours);
        return jdbc.queryForObject("SELECT count(*) FROM leads l WHERE "+q.where(),Long.class,q.args().toArray());
    }
    public List<Map<String,Object>> overdueLeads(Scope leads,Scope interactions,Instant asOf,long hours,int limit) {
        Query q=leads(leads,interactions,asOf,hours); var args=new ArrayList<>(q.args()); args.add(limit);
        return jdbc.queryForList("SELECT l.id,l.display_name AS title,l.owner_user_id,u.full_name AS owner_name,l.created_at FROM leads l JOIN users u ON u.id=l.owner_user_id AND u.organization_id=l.organization_id WHERE "+q.where()+" ORDER BY l.created_at,l.id LIMIT ?",args.toArray());
    }

    private Query opportunities(Scope opportunities,Scope interactions,Instant asOf,long days) {
        var args=new ArrayList<Object>(opportunities.args()); args.addAll(interactions.args());
        args.add(ts(asOf.minusSeconds(days*86400))); args.add(ts(asOf));
        return new Query(opportunities.where()+" AND op.stage IN ('NEW','QUALIFIED','PROPOSAL')"
                +" AND (op.customer_id IS NULL OR c.archived_at IS NULL) AND (op.lead_id IS NULL OR l.archived_at IS NULL)"
                +" AND NOT EXISTS (SELECT 1 FROM interactions i WHERE i.organization_id=op.organization_id"
                +" AND (i.opportunity_id=op.id OR (op.customer_id IS NOT NULL AND i.customer_id=op.customer_id) OR (op.lead_id IS NOT NULL AND i.lead_id=op.lead_id))"
                +" AND ("+interactions.where()+") AND i.occurred_at>? AND i.occurred_at<=?)",args);
    }
    private static final String OP_FROM=" FROM opportunities op JOIN users u ON u.id=op.owner_user_id AND u.organization_id=op.organization_id LEFT JOIN customers c ON c.id=op.customer_id AND c.organization_id=op.organization_id LEFT JOIN leads l ON l.id=op.lead_id AND l.organization_id=op.organization_id WHERE ";
    public List<Map<String,Object>> idleValues(Scope opportunities,Scope interactions,Instant asOf,long days) {
        Query q=opportunities(opportunities,interactions,asOf,days);
        return jdbc.queryForList("SELECT op.currency_code,COALESCE(sum(op.expected_value),0) AS amount,count(*) AS total"+OP_FROM+q.where()+" GROUP BY op.currency_code ORDER BY op.currency_code",q.args().toArray());
    }
    public List<Map<String,Object>> idleOpportunities(Scope opportunities,Scope interactions,Instant asOf,long days,int limit) {
        Query q=opportunities(opportunities,interactions,asOf,days);
        var args=new ArrayList<Object>(interactions.args());args.add(ts(asOf));args.addAll(q.args());args.add(limit);
        return jdbc.queryForList("SELECT op.id,op.title,op.owner_user_id,u.full_name AS owner_name,op.expected_value,op.currency_code,op.created_at,"
                +"(SELECT max(i.occurred_at) FROM interactions i WHERE i.organization_id=op.organization_id"
                +" AND (i.opportunity_id=op.id OR (op.customer_id IS NOT NULL AND i.customer_id=op.customer_id) OR (op.lead_id IS NOT NULL AND i.lead_id=op.lead_id))"
                +" AND ("+interactions.where()+") AND i.occurred_at<=?) AS last_activity"
                +OP_FROM+q.where()+" ORDER BY CASE WHEN op.expected_value IS NULL THEN 1 ELSE 0 END,op.expected_value DESC,op.id LIMIT ?",args.toArray());
    }

    private record HandoverQuery(String from,String where,List<Object> args) {}
    private HandoverQuery handovers(UUID org,Scope customers,Scope interactions,Instant asOf,Instant start,Instant end,boolean today) {
        var args=new ArrayList<Object>(); args.add(org);
        String period;
        if(today) { period="h.reviewed_at<=?"; args.add(ts(asOf)); }
        else { period="h.reviewed_at>=? AND h.reviewed_at<?"; args.add(ts(start)); args.add(ts(end)); }
        args.addAll(customers.args()); args.addAll(interactions.args()); args.add(ts(asOf));
        String from=" FROM (SELECT h.*,row_number() OVER (PARTITION BY h.customer_id ORDER BY h.reviewed_at DESC,h.id DESC) AS rn FROM handover_requests h WHERE h.organization_id=? AND h.handover_type='SALES' AND h.status='APPROVED' AND "+period+") h JOIN customers c ON c.id=h.customer_id AND c.organization_id=h.organization_id JOIN users u ON u.id=h.target_user_id AND u.organization_id=h.organization_id WHERE ";
        String where=customers.where()+" AND h.rn=1"+(today?" AND c.owner_user_id=h.target_user_id":"")
                +" AND NOT EXISTS (SELECT 1 FROM interactions i WHERE i.organization_id=h.organization_id AND i.customer_id=h.customer_id AND i.recorded_by_user_id=h.target_user_id AND ("+interactions.where()+") AND i.occurred_at>=h.reviewed_at AND i.occurred_at<=?)";
        return new HandoverQuery(from,where,args);
    }
    public long handoverTotal(UUID org,Scope customers,Instant start,Instant end) {
        var args=new ArrayList<Object>();args.add(org);args.add(ts(start));args.add(ts(end));args.addAll(customers.args());
        return jdbc.queryForObject("SELECT count(*) FROM (SELECT h.customer_id,h.target_user_id,row_number() OVER (PARTITION BY h.customer_id ORDER BY h.reviewed_at DESC,h.id DESC) AS rn FROM handover_requests h WHERE h.organization_id=? AND h.handover_type='SALES' AND h.status='APPROVED' AND h.reviewed_at>=? AND h.reviewed_at<?) h JOIN customers c ON c.id=h.customer_id WHERE "+customers.where()+" AND h.rn=1",Long.class,args.toArray());
    }
    public long uncontactedCount(UUID org,Scope customers,Scope interactions,Instant asOf,Instant start,Instant end) {
        HandoverQuery q=handovers(org,customers,interactions,asOf,start,end,false);
        return jdbc.queryForObject("SELECT count(*)"+q.from()+q.where(),Long.class,q.args().toArray());
    }
    public List<Map<String,Object>> uncontactedHandovers(UUID org,Scope customers,Scope interactions,Instant asOf,int limit) {
        HandoverQuery q=handovers(org,customers,interactions,asOf,null,null,true);var args=new ArrayList<>(q.args());args.add(limit);
        return jdbc.queryForList("SELECT c.id,c.display_name AS title,h.target_user_id AS owner_user_id,u.full_name AS owner_name,h.reviewed_at"+q.from()+q.where()+" ORDER BY h.reviewed_at,c.id LIMIT ?",args.toArray());
    }
    public List<Map<String,Object>> suggestions(UUID org,UUID viewer,boolean global,boolean manager,UUID owner,Instant start,Instant end,Instant now) {
        var args=new ArrayList<Object>();args.add(org);args.add(ts(start));args.add(ts(end));
        String scope="";
        if(!global) {
            scope=" AND (i.actor_user_id=?";args.add(viewer);
            if(manager) {scope+=" OR EXISTS (SELECT 1 FROM team_memberships m JOIN teams t ON t.id=m.team_id AND t.organization_id=m.organization_id WHERE m.organization_id=i.organization_id AND m.user_id=i.actor_user_id AND m.status='ACTIVE' AND m.joined_at<=? AND (m.left_at IS NULL OR m.left_at>?) AND t.status='ACTIVE' AND t.manager_user_id=?)";args.add(ts(now));args.add(ts(now));args.add(viewer);}
            scope+=")";
        }
        if(owner!=null){scope+=" AND i.actor_user_id=?";args.add(owner);}
        return jdbc.queryForList("SELECT s.kind,count(*) AS total,count(CASE WHEN s.status IN ('ACCEPTED','EDITED') THEN 1 END) AS accepted FROM ai_suggestions s JOIN ai_invocations i ON i.organization_id=s.organization_id AND i.id=s.invocation_id WHERE s.organization_id=? AND s.reviewed_at>=? AND s.reviewed_at<? AND s.status IN ('ACCEPTED','EDITED','REJECTED')"+scope+" GROUP BY s.kind ORDER BY s.kind",args.toArray());
    }
    public List<Map<String,Object>> tasks(Scope tasks,Instant start,Instant end,boolean overdue,int limit) {
        var args=new ArrayList<Object>(tasks.args());
        String time=overdue?"t.due_at<?":"t.due_at>=? AND t.due_at<?";
        if(overdue)args.add(ts(end));else {args.add(ts(start));args.add(ts(end));}args.add(limit);
        return jdbc.queryForList("SELECT t.id,t.title,t.owner_user_id,u.full_name AS owner_name,t.due_at FROM crm_tasks t JOIN users u ON u.id=t.owner_user_id AND u.organization_id=t.organization_id WHERE "+tasks.where()+" AND t.status IN ('OPEN','IN_PROGRESS') AND "+time+" ORDER BY t.due_at,t.id LIMIT ?",args.toArray());
    }
}
