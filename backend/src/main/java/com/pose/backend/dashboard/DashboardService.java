package com.pose.backend.dashboard;

import com.pose.backend.api.JsonInput;
import com.pose.backend.crm.CrmAccess;
import com.pose.backend.crm.CrmWorkAccess;
import com.pose.backend.dashboard.DashboardRepository.Scope;
import com.pose.backend.dashboard.DashboardResponse.*;
import com.pose.backend.identity.*;
import com.pose.backend.interaction.InteractionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class DashboardService {
    private static final List<String> IDS = List.of("A01_TOTAL_LEADS", "A02_LEAD_STATUS_DISTRIBUTION", "A03_QUALIFIED_LEAD_RATE",
            "A04_LEAD_TO_CUSTOMER_CONVERSION", "A05_FIRST_RESPONSE_TIME", "A06_CUSTOMER_ENGAGEMENT_SEGMENTS",
            "B01_OPEN_TASKS", "B02_OVERDUE_OPEN_TASKS", "B03_TASK_PRIORITY_DISTRIBUTION", "B04_TASKS_PER_USER",
            "B05_ON_TIME_COMPLETION_RATE", "C01_TERMINAL_WORKFLOW_SUCCESS_RATE", "C02_FAILED_EXECUTIONS_AND_RETRY",
            "C03_WORKFLOW_CYCLE_TIME", "D01_INTERACTIONS_BY_CHANNEL", "D02_INTERACTIONS_BY_ORG_USER");
    private static final Set<String> PARAMS = Set.of("asOf", "start", "end", "timezone", "ownerUserId", "workflowType", "metricIds");
    private final DashboardRepository repository;
    private final CrmAccess crm;
    private final CrmWorkAccess work;
    private final InteractionService interactions;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DashboardService(DashboardRepository repository, CrmAccess crm, CrmWorkAccess work,
                            InteractionService interactions, JdbcTemplate jdbc, Clock clock) {
        this.repository=repository; this.crm=crm; this.work=work; this.interactions=interactions; this.jdbc=jdbc; this.clock=clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse read(ActorContext actor, MultiValueMap<String, String> parameters) {
        if (!actor.hasPermission(PermissionCode.DASHBOARD_READ)) FoundationAccess.denyPermission();
        for (String key : parameters.keySet()) if (!PARAMS.contains(key)) throw JsonInput.invalid(key, "Tham số không được hỗ trợ.");
        Instant generatedAt=clock.instant(), asOf=parseInstant(single(parameters,"asOf"),"asOf",generatedAt);
        String startText=single(parameters,"start"), endText=single(parameters,"end");
        if ((startText==null)!=(endText==null)) throw JsonInput.invalid(startText==null?"start":"end", "Cần gửi cả start và end.");
        Instant start=parseInstant(startText,"start",asOf.minus(Duration.ofDays(30)));
        Instant end=parseInstant(endText,"end",asOf);
        if (!start.isBefore(end)) throw JsonInput.invalid("end", "End phải sau start.");
        if (Duration.between(start,end).compareTo(Duration.ofDays(366))>0) throw JsonInput.invalid("end", "Khoảng thời gian tối đa 366 ngày.");
        String zone=Optional.ofNullable(single(parameters,"timezone")).orElse("Asia/Ho_Chi_Minh");
        if (!ZoneId.getAvailableZoneIds().contains(zone)) throw JsonInput.invalid("timezone", "Múi giờ IANA không hợp lệ.");
        String ownerText=single(parameters,"ownerUserId"), workflowType=single(parameters,"workflowType");
        UUID owner=ownerText==null?null:uuid(ownerText,"ownerUserId");
        if (owner!=null) requireOwner(actor,owner);
        if (workflowType!=null && (workflowType.isBlank() || workflowType.length()>120)) throw JsonInput.invalid("workflowType", "Mã workflow không hợp lệ.");
        LinkedHashSet<String> selection=new LinkedHashSet<>();
        for (String raw : parameters.getOrDefault("metricIds",List.of())) for (String id : raw.split(",",-1)) {
            if (!IDS.contains(id)) throw JsonInput.invalid("metricIds", "Mã KPI không hợp lệ.");
            selection.add(id);
        }
        boolean all=selection.isEmpty(), global=actor.hasRole(RoleCode.ADMIN)||actor.hasRole(RoleCode.DATA_STAFF);
        boolean sales=actor.hasRole(RoleCode.SALES_REP), manager=actor.hasRole(RoleCode.MANAGER), care=actor.hasRole(RoleCode.CUSTOMER_CARE);
        boolean canA=global || ((manager||sales) && actor.hasPermission(PermissionCode.LEAD_READ));
        boolean canB=global || ((manager||sales||care) && actor.hasPermission(PermissionCode.TASK_MANAGE));
        boolean canD=global || ((manager||sales) && actor.hasPermission(PermissionCode.INTERACTION_READ));
        Map<String,List<Metric>> result=new LinkedHashMap<>();
        if (canA && selected(selection,"A")) {
            Scope lead=global?tenant(actor,"l","l.archived_at IS NULL"):from(crm.scope(actor,true,false));
            Scope customer=global?tenant(actor,"c","c.archived_at IS NULL"):actor.hasPermission(PermissionCode.CUSTOMER_READ)?from(crm.scope(actor,false,false)):null;
            if (owner!=null) { lead=add(lead,"l.owner_user_id=?",owner); if(customer!=null) customer=add(customer,"c.owner_user_id=?",owner); }
            Map<String,Object> leadTotals=repository.leadSummary(lead); BigDecimal total=decimal(leadTotals.get("total"));
            put(result, metric(IDS.get(0),Map.of(),total,"count",null,null,available(total,true),null,asOf,generatedAt));
            List<Map<String,Object>> statuses=repository.leadStatuses(lead);
            if (statuses.isEmpty()) put(result,metric(IDS.get(1),Map.of(),null,"count",null,total,"NOT_AVAILABLE_ZERO_DENOMINATOR",null,asOf,generatedAt));
            for (var row:statuses) { BigDecimal count=decimal(row.get("total")); put(result,metric(IDS.get(1),Map.of("status",row.get("status")),count,"count",count,total,"AVAILABLE_SNAPSHOT",null,asOf,generatedAt)); }
            rate(result,IDS.get(2),leadTotals,"qualified","total","qualified_rate",true,asOf,generatedAt);
            rate(result,IDS.get(3),leadTotals,"converted","total","converted_rate",true,asOf,generatedAt);
            Scope interactionScope=interactionScope(actor,global,owner);
            if (interactionScope==null) {
                put(result,notApplicable(IDS.get(4),"OUTSIDE_ROLE_SCOPE",asOf,generatedAt));
                put(result,notApplicable(IDS.get(5),"OUTSIDE_ROLE_SCOPE",asOf,generatedAt));
            } else {
                // A05's owner filter belongs to the Lead, not the recorder.
                Scope leadInteractions=interactionScope(actor,global,null);
                var response=repository.firstResponse(lead,leadInteractions,start,end);
                BigDecimal population=decimal(response.get("total")), responded=decimal(response.get("responded"));
                BigDecimal mean=decimal(response.get("avg_seconds"));
                String availability=population.signum()==0?"NO_DATA_IN_PERIOD":responded.signum()==0?"NOT_AVAILABLE":"AVAILABLE";
                put(result,metric(IDS.get(4),Map.of("kind","RESPONDED"),mean,"seconds",responded,population,availability,
                        responded.signum()==0 && population.signum()>0?"NO_RESPONSES":null,asOf,generatedAt));
                put(result,metric(IDS.get(4),Map.of("kind","NO_RESPONSE"),population.signum()==0?null:population.subtract(responded),"count",null,null,
                        population.signum()==0?"NO_DATA_IN_PERIOD":"AVAILABLE",null,asOf,generatedAt));
                if (customer==null) put(result,notApplicable(IDS.get(5),"OUTSIDE_ROLE_SCOPE",asOf,generatedAt));
                else {
                    var buckets=repository.engagement(customer,interactionScope(actor,global,null),asOf,true);
                    if (buckets.isEmpty()) put(result,metric(IDS.get(5),Map.of(),BigDecimal.ZERO,"count",null,null,"ZERO",null,asOf,generatedAt));
                    for(var row:buckets) put(result,metric(IDS.get(5),Map.of("segment",row.get("segment")),decimal(row.get("total")),"count",null,null,"AVAILABLE_SNAPSHOT",null,asOf,generatedAt));
                }
            }
        }
        if (canB && selected(selection,"B")) {
            Scope tasks=global?tenant(actor,"t","1=1"):scope(actor,work.taskScope(actor,false),"t");
            if(owner!=null) tasks=add(tasks,"t.owner_user_id=?",owner);
            var summary=repository.taskSummary(tasks,asOf,start,end);
            for(int i=6;i<=7;i++) { BigDecimal value=decimal(summary.get(i==6?"open_count":"overdue_count")); put(result,metric(IDS.get(i),Map.of(),value,"count",null,null,available(value,true),null,asOf,generatedAt)); }
            Map<String,BigDecimal> grouped=new TreeMap<>(), owners=new TreeMap<>();
            for(var row:repository.taskBuckets(tasks)) {
                BigDecimal n=decimal(row.get("total")); String key=row.get("priority")+"|"+row.get("status");
                grouped.merge(key,n,BigDecimal::add); owners.merge(row.get("owner_user_id").toString(),n,BigDecimal::add);
            }
            if(grouped.isEmpty()) put(result,metric(IDS.get(8),Map.of(),BigDecimal.ZERO,"count",null,null,"ZERO",null,asOf,generatedAt));
            grouped.forEach((key,value)->{String[] parts=key.split("\\|",-1); put(result,metric(IDS.get(8),Map.of("priority",parts[0],"status",parts[1]),value,"count",null,null,"AVAILABLE_SNAPSHOT",null,asOf,generatedAt));});
            if(owners.isEmpty()) put(result,metric(IDS.get(9),Map.of(),BigDecimal.ZERO,"count",null,null,"ZERO",null,asOf,generatedAt));
            owners.forEach((key,value)->put(result,metric(IDS.get(9),Map.of("ownerUserId",key),value,"count",null,null,"AVAILABLE_SNAPSHOT",null,asOf,generatedAt)));
            rate(result,IDS.get(10),summary,"on_time_count","completed_count","on_time_rate",false,asOf,generatedAt);
        }
        if (global && owner==null && selected(selection,"C")) {
            Scope workflows=tenant(actor,"w","1=1"); if(workflowType!=null) workflows=add(workflows,"w.workflow_type=?",workflowType);
            var summary=repository.workflowSummary(workflows,start,end); var failures=repository.failedWorkflowBuckets(workflows,start,end);
            if(summary.isEmpty()) for(int i=11;i<=13;i++) put(result,metric(IDS.get(i),Map.of(),null,i==13?"seconds":i==11?"ratio":"count",null,null,"NO_DATA_IN_PERIOD",null,asOf,generatedAt));
            for(var row:summary) {
                String type=row.get("workflow_type").toString(); BigDecimal done=decimal(row.get("done_count")), failed=decimal(row.get("failed_count")), valid=decimal(row.get("valid_count"));
                BigDecimal denominator=done.add(failed);
                put(result,metric(IDS.get(11),Map.of("workflowType",type),decimal(row.get("success_rate")),"ratio",done,denominator,denominator.signum()==0?"NOT_AVAILABLE_ZERO_DENOMINATOR":"AVAILABLE",null,asOf,generatedAt));
                boolean hasFailures=false;
                for(var failure:failures) if(type.equals(failure.get("workflow_type"))) { hasFailures=true; put(result,metric(IDS.get(12),Map.of("workflowType",type,"retryCount",failure.get("retry_count")),decimal(failure.get("total")),"count",null,null,"AVAILABLE",null,asOf,generatedAt)); }
                if(!hasFailures) put(result,metric(IDS.get(12),Map.of("workflowType",type),null,"count",null,null,"NO_DATA_IN_PERIOD",null,asOf,generatedAt));
                put(result,metric(IDS.get(13),Map.of("workflowType",type),decimal(row.get("avg_seconds")),"seconds",valid,null,valid.signum()==0?"NOT_AVAILABLE":"AVAILABLE",valid.signum()==0?"NO_VALID_INTERVAL":null,asOf,generatedAt));
            }
        }
        if (canD && selected(selection,"D")) {
            Scope scope=interactionScope(actor,global,owner); Map<String,BigDecimal> channels=new TreeMap<>(), recorders=new TreeMap<>();
            for(var row:repository.interactionBuckets(scope,start,end)) {
                BigDecimal n=decimal(row.get("total")); channels.merge(row.get("channel").toString(),n,BigDecimal::add); recorders.merge(row.get("recorded_by_user_id").toString(),n,BigDecimal::add);
            }
            if(channels.isEmpty()) for(int i=14;i<=15;i++) put(result,metric(IDS.get(i),Map.of(),null,"count",null,null,"NO_DATA_IN_PERIOD",null,asOf,generatedAt));
            channels.forEach((key,value)->put(result,metric(IDS.get(14),Map.of("channel",key),value,"count",null,null,"AVAILABLE",null,asOf,generatedAt)));
            recorders.forEach((key,value)->put(result,metric(IDS.get(15),Map.of("userId",key),value,"count",null,null,"AVAILABLE",null,asOf,generatedAt)));
        }
        List<Metric> output=new ArrayList<>();
        for(String id:IDS) if(all||selection.contains(id)) {
            List<Metric> entries=result.get(id);
            if(entries==null) entries=List.of(notApplicable(id,id.startsWith("C") && owner!=null?"OWNER_FILTER_NOT_SUPPORTED":"OUTSIDE_ROLE_SCOPE",asOf,generatedAt));
            output.addAll(entries);
        }
        return new DashboardResponse(actor.organizationId(),asOf,new DashboardResponse.Period(start,end,zone),new Filters(owner,workflowType,List.copyOf(selection)),List.copyOf(output));
    }

    private Scope interactionScope(ActorContext actor,boolean global,UUID owner) {
        if (!global && !actor.hasPermission(PermissionCode.INTERACTION_READ)) return null;
        Scope s=global?tenant(actor,"i","1=1"):scope(actor,work.subjectScope(actor,PermissionCode.INTERACTION_READ,false,"i","i.customer_id","i.lead_id"),"i");
        if(!global) s=new Scope(s.where()+interactions.careVisibleOnlyPredicate(actor,"i"),s.args());
        return owner==null?s:add(s,"i.recorded_by_user_id=?",owner);
    }
    private void requireOwner(ActorContext actor,UUID owner) {
        boolean global=actor.hasRole(RoleCode.ADMIN)||actor.hasRole(RoleCode.DATA_STAFF);
        Timestamp now=Timestamp.from(clock.instant());
        Integer count=jdbc.queryForObject("""
                SELECT count(*) FROM users u WHERE u.organization_id=? AND u.id=? AND
                  (?=TRUE OR u.id=? OR EXISTS (SELECT 1 FROM team_memberships m JOIN teams t
                   ON t.id=m.team_id AND t.organization_id=m.organization_id
                   WHERE m.organization_id=u.organization_id AND m.user_id=u.id AND t.manager_user_id=?
                     AND t.status='ACTIVE' AND m.status='ACTIVE' AND m.joined_at<=? AND (m.left_at IS NULL OR m.left_at>?)))
                """,Integer.class,actor.organizationId(),owner,global,actor.userId(),actor.hasRole(RoleCode.MANAGER)?actor.userId():null,now,now);
        if(count==null||count==0) FoundationAccess.denyScope();
    }
    private static String single(MultiValueMap<String,String> p,String key) {
        List<String> values=p.get(key); if(values==null)return null;
        if(values.size()!=1 || values.getFirst()==null || values.getFirst().isBlank()) throw JsonInput.invalid(key,"Tham số không hợp lệ.");
        return values.getFirst().strip();
    }
    private static Instant parseInstant(String text,String field,Instant fallback) {
        if(text==null)return fallback;
        try { return OffsetDateTime.parse(text).toInstant(); } catch(DateTimeParseException ex) { throw JsonInput.invalid(field,"Thời điểm ISO-8601 có múi giờ không hợp lệ."); }
    }
    private static UUID uuid(String value,String field) {
        try { UUID id=UUID.fromString(value); if(!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); return id; }
        catch(IllegalArgumentException ex) { throw JsonInput.invalid(field,"UUID không hợp lệ."); }
    }
    private static Scope tenant(ActorContext a,String alias,String condition) {return new Scope(alias+".organization_id=? AND "+condition,List.of(a.organizationId()));}
    private static Scope scope(ActorContext a,CrmAccess.Scope s,String alias) {var args=new ArrayList<Object>();args.add(a.organizationId());args.addAll(s.args());return new Scope(alias+".organization_id=? AND "+s.where(),args);}
    private static Scope from(CrmAccess.Scope s) {return new Scope(s.where(),s.args());}
    private static Scope add(Scope s,String sql,Object value) {var args=new ArrayList<>(s.args());args.add(value);return new Scope("("+s.where()+") AND "+sql,args);}
    private static boolean selected(Set<String> selection,String prefix) {return selection.isEmpty()||selection.stream().anyMatch(id->id.startsWith(prefix));}
    private static BigDecimal decimal(Object value) {return value==null?null:value instanceof BigDecimal b?b:new BigDecimal(value.toString());}
    private static String available(BigDecimal n,boolean snapshot) {return n.signum()==0?"ZERO":snapshot?"AVAILABLE_SNAPSHOT":"AVAILABLE";}
    private static Metric metric(String id,Map<String,Object> dims,BigDecimal value,String unit,BigDecimal num,BigDecimal den,String availability,String reason,Instant asOf,Instant generatedAt) {
        return new Metric(id,dims,value,unit,num,den,availability,reason,asOf,generatedAt,"W09-01:"+id.substring(0,3));
    }
    private static Metric notApplicable(String id,String reason,Instant asOf,Instant generatedAt) {return metric(id,Map.of(),null,"count",null,null,"NOT_APPLICABLE",reason,asOf,generatedAt);}
    private static void put(Map<String,List<Metric>> result,Metric metric) {result.computeIfAbsent(metric.metricId(),ignored->new ArrayList<>()).add(metric);}
    private static void rate(Map<String,List<Metric>> result,String id,Map<String,Object> row,String numerator,String denominator,String rate,boolean snapshot,Instant asOf,Instant generatedAt) {
        BigDecimal n=decimal(row.get(numerator)), d=decimal(row.get(denominator)), value=decimal(row.get(rate));
        put(result,metric(id,Map.of(),value,"ratio",n,d,d.signum()==0?"NOT_AVAILABLE_ZERO_DENOMINATOR":available(value,snapshot),null,asOf,generatedAt));
    }
}
