package com.pose.backend.opportunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.crm.*;
import com.pose.backend.identity.*;
import com.pose.backend.offering.OfferingRepository;
import com.pose.backend.salesassignment.SalesAssignmentCandidateResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class OpportunityService {
    private static final List<String> OPEN=List.of("NEW","QUALIFIED","PROPOSAL");
    private static final Set<String> STAGES=Set.of("NEW","QUALIFIED","PROPOSAL","WON","LOST","CANCELLED");
    private static final String INVALID_OFFERING="Sản phẩm/dịch vụ không còn hoạt động hoặc không thuộc phạm vi của bạn.";
    private final JdbcTemplate jdbc; private final CrmAccess crm; private final FoundationAccess access; private final FoundationAuditRepository audit;
    private final OpportunityRepository repo; private final OfferingRepository offerings; private final Clock clock;
    public OpportunityService(JdbcTemplate jdbc,CrmAccess crm,FoundationAccess access,FoundationAuditRepository audit,OpportunityRepository repo,OfferingRepository offerings,Clock clock) {
        this.jdbc=jdbc;this.crm=crm;this.access=access;this.audit=audit;this.repo=repo;this.offerings=offerings;this.clock=clock;
    }
    private boolean role(ActorContext actor,RoleCode role,PermissionCode permission,boolean write) { return write?crm.roleWith(actor,role,permission):actor.hasRole(role); }
    private CrmAccess.Scope scope(ActorContext actor,PermissionCode permission,boolean write,RoleCode only) {
        if(!actor.hasPermission(permission)) FoundationAccess.denyPermission();
        var parts=new ArrayList<String>(); var args=new ArrayList<Object>(); args.add(actor.organizationId());
        if((only==null||only==RoleCode.ADMIN)&&role(actor,RoleCode.ADMIN,permission,write)) parts.add("1=1");
        if((only==null||only==RoleCode.MANAGER)&&role(actor,RoleCode.MANAGER,permission,write)) {
            parts.add("EXISTS (SELECT 1 FROM opportunity_lines ol JOIN offerings mo ON mo.id=ol.offering_id AND mo.organization_id=ol.organization_id JOIN teams mt ON mt.id=mo.team_id AND mt.organization_id=mo.organization_id WHERE ol.organization_id=op.organization_id AND ol.opportunity_id=op.id AND mt.status='ACTIVE' AND mt.manager_user_id=?)"); args.add(actor.userId());
        }
        if((only==null||only==RoleCode.SALES_REP)&&role(actor,RoleCode.SALES_REP,permission,write)) {
            var cs=crm.subjectRoleScope(actor.userId(),RoleCode.SALES_REP,false);
            var ls=crm.subjectRoleScope(actor.userId(),RoleCode.SALES_REP,true);
            String lead=actor.hasPermission(PermissionCode.OFFERING_READ)?"(l.id IS NOT NULL AND l.archived_at IS NULL AND ("+ls.where()+"))":"1=0";
            parts.add("(op.owner_user_id=? AND ((c.id IS NOT NULL AND c.archived_at IS NULL AND ("+cs.where()+")) OR "+lead+"))");
            args.add(actor.userId()); args.addAll(cs.args()); if(actor.hasPermission(PermissionCode.OFFERING_READ)) args.addAll(ls.args());
        }
        if(parts.isEmpty()) FoundationAccess.denyScope();
        return new CrmAccess.Scope("op.organization_id=? AND ("+String.join(" OR ",parts)+")",args);
    }
    private OpportunityResponse find(ActorContext actor,UUID id,PermissionCode permission,boolean write) {
        var s=scope(actor,permission,write,null); return repo.find(s.where(),s.args(),id).orElseGet(()->{FoundationAccess.denyScope(); return null;});
    }
    public CrmAccess.Scope readScope(ActorContext actor) {
        return scope(actor,PermissionCode.OPPORTUNITY_READ,false,null);
    }
    private boolean inRoleScope(ActorContext actor,UUID id,RoleCode role,PermissionCode permission) {
        if(!crm.roleWith(actor,role,permission)) return false;
        var s=scope(actor,permission,true,role); return repo.find(s.where(),s.args(),id).isPresent();
    }
    @Transactional(readOnly=true) public OpportunityResponse get(ActorContext a,UUID id) { return find(a,id,PermissionCode.OPPORTUNITY_READ,false); }
    @Transactional(readOnly=true) public PageResponse<OpportunityResponse> list(ActorContext actor,int page,int size,String search,String stage,UUID customer,UUID lead,UUID owner,LocalDate from,LocalDate to) {
        PageResponse.validate(page,size); var s=scope(actor,PermissionCode.OPPORTUNITY_READ,false,null); var where=new StringBuilder(s.where()); var args=new ArrayList<>(s.args());
        if(customer!=null && lead!=null) throw JsonInput.invalid("leadId","Chỉ chọn khách hàng hoặc Lead.");
        if(search!=null&&!search.isBlank()) {
            if(search.length()>200) throw JsonInput.invalid("search","Tối đa 200 ký tự.");
            String pattern="%"+search.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            // Title, customer/lead name, or any line offering name/code; rows are already limited by scope.
            where.append(" AND (lower(op.title) LIKE ? ESCAPE '!' OR lower(c.display_name) LIKE ? ESCAPE '!' OR lower(l.display_name) LIKE ? ESCAPE '!'"
                    +" OR EXISTS (SELECT 1 FROM opportunity_lines sl JOIN offerings so ON so.organization_id=sl.organization_id AND so.id=sl.offering_id"
                    +" WHERE sl.organization_id=op.organization_id AND sl.opportunity_id=op.id AND (lower(so.name) LIKE ? ESCAPE '!' OR lower(so.code) LIKE ? ESCAPE '!')))");
            for(int i=0;i<5;i++) args.add(pattern);
        }
        if(stage!=null) { if(!STAGES.contains(stage)) throw JsonInput.invalid("stage","Giai đoạn không hợp lệ."); where.append(" AND op.stage=?");args.add(stage); }
        if(customer!=null) { where.append(" AND op.customer_id=?"); args.add(customer); }
        if(lead!=null) { where.append(" AND op.lead_id=?"); args.add(lead); }
        if(owner!=null) { requireManagerOrAdmin(actor); where.append(" AND op.owner_user_id=?");args.add(owner); }
        if(from!=null) { where.append(" AND op.expected_close_date>=?");args.add(from); } if(to!=null) { where.append(" AND op.expected_close_date<=?");args.add(to); }
        return new PageResponse<>(repo.list(where.toString(),args,page,size),page,size,repo.count(where.toString(),args));
    }
    @Transactional(readOnly=true) public List<SalesAssignmentCandidateResponse> owners(ActorContext actor) {
        requireManagerOrAdmin(actor); var s=scope(actor,PermissionCode.OPPORTUNITY_READ,false,null);
        return jdbc.query("SELECT id,full_name FROM (SELECT DISTINCT u.id,u.full_name FROM opportunities op JOIN users u ON u.id=op.owner_user_id AND u.organization_id=op.organization_id LEFT JOIN customers c ON c.id=op.customer_id AND c.organization_id=op.organization_id LEFT JOIN leads l ON l.id=op.lead_id AND l.organization_id=op.organization_id WHERE "+s.where()+") owners ORDER BY lower(full_name),full_name,id",(rs,n)->new SalesAssignmentCandidateResponse(rs.getObject("id",UUID.class),rs.getString("full_name")),s.args().toArray());
    }
    @Transactional(readOnly=true) public List<SalesAssignmentCandidateResponse> candidates(ActorContext actor,UUID customer,List<UUID> ids) {
        requireManagerOrAdmin(actor); if(!actor.hasPermission(PermissionCode.OPPORTUNITY_READ)) FoundationAccess.denyPermission();
        if(customer==null) throw JsonInput.invalid("customerId","Vui lòng điền đầy đủ các trường bắt buộc");
        if(ids==null||ids.isEmpty()||ids.size()>50||new HashSet<>(ids).size()!=ids.size()) throw JsonInput.invalid("offeringIds","Chọn từ 1 đến 50 sản phẩm khác nhau.");
        RoleCode role=actor.hasRole(RoleCode.ADMIN)?RoleCode.ADMIN:RoleCode.MANAGER;
        if(!customerInRole(actor,customer,role)) throw JsonInput.invalid("customerId","Khách hàng không thuộc phạm vi của bạn.");
        for(int i=0;i<ids.size();i++) if(!offeringInRole(actor,ids.get(i),role)) throw JsonInput.invalid("offeringIds",INVALID_OFFERING);
        return jdbc.query("SELECT id,full_name FROM users WHERE organization_id=? AND status='ACTIVE' AND archived_at IS NULL ORDER BY lower(full_name),full_name,id",(rs,n)->new SalesAssignmentCandidateResponse(rs.getObject("id",UUID.class),rs.getString("full_name")),actor.organizationId()).stream().filter(c->eligibleCustomer(actor.organizationId(),customer,c.id()) && ids.stream().allMatch(id->crm.eligibleOwner(actor.organizationId(),c.id(),id))).toList();
    }
    @Transactional public OpportunityResponse create(ActorContext incoming,JsonNode body) {
        ActorContext a=access.lockAndRefresh(incoming); if(!a.hasPermission(PermissionCode.OPPORTUNITY_WRITE)) FoundationAccess.denyPermission();
        boolean sales=crm.roleWith(a,RoleCode.SALES_REP,PermissionCode.OPPORTUNITY_WRITE), manager=crm.roleWith(a,RoleCode.MANAGER,PermissionCode.OPPORTUNITY_WRITE);
        if(!sales&&!manager) FoundationAccess.denyPermission();
        JsonInput in=JsonInput.of(body,"customerId","ownerUserId","title","stage","expectedCloseDate","notes","lines");
        UUID customer=CrmInput.uuid(in,"customerId"), requested=in.has("ownerUserId")?in.uuid("ownerUserId"):null;
        var createRoles=new ArrayList<RoleCode>();
        if(sales && (requested==null || requested.equals(a.userId()))) createRoles.add(RoleCode.SALES_REP);
        if(manager && requested!=null) createRoles.add(RoleCode.MANAGER);
        if(createRoles.isEmpty()) { if(requested==null) throw JsonInput.invalid("ownerUserId","Vui lòng điền đầy đủ các trường bắt buộc"); FoundationAccess.denyScope(); }
        createRoles.removeIf(r->!customerInRole(a,customer,r));
        if(createRoles.isEmpty()) throw JsonInput.invalid("customerId","Khách hàng không thuộc phạm vi của bạn.");
        String title=CrmInput.text(in,"title",220,true,false), stage=openStage(CrmInput.text(in,"stage",30,true,false));
        LocalDate close=date(in,"expectedCloseDate",true); validateExpected(close);
        String notes=CrmInput.text(in,"notes",4000,false,true); var lines=parseLines(body.get("lines")); var viable=validateLines(a,lines,createRoles);
        RoleCode role=viable.contains(RoleCode.SALES_REP)?RoleCode.SALES_REP:RoleCode.MANAGER;
        UUID owner=role==RoleCode.SALES_REP?a.userId():requested;
        if(role==RoleCode.MANAGER && (!eligibleCustomer(a.organizationId(),customer,owner)||lines.stream().anyMatch(l->!crm.eligibleOwner(a.organizationId(),owner,l.offeringId())))) throw ApiRequestException.invalidOwner();
        UUID id=UUID.randomUUID();Instant now=clock.instant();repo.insert(id,a.organizationId(),customer,owner,title,stage,close,notes,lines,now); audit.record(a,"OPPORTUNITY_CREATED","OPPORTUNITY",id,null,Map.of("stage",stage),now);
        return find(a,id,PermissionCode.OPPORTUNITY_WRITE,true);
    }
    @Transactional public OpportunityResponse patch(ActorContext incoming,UUID id,JsonNode body) {
        ActorContext a=access.lockAndRefresh(incoming); OpportunityResponse before=find(a,id,PermissionCode.OPPORTUNITY_WRITE,true); requireOpen(before);
        JsonInput in=JsonInput.of(body,"title","stage","expectedCloseDate","notes","lines","expectedUpdatedAt"); checkVersion(in,before);
        String title=in.has("title")?CrmInput.text(in,"title",220,true,false):before.title();
        String stage=in.has("stage")?openStage(CrmInput.text(in,"stage",30,true,false)):before.stage();
        if(OPEN.indexOf(stage)<OPEN.indexOf(before.stage())) throw ApiRequestException.conflict("OPPORTUNITY_STAGE_BACKWARD");
        LocalDate close=in.has("expectedCloseDate")?date(in,"expectedCloseDate",true):before.expectedCloseDate(); if(!Objects.equals(close,before.expectedCloseDate())) validateExpected(close);
        String notes=in.has("notes")?CrmInput.text(in,"notes",4000,false,true):before.notes();
        var lines=in.has("lines")?parseLines(body.get("lines")):before.lines().stream().map(l->line(l.offeringId(),new BigDecimal(l.quantity()),new BigDecimal(l.unitPrice()),new BigDecimal(l.discountAmount()),"lines")).toList();
        var roles=new ArrayList<RoleCode>(); for(RoleCode r:List.of(RoleCode.ADMIN,RoleCode.MANAGER,RoleCode.SALES_REP)) if(inRoleScope(a,id,r,PermissionCode.OPPORTUNITY_WRITE)) roles.add(r);
        validateLines(a,lines,roles);
        if(in.has("lines")) {
            Set<UUID> existing=new HashSet<>(); before.lines().forEach(l->existing.add(l.offeringId()));
            for(int i=0;i<lines.size();i++) {
                UUID offeringId=lines.get(i).offeringId();
                if(!existing.contains(offeringId) && !crm.eligibleOwner(a.organizationId(),before.owner().id(),offeringId))
                    throw ApiRequestException.invalidLineOwner(i);
            }
        }
        Instant now=next(before.updatedAt()); repo.update(a.organizationId(),id,title,stage,close,notes,lines,now);
        audit.record(a,"OPPORTUNITY_UPDATED","OPPORTUNITY",id,Map.of("stage",before.stage()),Map.of("stage",stage),now);
        if(!stage.equals(before.stage())) audit.record(a,"OPPORTUNITY_STAGE_CHANGED","OPPORTUNITY",id,Map.of("stage",before.stage()),Map.of("stage",stage),now);
        return find(a,id,PermissionCode.OPPORTUNITY_WRITE,true);
    }
    @Transactional public OpportunityResponse transition(ActorContext incoming,UUID id,JsonNode body,String action) {
        ActorContext a=access.lockAndRefresh(incoming); boolean reopen=action.equals("reopen"); PermissionCode permission=reopen?PermissionCode.OPPORTUNITY_REOPEN:PermissionCode.OPPORTUNITY_WRITE;
        if(reopen&&!crm.roleWith(a,RoleCode.ADMIN,permission)&&!crm.roleWith(a,RoleCode.MANAGER,permission)) FoundationAccess.denyPermission();
        OpportunityResponse before=find(a,id,permission,true); if(!reopen) requireOpen(before); else if(OPEN.contains(before.stage())) throw ApiRequestException.conflict("OPPORTUNITY_STATE_CONFLICT");
        JsonInput in=JsonInput.of(body,reopen?new String[]{"expectedUpdatedAt"}:action.equals("win")?new String[]{"expectedUpdatedAt","finalValue","actualCloseDate"}:new String[]{"expectedUpdatedAt","reason"}); checkVersion(in,before);
        Instant now=next(before.updatedAt());
        if(reopen) {
            Map<String,Object> data=new LinkedHashMap<>();data.put("stage",before.stage());data.put("finalValue",before.finalValue());data.put("actualCloseDate",before.actualCloseDate()==null?null:before.actualCloseDate().toString());data.put("closedAt",before.closedAt()==null?null:before.closedAt().toString());
            repo.reopen(a.organizationId(),id,now); audit.record(a,"OPPORTUNITY_REOPENED","OPPORTUNITY",id,data,Map.of("stage","QUALIFIED"),now);
        } else {
            BigDecimal value=null; LocalDate actual=null; String reason=null; String stage;
            if(action.equals("win")) { stage="WON";value=decimal(body.get("finalValue"),"finalValue",false);actual=date(in,"actualCloseDate",true);if(actual.isAfter(today())) throw JsonInput.invalid("actualCloseDate","Ngày chốt không được ở tương lai."); }
            else { stage=action.equals("lose")?"LOST":"CANCELLED"; reason=CrmInput.text(in,"reason",4000,true,false); }
            repo.close(a.organizationId(),id,stage,value,actual,reason,now);audit.record(a,"OPPORTUNITY_"+(stage.equals("WON")?"WON":stage.equals("LOST")?"LOST":"CANCELLED"),"OPPORTUNITY",id,Map.of("stage",before.stage()),Map.of("stage",stage),now);
        }
        return find(a,id,permission,true);
    }
    private boolean customerInRole(ActorContext a,UUID customer,RoleCode role) {
        if(role==RoleCode.ADMIN) return jdbc.queryForObject("SELECT count(*) FROM customers c WHERE c.organization_id=? AND c.id=? AND c.archived_at IS NULL",Integer.class,a.organizationId(),customer)>0;
        var s=crm.subjectRoleScope(a.userId(),role,false); var args=new ArrayList<Object>();args.add(a.organizationId());args.add(customer);args.addAll(s.args());
        return jdbc.queryForObject("SELECT count(*) FROM customers c WHERE c.organization_id=? AND c.id=? AND c.archived_at IS NULL AND ("+s.where()+")",Integer.class,args.toArray())>0;
    }
    private boolean eligibleCustomer(UUID org,UUID customer,UUID owner) {
        var s=crm.subjectRoleScope(owner,RoleCode.SALES_REP,false); var args=new ArrayList<Object>();args.add(org);args.add(customer);args.addAll(s.args());
        return jdbc.queryForObject("SELECT count(*) FROM customers c WHERE c.organization_id=? AND c.id=? AND c.archived_at IS NULL AND ("+s.where()+")",Integer.class,args.toArray())>0;
    }
    private boolean offeringInRole(ActorContext a,UUID id,RoleCode role) {
        if(role==RoleCode.ADMIN) return jdbc.queryForObject("SELECT count(*) FROM offerings WHERE organization_id=? AND id=? AND status='ACTIVE'",Integer.class,a.organizationId(),id)>0;
        if(role==RoleCode.MANAGER) return crm.managedOffering(a,id,false);
        return a.hasPermission(PermissionCode.OFFERING_READ)&&offerings.salesAssignmentScope(a.organizationId(),id,a.userId(),clock.instant()).isPresent();
    }
    private List<RoleCode> validateLines(ActorContext a,List<OpportunityRepository.InputLine> lines,List<RoleCode> roles) {
        var viable=new ArrayList<>(roles);
        for(int i=0;i<lines.size();i++) { UUID id=lines.get(i).offeringId(); viable.removeIf(r->!offeringInRole(a,id,r)); if(viable.isEmpty()) throw JsonInput.invalid("lines["+i+"].offeringId",INVALID_OFFERING); } return viable;
    }
    private List<OpportunityRepository.InputLine> parseLines(JsonNode values) {
        if(values==null||!values.isArray()||values.isEmpty()||values.size()>50) throw JsonInput.invalid("lines","Cần từ 1 đến 50 dòng sản phẩm/dịch vụ.");
        var result=new ArrayList<OpportunityRepository.InputLine>();var seen=new HashSet<UUID>();BigDecimal total=BigDecimal.ZERO;
        for(int i=0;i<values.size();i++) { JsonNode item=values.get(i); String prefix="lines["+i+"]"; if(!item.isObject()) throw JsonInput.invalid(prefix,"Dòng không hợp lệ.");
            item.fieldNames().forEachRemaining(f->{if(!Set.of("offeringId","quantity","unitPrice","discountAmount").contains(f)) throw JsonInput.invalid(prefix+"."+f,"Trường không được phép.");});
            UUID id;try { if(!item.path("offeringId").isTextual()) throw new IllegalArgumentException();String raw=item.get("offeringId").asText();id=UUID.fromString(raw);if(!id.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException(); } catch(IllegalArgumentException ex){throw JsonInput.invalid(prefix+".offeringId","Sản phẩm/dịch vụ không hợp lệ.");}
            if(!seen.add(id)) throw JsonInput.invalid(prefix+".offeringId","Sản phẩm/dịch vụ bị trùng.");
            BigDecimal quantity=decimal(item.get("quantity"),prefix+".quantity",true),price=decimal(item.get("unitPrice"),prefix+".unitPrice",false),discount=item.has("discountAmount")?decimal(item.get("discountAmount"),prefix+".discountAmount",false):BigDecimal.ZERO;
            var line=line(id,quantity,price,discount,prefix); result.add(line);total=total.add(line.total()); if(total.precision()>18) throw JsonInput.invalid("lines","Tổng giá trị vượt giới hạn.");
        } return List.copyOf(result);
    }
    private OpportunityRepository.InputLine line(UUID id,BigDecimal quantity,BigDecimal price,BigDecimal discount,String prefix) {
        BigDecimal gross=quantity.multiply(price); if(discount.compareTo(gross)>0) throw JsonInput.invalid(prefix+".discountAmount","Chiết khấu vượt giá trị dòng.");
        BigDecimal total=gross.subtract(discount).setScale(2,RoundingMode.HALF_UP);if(total.precision()>18) throw JsonInput.invalid(prefix+".unitPrice","Giá trị dòng vượt giới hạn.");
        return new OpportunityRepository.InputLine(id,quantity,price,discount,total);
    }
    private static BigDecimal decimal(JsonNode node,String field,boolean positive) {
        try { if(node==null||(!node.isNumber()&&!node.isTextual())) throw new NumberFormatException(); BigDecimal n=new BigDecimal(node.asText()); if(n.scale()>2||n.scale()<-16||n.precision()>18||n.signum()<0||(positive&&n.signum()==0)||(positive&&n.compareTo(new BigDecimal("9999999999.99"))>0)||(!positive&&n.compareTo(new BigDecimal("9999999999999999.99"))>0)) throw new NumberFormatException(); return n.setScale(2); }
        catch(NumberFormatException|ArithmeticException ex) { throw JsonInput.invalid(field,"Giá trị số không hợp lệ."); }
    }
    private LocalDate date(JsonInput in,String field,boolean required) { try { String s=CrmInput.text(in,field,10,required,false);return s==null?null:LocalDate.parse(s); } catch(DateTimeParseException e) { throw JsonInput.invalid(field,"Ngày không hợp lệ."); } }
    private LocalDate today() { return clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(); }
    private void validateExpected(LocalDate d) { if(d==null||d.isBefore(today())) throw JsonInput.invalid("expectedCloseDate","Ngày dự kiến chốt không được ở quá khứ."); }
    private static String openStage(String stage) { if(!OPEN.contains(stage)) throw JsonInput.invalid("stage","Giai đoạn không hợp lệ."); return stage; }
    private static void requireOpen(OpportunityResponse before) { if(!OPEN.contains(before.stage())) throw ApiRequestException.conflict("OPPORTUNITY_LOCKED"); }
    private static void checkVersion(JsonInput in,OpportunityResponse before) { if(!CrmInput.instant(in,"expectedUpdatedAt").equals(before.updatedAt())) throw ApiRequestException.conflict("OPPORTUNITY_CHANGED"); }
    private Instant next(Instant old) { Instant now=clock.instant(), minimum=old.plusNanos(1000); return now.isAfter(minimum)?now:minimum; }
    private static void requireManagerOrAdmin(ActorContext a) { if(!a.hasRole(RoleCode.ADMIN)&&!a.hasRole(RoleCode.MANAGER)) FoundationAccess.denyPermission(); }
}
