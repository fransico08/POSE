package com.pose.backend.customer360;

import com.pose.backend.crm.*;
import com.pose.backend.customer.*;
import com.pose.backend.identity.*;
import com.pose.backend.interaction.*;
import com.pose.backend.opportunity.OpportunityService;
import com.pose.backend.task.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static com.pose.backend.customer360.Customer360Response.*;

@Service
public class Customer360Service {
    private final JdbcTemplate jdbc;
    private final CrmWorkAccess work;
    private final CrmAccess crm;
    private final CustomerRepository customers;
    private final OpportunityService opportunities;
    private final InteractionService interactions;
    private final TaskService tasks;
    private final Clock clock;
    public Customer360Service(JdbcTemplate jdbc, CrmWorkAccess work, CrmAccess crm, CustomerRepository customers,
                              OpportunityService opportunities, InteractionService interactions, TaskService tasks, Clock clock) {
        this.jdbc=jdbc; this.work=work; this.crm=crm; this.customers=customers;
        this.opportunities=opportunities; this.interactions=interactions; this.tasks=tasks; this.clock=clock;
    }

    @Transactional(readOnly=true)
    public Customer360Response profile(ActorContext actor, UUID id) {
        if (!actor.hasPermission(PermissionCode.CUSTOMER_360_READ)) FoundationAccess.denyPermission();
        var scope=work.subjectScope(actor,PermissionCode.CUSTOMER_360_READ,false,"v","v.id","CAST(NULL AS UUID)");
        List<Object> args=new ArrayList<>();args.add(actor.organizationId());args.addAll(scope.args());
        CustomerResponse c=customers.find("c.organization_id=? AND c.archived_at IS NULL AND EXISTS (SELECT 1 FROM customers v WHERE v.organization_id=c.organization_id AND v.id=c.id AND "+scope.where()+")",args,id)
                .orElseGet(()->{FoundationAccess.denyScope();return null;});
        boolean metadata=actor.hasRole(RoleCode.DATA_STAFF);
        if(metadata) c=new CustomerResponse(c.id(),c.displayName(),null,null,c.customerType(),null,null,c.ownerUserId(),c.ownerName(),c.relatedOfferingId(),c.offeringCode(),c.offeringName(),c.status(),c.createdAt(),c.updatedAt());
        Instant now=clock.instant();
        List<Name> teams=jdbc.query("""
                SELECT DISTINCT t.id,t.name FROM teams t JOIN team_memberships tm ON tm.team_id=t.id AND tm.organization_id=t.organization_id
                WHERE t.organization_id=? AND t.status='ACTIVE' AND tm.status='ACTIVE' AND tm.user_id=?
                  AND tm.joined_at<=? AND (tm.left_at IS NULL OR tm.left_at>?) ORDER BY t.name,t.id
                """,(r,n)->new Name(r.getObject("id",UUID.class),r.getString("name")),actor.organizationId(),c.ownerUserId(),Timestamp.from(now),Timestamp.from(now));
        List<Name> care=jdbc.query("""
                SELECT DISTINCT u.id,u.full_name FROM customer_assignments ca JOIN users u ON u.id=ca.user_id AND u.organization_id=ca.organization_id
                WHERE ca.organization_id=? AND ca.customer_id=? AND ca.assignment_type='CARE' AND ca.status='ACTIVE'
                  AND ca.effective_from<=? AND (ca.effective_to IS NULL OR ca.effective_to>=?) ORDER BY u.full_name,u.id
                """,(r,n)->new Name(r.getObject("id",UUID.class),r.getString("full_name")),actor.organizationId(),id,Timestamp.from(now),Timestamp.from(now));
        long sourceCount=jdbc.queryForObject("SELECT count(*) FROM source_record_links WHERE organization_id=? AND subject_type='CUSTOMER' AND subject_id=? AND status='ACTIVE'",Long.class,actor.organizationId(),id);
        List<String> systems=jdbc.queryForList("""
                SELECT DISTINCT ss.code FROM source_record_links sr JOIN source_systems ss ON ss.id=sr.source_system_id AND ss.organization_id=sr.organization_id
                WHERE sr.organization_id=? AND sr.subject_type='CUSTOMER' AND sr.subject_id=? AND sr.status='ACTIVE' ORDER BY ss.code
                """,String.class,actor.organizationId(),id);
        return new Customer360Response(new Customer(c,new Name(c.ownerUserId(),c.ownerName()),teams,care),new Sources(sourceCount,systems),
                leads(actor,id),opportunities(actor,id,metadata),interactions(actor,id,metadata),tasks(actor,id,now,metadata),
                feedback(actor,id),support(actor,id,now),now);
    }

