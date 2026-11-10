package com.pose.backend.dashboard;

import com.pose.backend.api.JsonInput;
import com.pose.backend.crm.CrmAccess;
import com.pose.backend.crm.CrmWorkAccess;
import com.pose.backend.dashboard.DashboardRepository.Scope;
import com.pose.backend.dashboard.DashboardResponse.Metric;
import com.pose.backend.identity.*;
import com.pose.backend.interaction.InteractionService;
import com.pose.backend.opportunity.OpportunityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ValueLeakageService {
    public static final List<String> IDS=List.of("E01_OVERDUE_LEAD_RESPONSE","E02_UNATTENDED_OPPORTUNITY_VALUE","E03_UNCONTACTED_AFTER_HANDOVER","E04_FIRST_MESSAGE_RESPONSE_TIME","E05_AI_SUGGESTION_ACCEPTANCE_RATE");
    public record TodayItem(String type,int rank,String subjectType,UUID subjectId,String title,UUID ownerUserId,String ownerName,String reason,Instant occurredAt,Instant dueAt,BigDecimal expectedValue,String currency,String href) {}
    public record TodayResponse(boolean applicable,List<TodayItem> items) {}
    private final ValueLeakageRepository repo; private final CrmAccess crm; private final CrmWorkAccess work;
    private final InteractionService interactions; private final OpportunityService opportunities; private final Clock clock;
    private final long slaHours,idleDays;
    public ValueLeakageService(ValueLeakageRepository repo,CrmAccess crm,CrmWorkAccess work,InteractionService interactions,
                               OpportunityService opportunities,Clock clock,
                               @Value("${pose.kpi.lead-response-sla-hours:24}") long slaHours,
                               @Value("${pose.kpi.opportunity-idle-days:14}") long idleDays) {
        if(slaHours<1||idleDays<1||slaHours>876000||idleDays>36500) throw new IllegalArgumentException("Invalid KPI thresholds");
        this.repo=repo;this.crm=crm;this.work=work;this.interactions=interactions;this.opportunities=opportunities;this.clock=clock;this.slaHours=slaHours;this.idleDays=idleDays;
    }
    private static boolean global(ActorContext a){return a.hasRole(RoleCode.ADMIN)||a.hasRole(RoleCode.DATA_STAFF);}
    private static Scope tenant(ActorContext a,String alias,String extra){return new Scope(alias+".organization_id=? AND "+extra,List.of(a.organizationId()));}
    private static Scope from(CrmAccess.Scope s){return new Scope(s.where(),s.args());}
    private static Scope scoped(ActorContext a,CrmAccess.Scope s,String alias){var args=new ArrayList<Object>();args.add(a.organizationId());args.addAll(s.args());return new Scope(alias+".organization_id=? AND "+s.where(),args);}
    private static Scope add(Scope s,String clause,Object value){var args=new ArrayList<>(s.args());args.add(value);return new Scope("("+s.where()+") AND "+clause,args);}
    private Scope interactionScope(ActorContext a) {
        if(global(a))return tenant(a,"i","1=1");
        Scope s=scoped(a,work.subjectScope(a,PermissionCode.INTERACTION_READ,false,"i","i.customer_id","i.lead_id"),"i");
        return new Scope(s.where()+interactions.careVisibleOnlyPredicate(a,"i"),s.args());
    }
    private Scope leadScope(ActorContext a,boolean write){return !write&&global(a)?tenant(a,"l","l.archived_at IS NULL"):from(crm.scope(a,true,write));}
    private Scope customerScope(ActorContext a,boolean write){return !write&&global(a)?tenant(a,"c","c.archived_at IS NULL"):from(crm.scope(a,false,write));}
    private Scope opportunityScope(ActorContext a,boolean write){return !write&&global(a)?tenant(a,"op","1=1"):from(write?opportunities.writeScope(a):opportunities.readScope(a));}
    private static Metric metric(String id,Map<String,Object> dimensions,BigDecimal value,String unit,BigDecimal numerator,BigDecimal denominator,String availability,String reason,Instant asOf,Instant generated) {
        return new Metric(id,dimensions,value,unit,numerator,denominator,availability,reason,asOf,generated,"DEC-21:"+id.substring(0,3));
    }
    private static String availability(BigDecimal n){return n.signum()==0?"ZERO":"AVAILABLE_SNAPSHOT";}
    private static BigDecimal number(Object value){return value==null?null:value instanceof BigDecimal b?b:new BigDecimal(value.toString());}
    private static void put(Map<String,List<Metric>> output,Metric metric){output.computeIfAbsent(metric.metricId(),ignored->new ArrayList<>()).add(metric);}
    private static boolean requested(Set<String> selected,int index){return selected.isEmpty()||selected.contains(IDS.get(index));}
    public void append(ActorContext actor,Set<String> selected,UUID owner,Instant asOf,Instant start,Instant end,Instant generated,Map<String,List<Metric>> output) {
        boolean global=global(actor), applicable=global||actor.hasRole(RoleCode.MANAGER)||actor.hasRole(RoleCode.SALES_REP);
        if(!applicable)return;
        boolean hasInteractions=global||actor.hasPermission(PermissionCode.INTERACTION_READ);
        Scope interaction=hasInteractions?interactionScope(actor):null;
        if(requested(selected,0)&&hasInteractions&&(global||actor.hasPermission(PermissionCode.LEAD_READ))) {
            Scope s=leadScope(actor,false);if(owner!=null)s=add(s,"l.owner_user_id=?",owner);
            BigDecimal value=BigDecimal.valueOf(repo.overdueLeadCount(s,interaction,asOf,slaHours));
            put(output,metric(IDS.get(0),Map.of(),value,"count",null,null,availability(value),null,asOf,generated));
        }
        if(requested(selected,1)&&hasInteractions&&(global||actor.hasPermission(PermissionCode.OPPORTUNITY_READ))) {
            Scope s=opportunityScope(actor,false);if(owner!=null)s=add(s,"op.owner_user_id=?",owner);
            var rows=repo.idleValues(s,interaction,asOf,idleDays);
            for(var row:rows){BigDecimal value=number(row.get("amount"));put(output,metric(IDS.get(1),Map.of("currency",row.get("currency_code").toString().strip()),value,"currency",number(row.get("total")),null,availability(value),null,asOf,generated));}
            if(rows.isEmpty())put(output,metric(IDS.get(1),Map.of(),BigDecimal.ZERO,"currency",BigDecimal.ZERO,null,"ZERO",null,asOf,generated));
        }
        if(requested(selected,2)&&hasInteractions&&(global||actor.hasPermission(PermissionCode.CUSTOMER_READ))) {
            Scope s=customerScope(actor,false);if(owner!=null)s=add(s,"h.target_user_id=?",owner);
            BigDecimal den=BigDecimal.valueOf(repo.handoverTotal(actor.organizationId(),s,start,end));
            BigDecimal value=den.signum()==0?null:BigDecimal.valueOf(repo.uncontactedCount(actor.organizationId(),s,interaction,asOf,start,end));
            put(output,metric(IDS.get(2),Map.of(),value,"count",null,den,value==null?"NO_DATA_IN_PERIOD":availability(value),null,asOf,generated));
        }
        if(requested(selected,3))put(output,metric(IDS.get(3),Map.of(),null,"seconds",null,null,"NOT_SUPPORTED","CONVERSATION_DATA_NOT_AVAILABLE",asOf,generated));
        if(requested(selected,4)) {
            var rows=repo.suggestions(actor.organizationId(),actor.userId(),global,actor.hasRole(RoleCode.MANAGER),owner,start,end,clock.instant());
            if(rows.isEmpty())put(output,metric(IDS.get(4),Map.of(),null,"ratio",BigDecimal.ZERO,BigDecimal.ZERO,"NOT_AVAILABLE_ZERO_DENOMINATOR",null,asOf,generated));
            for(var row:rows){BigDecimal n=number(row.get("accepted")),d=number(row.get("total")),value=n.divide(d,MathContext.DECIMAL128);put(output,metric(IDS.get(4),Map.of("kind",row.get("kind")),value,"ratio",n,d,value.signum()==0?"ZERO":"AVAILABLE",null,asOf,generated));}
        }
    }
    @Transactional(readOnly=true)
    public TodayResponse today(ActorContext actor,MultiValueMap<String,String> params) {
        if(!actor.hasPermission(PermissionCode.DASHBOARD_READ))FoundationAccess.denyPermission();
        for(String key:params.keySet())if(!Set.of("asOf","timezone","limit").contains(key))throw JsonInput.invalid(key,"Tham số không được hỗ trợ.");
        String date=single(params,"asOf"),zoneText=Optional.ofNullable(single(params,"timezone")).orElse("Asia/Ho_Chi_Minh"),limitText=single(params,"limit");
        Instant asOf;try{asOf=date==null?clock.instant():OffsetDateTime.parse(date).toInstant();}catch(DateTimeParseException ex){throw JsonInput.invalid("asOf","Thời điểm ISO-8601 có múi giờ không hợp lệ.");}
        if(!ZoneId.getAvailableZoneIds().contains(zoneText))throw JsonInput.invalid("timezone","Múi giờ IANA không hợp lệ.");
        int limit;try{limit=limitText==null?20:Integer.parseInt(limitText);if(limit<1||limit>50)throw new NumberFormatException();}catch(NumberFormatException ex){throw JsonInput.invalid("limit","Số lượng phải từ 1 đến 50.");}
        boolean workRole=actor.hasRole(RoleCode.ADMIN)||actor.hasRole(RoleCode.MANAGER)||actor.hasRole(RoleCode.SALES_REP)||actor.hasRole(RoleCode.CUSTOMER_CARE);
        if(!workRole)return new TodayResponse(false,List.of());
        LocalDate local=asOf.atZone(ZoneId.of(zoneText)).toLocalDate();Instant dayStart=local.atStartOfDay(ZoneId.of(zoneText)).toInstant(),dayEnd=local.plusDays(1).atStartOfDay(ZoneId.of(zoneText)).toInstant();
        List<TodayItem> items=new ArrayList<>();
        if(actor.hasPermission(PermissionCode.TASK_MANAGE)) {
            Scope tasks=scoped(actor,work.taskScope(actor,true),"t");
            for(var row:repo.tasks(tasks,null,asOf,true,limit))items.add(task(row,"OVERDUE_TASK",1,asOf));
        }
        boolean salesWork=(actor.hasRole(RoleCode.ADMIN)||actor.hasRole(RoleCode.MANAGER)||actor.hasRole(RoleCode.SALES_REP));
        if(salesWork && actor.hasPermission(PermissionCode.INTERACTION_READ)) {
            Scope visible=interactionScope(actor);
            if(actor.hasPermission(PermissionCode.LEAD_WRITE))for(var row:repo.overdueLeads(leadScope(actor,true),visible,asOf,slaHours,limit)) {
                Instant created=instant(row.get("created_at"));long hours=Math.max(0,Duration.between(created,asOf).toHours());items.add(item(row,"LEAD_RESPONSE_OVERDUE",2,"LEAD","Chưa phản hồi sau "+hours+" giờ",created,null,null,null,"/app/lead/"));
            }
            if(actor.hasPermission(PermissionCode.CUSTOMER_WRITE))for(var row:repo.uncontactedHandovers(actor.organizationId(),customerScope(actor,true),visible,asOf,limit)) {
                Instant reviewed=instant(row.get("reviewed_at"));long days=Math.max(0,Duration.between(reviewed,asOf).toDays());items.add(item(row,"HANDOVER_NOT_CONTACTED",3,"CUSTOMER","Đã bàn giao "+days+" ngày, chưa liên hệ",reviewed,null,null,null,"/app/khach-hang/"));
            }
            if(actor.hasPermission(PermissionCode.OPPORTUNITY_WRITE))for(var row:repo.idleOpportunities(opportunityScope(actor,true),visible,asOf,idleDays,limit)) {
                BigDecimal value=number(row.get("expected_value"));String currency=row.get("currency_code").toString().strip();
                Instant last=row.get("last_activity")==null?instant(row.get("created_at")):instant(row.get("last_activity"));
                long days=Math.max(0,Duration.between(last,asOf).toDays());
                items.add(item(row,"IDLE_OPPORTUNITY",4,"OPPORTUNITY","Không chăm sóc "+days+" ngày"+(value==null?"":", giá trị "+NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value)+" "+currency),last,null,value,currency,"/app/co-hoi/"));
            }
        }
        if(actor.hasPermission(PermissionCode.TASK_MANAGE)) {
            Scope tasks=scoped(actor,work.taskScope(actor,true),"t");Instant first=dayStart.isAfter(asOf)?dayStart:asOf;
            for(var row:repo.tasks(tasks,first,dayEnd,false,limit))items.add(task(row,"TASK_DUE_TODAY",5,asOf));
        }
        return new TodayResponse(true,List.copyOf(items.subList(0,Math.min(limit,items.size()))));
    }
    private static String single(MultiValueMap<String,String> params,String key){var v=params.get(key);if(v==null)return null;if(v.size()!=1||v.getFirst()==null||v.getFirst().isBlank())throw JsonInput.invalid(key,"Tham số không hợp lệ.");return v.getFirst().strip();}
    private static Instant instant(Object value){return value instanceof Timestamp t?t.toInstant():value instanceof OffsetDateTime t?t.toInstant():(Instant)value;}
    private static UUID uuid(Object value){return value instanceof UUID u?u:UUID.fromString(value.toString());}
    private static TodayItem item(Map<String,Object> row,String type,int rank,String subject,String reason,Instant occurred,Instant due,BigDecimal value,String currency,String path){UUID id=uuid(row.get("id"));return new TodayItem(type,rank,subject,id,row.get("title").toString(),uuid(row.get("owner_user_id")),row.get("owner_name").toString(),reason,occurred,due,value,currency,path+id);}
    private static TodayItem task(Map<String,Object> row,String type,int rank,Instant asOf){Instant due=instant(row.get("due_at"));String reason=rank==1?"Đã quá hạn "+Math.max(0,Duration.between(due,asOf).toDays())+" ngày":"Đến hạn hôm nay";return item(row,type,rank,"TASK",reason,null,due,null,null,"/app/nhiem-vu/");}
}
