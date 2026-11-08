package com.pose.backend.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.crm.*;
import com.pose.backend.identity.*;
import com.pose.backend.salesassignment.SalesAssignmentCandidateResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class CustomerService {
    private static final Set<String> TYPES=Set.of("INDIVIDUAL","BUSINESS");
    private static final Set<String> STATUSES=Set.of("ACTIVE","INACTIVE");
    private final CustomerRepository customers; private final CrmJdbc db; private final CrmAccess scope;
    private final FoundationAccess access; private final FoundationAuditRepository audit; private final Clock clock;
    public CustomerService(CustomerRepository customers,CrmJdbc db,CrmAccess scope,FoundationAccess access,FoundationAuditRepository audit,Clock clock) {
        this.customers=customers; this.db=db; this.scope=scope; this.access=access; this.audit=audit; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public PageResponse<CustomerResponse> list(ActorContext actor,int page,int size,String search,String status,String type,UUID ownerId) {
        PageResponse.validate(page,size); var s=scope.scope(actor,false,false); var w=new StringBuilder(s.where()); var args=new ArrayList<>(s.args());
        CrmJdbc.search(w,args,search,"c");
        if(status!=null) { if(!STATUSES.contains(status)) throw JsonInput.invalid("status","Trạng thái không hợp lệ."); w.append(" AND c.status=?"); args.add(status); }
        if(type!=null) { if(!TYPES.contains(type)) throw JsonInput.invalid("customerType","Loại khách hàng không hợp lệ."); w.append(" AND c.customer_type=?"); args.add(type); }
        if(ownerId!=null) { requireOwnerFilter(actor); w.append(" AND c.owner_user_id=?"); args.add(ownerId); }
        return new PageResponse<>(customers.list(w.toString(),args,page,size),page,size,customers.count(w.toString(),args));
    }
    @Transactional(readOnly=true) public CustomerResponse get(ActorContext actor,UUID id) { return find(actor,id,false); }
    @Transactional(readOnly=true) public List<SalesAssignmentCandidateResponse> candidates(ActorContext actor,UUID offering) { return scope.candidates(actor,offering,false); }
    @Transactional(readOnly=true) public List<SalesAssignmentCandidateResponse> owners(ActorContext actor) { return db.owners(actor,scope,false); }
    @Transactional public CustomerResponse create(ActorContext incoming,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming);
        scope.requireCreateRole(actor,false);
        JsonInput in=JsonInput.of(body,"displayName","email","phone","customerType","relatedOfferingId","address","notes","ownerUserId");
        String name=CrmInput.text(in,"displayName",180,true,false); ContactInput contact=contact(in,null,null);
        String type=type(CrmInput.text(in,"customerType",20,true,false)); UUID offering=CrmInput.uuid(in,"relatedOfferingId");
        String address=CrmInput.text(in,"address",500,false,true),notes=CrmInput.text(in,"notes",4000,false,true);
        UUID requested=in.has("ownerUserId")?in.uuid("ownerUserId"):null;
        UUID owner=scope.ownerForCreate(actor,false,offering,requested); duplicates(actor,contact,null);
        UUID id=UUID.randomUUID(); Instant now=clock.instant();
        try {
            customers.insert(id,actor.organizationId(),owner,name,contact,type,address,notes,offering,now);
        } catch (DataIntegrityViolationException e) { if(uniqueContact(e)) throw ApiRequestException.duplicate("CUSTOMER_DUPLICATE",null); throw e; }
        customers.insertInitialAssignment(UUID.randomUUID(),actor.organizationId(),id,owner,actor.userId(),now);
        audit.record(actor,"CUSTOMER_CREATED","CUSTOMER",id,null,Map.of("status","ACTIVE"),now);
        return find(actor,id,true);
    }
    @Transactional public CustomerResponse patch(ActorContext incoming,UUID id,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming); CustomerResponse before=find(actor,id,true);
        JsonInput in=JsonInput.of(body,"displayName","email","phone","customerType","relatedOfferingId","address","notes","expectedUpdatedAt");
        if(!CrmInput.instant(in,"expectedUpdatedAt").equals(before.updatedAt())) throw ApiRequestException.conflict("CUSTOMER_CHANGED");
        String name=in.has("displayName")?CrmInput.text(in,"displayName",180,true,false):before.displayName();
        ContactInput contact=contact(in,before.email(),before.phone());
        String type=in.has("customerType")?type(CrmInput.text(in,"customerType",20,true,false)):before.customerType();
        String address=in.has("address")?CrmInput.text(in,"address",500,false,true):before.address();
        String notes=in.has("notes")?CrmInput.text(in,"notes",4000,false,true):before.notes();
        UUID offering=in.has("relatedOfferingId")?in.uuid("relatedOfferingId"):before.relatedOfferingId();
        if (!Objects.equals(offering,before.relatedOfferingId())) {
            if (!scope.eligibleOwner(actor.organizationId(),before.ownerUserId(),offering)) FoundationAccess.denyScope();
            boolean admin=scope.roleWith(actor,RoleCode.ADMIN,PermissionCode.CUSTOMER_WRITE);
            boolean manager=scope.roleWith(actor,RoleCode.MANAGER,PermissionCode.CUSTOMER_WRITE) && scope.managedOffering(actor,offering,false);
            boolean sales=scope.roleWith(actor,RoleCode.SALES_REP,PermissionCode.CUSTOMER_WRITE) && scope.eligibleOwner(actor.organizationId(),actor.userId(),offering);
            if (!admin && !manager && !sales) FoundationAccess.denyScope();
        }
        duplicates(actor,contact,id); Instant now=clock.instant();
        try {
            customers.update(actor.organizationId(),id,name,contact,type,address,notes,offering,now);
        } catch (DataIntegrityViolationException e) { if(uniqueContact(e)) throw ApiRequestException.duplicate("CUSTOMER_DUPLICATE",null); throw e; }
        audit.record(actor,"CUSTOMER_UPDATED","CUSTOMER",id,Map.of("status",before.status()),Map.of("status",before.status()),now);
        return find(actor,id,true);
    }
    @Transactional public CustomerReassignResponse reassign(ActorContext incoming,UUID id,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming);
        if(!actor.hasPermission(PermissionCode.CUSTOMER_WRITE)) FoundationAccess.denyPermission();
        boolean admin=scope.roleWith(actor,RoleCode.ADMIN,PermissionCode.CUSTOMER_WRITE);
        boolean manager=scope.roleWith(actor,RoleCode.MANAGER,PermissionCode.CUSTOMER_WRITE);
        if(!admin && !manager) FoundationAccess.denyPermission();
        CustomerResponse before=find(actor,id,true);
        customers.lock(actor.organizationId(),id);
        before=find(actor,id,true);
        if(before.relatedOfferingId()!=null) {
            scope.requireManagedOffering(actor,before.relatedOfferingId(),admin);
        }
        JsonInput in=JsonInput.of(body,"newOwnerUserId","reason");
        UUID newOwner=CrmInput.uuid(in,"newOwnerUserId");
        String reason=CrmInput.text(in,"reason",1000,true,false);
        if(before.relatedOfferingId()==null) {
            throw ApiRequestException.unprocessable("relatedOfferingId","Hồ sơ chưa có sản phẩm/dịch vụ liên quan nên chưa đổi được người phụ trách.");
        }
        if(newOwner.equals(before.ownerUserId())) {
            throw ApiRequestException.unprocessable("newOwnerUserId","Người phụ trách mới trùng với người phụ trách hiện tại.");
        }
        if(!scope.eligibleOwner(actor.organizationId(),newOwner,before.relatedOfferingId())) {
            throw ApiRequestException.unprocessable("newOwnerUserId","Người phụ trách không đủ điều kiện.");
        }
        Map<UUID,Boolean> eligible=new HashMap<>();
        long blocked=customers.findOpenOpportunityOfferings(actor.organizationId(),id,before.ownerUserId()).stream()
                .filter(row->!eligible.computeIfAbsent(row.offeringId(),offering->scope.eligibleOwner(actor.organizationId(),newOwner,offering)))
                .map(CustomerRepository.OpportunityOffering::opportunityId).distinct().count();
        if(blocked>0) throw ApiRequestException.unprocessable("newOwnerUserId","Người phụ trách mới chưa được phân công sản phẩm/dịch vụ của "+blocked+" cơ hội đang mở trên hồ sơ này.");
        Instant candidate=clock.instant();
        Instant now=candidate.isAfter(before.updatedAt())?candidate:before.updatedAt().plusMillis(1);
        UUID oldOwner=before.ownerUserId();
        List<UUID> movedOpportunities=customers.findOpenOpportunitiesByOwner(actor.organizationId(),id,oldOwner);
        for(UUID oppId:movedOpportunities) {
            customers.updateOpportunityOwner(actor.organizationId(),oppId,newOwner,now);
            audit.record(actor,"OPPORTUNITY_ASSIGNED","OPPORTUNITY",oppId,Map.of("ownerUserId",oldOwner),Map.of("ownerUserId",newOwner),now);
        }
        List<UUID> movedTasks=customers.findOpenTasksByOwner(actor.organizationId(),id,oldOwner);
        for(UUID taskId:movedTasks) {
            customers.updateTaskOwner(actor.organizationId(),taskId,newOwner,now);
            audit.record(actor,"TASK_ASSIGNED","CRM_TASK",taskId,Map.of("ownerUserId",oldOwner),Map.of("ownerUserId",newOwner),now);
        }
        UUID handoverId=UUID.randomUUID();
        customers.insertHandoverRequest(handoverId,actor.organizationId(),id,actor.userId(),newOwner,reason,now);
        customers.replaceSalesAssignmentOnHandover(UUID.randomUUID(),actor.organizationId(),id,newOwner,actor.userId(),handoverId,now);
        customers.updateOwner(actor.organizationId(),id,newOwner,now);
        audit.record(actor,"OWNER_REASSIGNED","CUSTOMER",id,
                Map.of("ownerUserId",oldOwner),
                Map.of("ownerUserId",newOwner,"handoverId",handoverId,"movedOpportunities",movedOpportunities.size(),"movedTasks",movedTasks.size()),
                now);
        CustomerResponse updated=customers.find("c.organization_id=? AND c.archived_at IS NULL",List.of(actor.organizationId()),id).orElseThrow();
        return CustomerReassignResponse.from(updated,handoverId,movedOpportunities,movedTasks);
    }
    private CustomerResponse find(ActorContext actor,UUID id,boolean write) {
        var s=scope.scope(actor,false,write); return customers.find(s.where(),s.args(),id).orElseGet(()->{ FoundationAccess.denyScope(); return null; });
    }
    private void duplicates(ActorContext actor,ContactInput contact,UUID exclude) {
        List<UUID> ids=db.duplicateIds("customers",actor.organizationId(),contact.normalizedEmail(),contact.normalizedPhone(),exclude,false);
        if(ids.isEmpty()) return; UUID visible=null;
        try { var s=scope.scope(actor,false,false); for(UUID id:ids) if(customers.find(s.where(),s.args(),id).isPresent()) { visible=id; break; } }
        catch (AuthorizationDeniedException ignored) { }
        throw ApiRequestException.duplicate("CUSTOMER_DUPLICATE",visible);
    }
    private static boolean uniqueContact(Throwable e) {
        for(Throwable t=e;t!=null;t=t.getCause()) if(t instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
            String message=String.valueOf(sql.getMessage()).toLowerCase(Locale.ROOT);
            return message.contains("uq_customers_org_email") || message.contains("uq_customers_org_phone") || message.contains("normalized_email") || message.contains("normalized_phone");
        }
        return false;
    }
    private static ContactInput contact(JsonInput in,String oldEmail,String oldPhone) {
        return ContactInput.of(in.has("email")?CrmInput.text(in,"email",254,false,true):oldEmail,
                in.has("phone")?CrmInput.text(in,"phone",40,false,true):oldPhone);
    }
    private static String type(String t) { if(!TYPES.contains(t)) throw JsonInput.invalid("customerType","Loại khách hàng không hợp lệ."); return t; }
    private void requireOwnerFilter(ActorContext actor) {
        if(!actor.hasPermission(PermissionCode.CUSTOMER_READ) || (!actor.hasRole(RoleCode.ADMIN) && !actor.hasRole(RoleCode.MANAGER))) FoundationAccess.denyPermission();
    }
}