    private Leads leads(ActorContext actor,UUID customer) {
        if(!actor.hasPermission(PermissionCode.LEAD_READ)) return new Leads(true,null,List.of());
        try {
            var s=crm.scope(actor,true,false); var args=new ArrayList<>(s.args());args.add(customer);
            List<Lead> rows=jdbc.query("SELECT l.id,l.display_name,l.status,l.converted_at FROM leads l WHERE "+s.where()+" AND l.converted_customer_id=? ORDER BY l.converted_at DESC,l.id",
                    (r,n)->new Lead(r.getObject("id",UUID.class),r.getString("display_name"),r.getString("status"),CrmJdbc.instant(r,"converted_at")),args.toArray());
            return new Leads(false,(long)rows.size(),rows);
        } catch(AuthorizationDeniedException e) { return new Leads(true,null,List.of()); }
    }
    private Opportunities opportunities(ActorContext actor,UUID customer,boolean metadata) {
        if(!actor.hasPermission(PermissionCode.OPPORTUNITY_READ)) return new Opportunities(true,null,null,null,List.of());
        try {
            var s=opportunities.readScope(actor);var args=new ArrayList<>(s.args());args.add(customer);
            String from=" FROM opportunities op LEFT JOIN customers c ON c.id=op.customer_id AND c.organization_id=op.organization_id LEFT JOIN leads l ON l.id=op.lead_id AND l.organization_id=op.organization_id WHERE "+s.where()+" AND op.customer_id=?";
            Map<String,Long> counts=new LinkedHashMap<>();for(String stage:List.of("NEW","QUALIFIED","PROPOSAL","WON","LOST","CANCELLED")) counts.put(stage,0L);
            jdbc.query("SELECT op.stage,count(*) AS total"+from+" GROUP BY op.stage",(org.springframework.jdbc.core.RowCallbackHandler) r->counts.put(r.getString("stage"),r.getLong("total")),args.toArray());
            var totals=jdbc.queryForMap("SELECT COALESCE(sum(CASE WHEN op.stage IN ('NEW','QUALIFIED','PROPOSAL') THEN op.expected_value ELSE 0 END),0) AS open_value,COALESCE(sum(CASE WHEN op.stage='WON' THEN op.final_value ELSE 0 END),0) AS won_value"+from,args.toArray());
            List<Opportunity> rows=jdbc.query("SELECT op.id,op.title,op.stage,op.expected_value,op.expected_close_date"+from+" ORDER BY op.updated_at DESC,op.id LIMIT 10",
                    (r,n)->new Opportunity(r.getObject("id",UUID.class),metadata?null:r.getString("title"),r.getString("stage"),metadata||r.getBigDecimal("expected_value")==null?null:r.getBigDecimal("expected_value").toPlainString(),r.getObject("expected_close_date",LocalDate.class)),args.toArray());
            return new Opportunities(false,counts,metadata?null:totals.get("OPEN_VALUE").toString(),metadata?null:totals.get("WON_VALUE").toString(),rows);
        } catch(AuthorizationDeniedException e) { return new Opportunities(true,null,null,null,List.of()); }
    }
    private Interactions interactions(ActorContext actor,UUID customer,boolean metadata) {
        if(!actor.hasPermission(PermissionCode.INTERACTION_READ)) return new Interactions(true,null,null,List.of());
        try {
            var page=interactions.list(actor,0,10,customer,null,null,null,null,null,null,null);
            var items=metadata?page.items().stream().map(i->i.redacted().withoutOpportunityTitle()).toList():page.items();
            return new Interactions(false,page.totalItems(),items.isEmpty()?null:items.getFirst().occurredAt(),items);
        } catch(AuthorizationDeniedException e) { return new Interactions(true,null,null,List.of()); }
    }
    private Tasks tasks(ActorContext actor,UUID customer,Instant now,boolean metadata) {
        if(!actor.hasPermission(PermissionCode.TASK_MANAGE)) return new Tasks(true,null,null,List.of());
        try {
            var s=work.taskScope(actor,false);var args=new ArrayList<Object>();args.add(actor.organizationId());args.addAll(s.args());args.add(customer);
            String where="t.organization_id=? AND "+s.where()+" AND t.customer_id=?";
            Map<String,Long> counts=new LinkedHashMap<>();for(String status:List.of("OPEN","IN_PROGRESS","DONE","CANCELLED")) counts.put(status,0L);
            jdbc.query("SELECT t.status,count(*) AS total FROM crm_tasks t WHERE "+where+" GROUP BY t.status",(org.springframework.jdbc.core.RowCallbackHandler) r->counts.put(r.getString("status"),r.getLong("total")),args.toArray());
            var overdueArgs=new ArrayList<>(args);overdueArgs.add(Timestamp.from(now));
            long overdue=jdbc.queryForObject("SELECT count(*) FROM crm_tasks t WHERE "+where+" AND t.status IN ('OPEN','IN_PROGRESS') AND t.due_at<?",Long.class,overdueArgs.toArray());
            var open=tasks.list(actor,0,10,customer,null,null,null,"OPEN",null,null,null);
            var progress=tasks.list(actor,0,10,customer,null,null,null,"IN_PROGRESS",null,null,null);
            List<TaskResponse> items=new ArrayList<>(open.items());items.addAll(progress.items());
            items.sort(Comparator.comparing(TaskResponse::dueAt).thenComparing(TaskResponse::id));
            if(metadata) items.replaceAll(t->t.redacted().withoutOpportunityTitle());
            return new Tasks(false,counts,overdue,List.copyOf(items.subList(0,Math.min(10,items.size()))));
        } catch(AuthorizationDeniedException e) { return new Tasks(true,null,null,List.of()); }
    }
    /** Feedback shares the Interaction row of the role matrix: interaction.read, subject scope and note visibility. */
    private Metric feedback(ActorContext actor,UUID customer) {
        if(!actor.hasPermission(PermissionCode.INTERACTION_READ)) return new Metric(true,null,null);
        try {
            var s=work.subjectScope(actor,PermissionCode.INTERACTION_READ,false,"f","f.customer_id","f.lead_id");
            List<Object> args=new ArrayList<>();args.add(actor.organizationId());args.addAll(s.args());args.add(customer);
            var row=jdbc.queryForMap("SELECT count(*) AS total,avg(f.rating) AS average_rating FROM feedbacks f WHERE f.organization_id=? AND "+s.where()
                    +interactions.careVisibleOnlyPredicate(actor,"f")+" AND f.customer_id=?",args.toArray());
            Object average=row.get("average_rating");
            Double rounded=average==null?null:Math.round(((Number)average).doubleValue()*100)/100.0;
            return new Metric(false,((Number)row.get("total")).longValue(),rounded);
        } catch(AuthorizationDeniedException e) { return new Metric(true,null,null); }
    }
    private Metric support(ActorContext actor,UUID customer,Instant now) {
        if(!actor.hasPermission(PermissionCode.SUPPORT_REQUEST_MANAGE)) return new Metric(true,null,null);
        List<String> roles=new ArrayList<>();List<Object> args=new ArrayList<>();
        args.add(actor.organizationId());args.add(customer);
        if(work.hasRolePermission(actor,RoleCode.ADMIN,PermissionCode.SUPPORT_REQUEST_MANAGE)) roles.add("1=1");
        if(work.hasRolePermission(actor,RoleCode.CUSTOMER_CARE,PermissionCode.SUPPORT_REQUEST_MANAGE)) {
            roles.add("sr.assigned_to_user_id=?");args.add(actor.userId());
        }
        if(work.hasRolePermission(actor,RoleCode.MANAGER,PermissionCode.SUPPORT_REQUEST_MANAGE)) {
            roles.add("EXISTS (SELECT 1 FROM team_memberships tm JOIN teams t ON t.id=tm.team_id AND t.organization_id=tm.organization_id WHERE tm.organization_id=sr.organization_id AND tm.user_id=sr.assigned_to_user_id AND tm.status='ACTIVE' AND tm.joined_at<=? AND (tm.left_at IS NULL OR tm.left_at>?) AND t.status='ACTIVE' AND t.manager_user_id=?)");
            args.add(Timestamp.from(now));args.add(Timestamp.from(now));args.add(actor.userId());
        }
        if(roles.isEmpty()) return new Metric(true,null,null);
        long count=jdbc.queryForObject("SELECT count(*) FROM support_requests sr WHERE sr.organization_id=? AND sr.customer_id=? AND sr.status NOT IN ('RESOLVED','CLOSED') AND ("+String.join(" OR ",roles)+")",Long.class,args.toArray());
        return new Metric(false,count,null);
    }
}
