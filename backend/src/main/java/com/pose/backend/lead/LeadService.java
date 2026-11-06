package com.pose.backend.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.crm.*;
import com.pose.backend.identity.*;
import com.pose.backend.salesassignment.SalesAssignmentCandidateResponse;
import com.pose.workflow.LeadQualifiedTrigger;
import com.pose.workflow.TransactionalOutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class LeadService {
    private static final Set<String> SOURCES=Set.of("WEBSITE","REFERRAL","EVENT","SOCIAL_MEDIA","PHONE","WALK_IN","OTHER");
    private static final Set<String> STATUSES=Set.of("NEW","QUALIFIED","CONVERTED","DISQUALIFIED");
    private final LeadRepository leads; private final CrmJdbc db; private final CrmAccess scope;
    private final FoundationAccess access; private final FoundationAuditRepository audit; private final Clock clock;
    private final TransactionalOutboxPublisher outbox;
    public LeadService(LeadRepository leads,CrmJdbc db,CrmAccess scope,FoundationAccess access,FoundationAuditRepository audit,Clock clock,TransactionalOutboxPublisher outbox) {
        this.leads=leads; this.db=db; this.scope=scope; this.access=access; this.audit=audit; this.clock=clock; this.outbox=outbox;
    }
    @Transactional(readOnly=true)
    public PageResponse<LeadResponse> list(ActorContext actor,int page,int size,String search,String status,UUID offeringId,UUID ownerId) {
        PageResponse.validate(page,size); var s=scope.scope(actor,true,false); var w=new StringBuilder(s.where()); var args=new ArrayList<>(s.args());
        CrmJdbc.search(w,args,search,"l");
        if(status!=null) { if(!STATUSES.contains(status)) throw JsonInput.invalid("status","Trạng thái không hợp lệ."); w.append(" AND l.status=?"); args.add(status); }
        if(offeringId!=null) { w.append(" AND l.offering_id=?"); args.add(offeringId); }
        if(ownerId!=null) { requireOwnerFilter(actor); w.append(" AND l.owner_user_id=?"); args.add(ownerId); }
        return new PageResponse<>(leads.list(w.toString(),args,page,size),page,size,leads.count(w.toString(),args));
    }
    @Transactional(readOnly=true) public LeadResponse get(ActorContext actor,UUID id) { return find(actor,id,false); }
    @Transactional(readOnly=true) public List<SalesAssignmentCandidateResponse> candidates(ActorContext actor,UUID offering) { return scope.candidates(actor,offering,true); }
    @Transactional(readOnly=true) public List<SalesAssignmentCandidateResponse> owners(ActorContext actor) { return db.owners(actor,scope,true); }
    @Transactional public LeadResponse create(ActorContext incoming,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming);
        scope.requireCreateRole(actor,true);
        JsonInput in=JsonInput.of(body,"displayName","email","phone","source","offeringId","notes","ownerUserId");
        String name=CrmInput.text(in,"displayName",180,true,false); ContactInput contact=contact(in,null,null);
        String source=source(CrmInput.text(in,"source",30,true,false)); UUID offering=CrmInput.uuid(in,"offeringId");
        String notes=CrmInput.text(in,"notes",4000,false,true); UUID requested=in.has("ownerUserId")?in.uuid("ownerUserId"):null;
        UUID owner=scope.ownerForCreate(actor,true,offering,requested); duplicates(actor,contact,null);
        UUID id=UUID.randomUUID(); Instant now=clock.instant();
        leads.insert(id,actor.organizationId(),owner,offering,name,contact,source,notes,now);
        audit.record(actor,"LEAD_CREATED","LEAD",id,null,Map.of("status","NEW"),now);
        return find(actor,id,true);
    }
    @Transactional public LeadResponse patch(ActorContext incoming,UUID id,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming); LeadResponse before=find(actor,id,true); requireOpen(before);
        JsonInput in=JsonInput.of(body,"displayName","email","phone","source","notes","offeringId","expectedUpdatedAt");
        if(!CrmInput.instant(in,"expectedUpdatedAt").equals(before.updatedAt())) throw ApiRequestException.conflict("LEAD_CHANGED");
        String name=in.has("displayName")?CrmInput.text(in,"displayName",180,true,false):before.displayName();
        ContactInput contact=contact(in,before.email(),before.phone());
        String source=in.has("source")?source(CrmInput.text(in,"source",30,true,false)):before.source();
        String notes=in.has("notes")?CrmInput.text(in,"notes",4000,false,true):before.notes();
        UUID offering=in.has("offeringId")?in.uuid("offeringId"):before.offeringId();
        if (!offering.equals(before.offeringId())) {
            if (!scope.eligibleOwner(actor.organizationId(),before.ownerUserId(),offering)) FoundationAccess.denyScope();
            boolean admin=scope.roleWith(actor,RoleCode.ADMIN,PermissionCode.LEAD_WRITE);
            boolean manager=scope.roleWith(actor,RoleCode.MANAGER,PermissionCode.LEAD_WRITE) && scope.managedOffering(actor,offering,false);
            boolean sales=scope.roleWith(actor,RoleCode.SALES_REP,PermissionCode.LEAD_WRITE) && before.ownerUserId().equals(actor.userId());
            if (!admin && !manager && !sales) FoundationAccess.denyScope();
        }
        duplicates(actor,contact,id); Instant now=clock.instant();
        leads.update(actor.organizationId(),id,name,contact,source,notes,offering,now);
        audit.record(actor,"LEAD_UPDATED","LEAD",id,Map.of("status",before.status()),Map.of("status",before.status()),now);
        return find(actor,id,true);
    }
    @Transactional public LeadResponse transition(ActorContext incoming,UUID id,JsonNode body,boolean qualify) {
        ActorContext actor=access.lockAndRefresh(incoming); LeadResponse before=find(actor,id,true); requireOpen(before);
        JsonInput in=JsonInput.of(body,qualify?new String[]{"expectedUpdatedAt"}:new String[]{"expectedUpdatedAt","reason"});
        if(!CrmInput.instant(in,"expectedUpdatedAt").equals(before.updatedAt())) throw ApiRequestException.conflict("LEAD_CHANGED");
        if(qualify && !"NEW".equals(before.status())) throw ApiRequestException.conflict("LEAD_STATE_CONFLICT");
        String reason=qualify?null:CrmInput.text(in,"reason",4000,true,false); Instant now=clock.instant();
        String newStatus=qualify?"QUALIFIED":"DISQUALIFIED";
        leads.transition(actor.organizationId(),id,newStatus,qualify?now:before.qualifiedAt(),reason,now);
        if (qualify) outbox.publishLeadQualified(new LeadQualifiedTrigger(actor.organizationId(),id,before.ownerUserId(),actor.userId(),now));
        audit.record(actor,qualify?"LEAD_QUALIFIED":"LEAD_DISQUALIFIED","LEAD",id,Map.of("status",before.status()),Map.of("status",newStatus),now);
        return find(actor,id,true);
    }
    private LeadResponse find(ActorContext actor,UUID id,boolean write) {
        var s=scope.scope(actor,true,write); return leads.find(s.where(),s.args(),id).orElseGet(()->{ FoundationAccess.denyScope(); return null; });
    }
    private void duplicates(ActorContext actor,ContactInput contact,UUID exclude) {
        List<UUID> ids=db.duplicateIds("leads",actor.organizationId(),contact.normalizedEmail(),contact.normalizedPhone(),exclude,true);
        if(ids.isEmpty()) return; UUID visible=null;
        try { var s=scope.scope(actor,true,false); for(UUID id:ids) if(leads.find(s.where(),s.args(),id).isPresent()) { visible=id; break; } }
        catch (AuthorizationDeniedException ignored) { }
        throw ApiRequestException.duplicate("LEAD_DUPLICATE",visible);
    }
    private static ContactInput contact(JsonInput in,String oldEmail,String oldPhone) {
        return ContactInput.of(in.has("email")?CrmInput.text(in,"email",254,false,true):oldEmail,
                in.has("phone")?CrmInput.text(in,"phone",40,false,true):oldPhone);
    }
    private static String source(String s) { if(!SOURCES.contains(s)) throw JsonInput.invalid("source","Nguồn không hợp lệ."); return s; }
    static void requireOpen(LeadResponse lead) { if(Set.of("CONVERTED","DISQUALIFIED").contains(lead.status())) throw ApiRequestException.conflict("LEAD_CLOSED"); }
    private void requireOwnerFilter(ActorContext actor) {
        if(!actor.hasPermission(PermissionCode.LEAD_READ) || (!actor.hasRole(RoleCode.ADMIN) && !actor.hasRole(RoleCode.MANAGER))) FoundationAccess.denyPermission();
    }
}
