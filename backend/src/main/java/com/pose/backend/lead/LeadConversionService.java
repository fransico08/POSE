package com.pose.backend.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.crm.*;
import com.pose.backend.customer.*;
import com.pose.backend.identity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class LeadConversionService {
    private final FoundationAccess access; private final CrmAccess crm; private final CrmJdbc db;
    private final LeadRepository leads; private final CustomerRepository customers; private final FoundationAuditRepository audit;
    private final JdbcTemplate jdbc; private final Clock clock;
    public LeadConversionService(FoundationAccess access,CrmAccess crm,CrmJdbc db,LeadRepository leads,
                                 CustomerRepository customers,FoundationAuditRepository audit,JdbcTemplate jdbc,Clock clock) {
        this.access=access;this.crm=crm;this.db=db;this.leads=leads;this.customers=customers;this.audit=audit;this.jdbc=jdbc;this.clock=clock;
    }
    @Transactional public LeadConversionResponse convert(ActorContext incoming,UUID id,JsonNode body) {
        ActorContext actor=access.lockAndRefresh(incoming); LeadResponse before=findConvertible(actor,id);
        jdbc.queryForObject("SELECT id FROM leads WHERE organization_id=? AND id=? FOR UPDATE",UUID.class,actor.organizationId(),id);
        before=findConvertible(actor,id);
        LeadService.requireOpen(before);
        JsonInput in=JsonInput.of(body,"expectedUpdatedAt","customerType","address","notes");
        if(!CrmInput.instant(in,"expectedUpdatedAt").equals(before.updatedAt())) throw ApiRequestException.conflict("LEAD_CHANGED");
        if(!"QUALIFIED".equals(before.status())) throw ApiRequestException.conflict("LEAD_STATE_CONFLICT");
        String type=CrmInput.text(in,"customerType",20,true,false);
        if(!Set.of("INDIVIDUAL","BUSINESS").contains(type)) throw JsonInput.invalid("customerType","Loại khách hàng không hợp lệ.");
        String address=CrmInput.text(in,"address",500,false,true);
        String notes=in.has("notes")?CrmInput.text(in,"notes",4000,false,true):before.notes();
        if(!crm.eligibleOwner(actor.organizationId(),before.ownerUserId(),before.offeringId())) throw ApiRequestException.conversionOwner();
        ContactInput contact=jdbc.queryForObject("SELECT email,normalized_email,phone,normalized_phone FROM leads WHERE organization_id=? AND id=?",
                (rs,n)->new ContactInput(rs.getString("email"),rs.getString("normalized_email"),rs.getString("phone"),rs.getString("normalized_phone")),actor.organizationId(),id);
        List<UUID> matches=db.duplicateIds("customers",actor.organizationId(),contact.normalizedEmail(),contact.normalizedPhone(),null,false);
        UUID customerId; boolean created=matches.isEmpty(); Instant now=clock.instant();
        if(created) {
            customerId=UUID.randomUUID();
            customers.insert(customerId,actor.organizationId(),before.ownerUserId(),before.displayName(),contact,type,address,notes,before.offeringId(),now);
            customers.insertInitialAssignment(UUID.randomUUID(),actor.organizationId(),customerId,before.ownerUserId(),actor.userId(),now);
            audit.record(actor,"CUSTOMER_CREATED","CUSTOMER",customerId,null,Map.of("status","ACTIVE"),now);
        } else {
            UUID visible=matches.size()==1?visibleCustomer(actor,matches.getFirst()):null;
            if(matches.size()!=1 || !customerInSalesScope(actor.organizationId(),matches.getFirst(),before.ownerUserId())) throw ApiRequestException.conversionConflict(visible);
            customerId=matches.getFirst();
        }
        List<OpportunityOwner> open=jdbc.query("SELECT id,owner_user_id FROM opportunities WHERE organization_id=? AND lead_id=? AND stage IN ('NEW','QUALIFIED','PROPOSAL') ORDER BY id",
                (rs,n)->new OpportunityOwner(rs.getObject("id",UUID.class),rs.getObject("owner_user_id",UUID.class)),actor.organizationId(),id);
        for(OpportunityOwner opportunity:open) if(!customerInSalesScope(actor.organizationId(),customerId,opportunity.owner())) throw ApiRequestException.conversionConflict(null);
        int movedInteractions=moveWork("interactions",actor.organizationId(),id,customerId,open,now,false);
        int movedTasks=moveWork("crm_tasks",actor.organizationId(),id,customerId,open,now,true);
        for(OpportunityOwner opportunity:open) {
            jdbc.update("UPDATE opportunities SET customer_id=?,lead_id=NULL,updated_at=? WHERE organization_id=? AND id=?",customerId,Timestamp.from(now),actor.organizationId(),opportunity.id());
            audit.record(actor,"OPPORTUNITY_SUBJECT_CHANGED","OPPORTUNITY",opportunity.id(),Map.of("leadId",id),Map.of("customerId",customerId),now);
        }
        leads.convert(actor.organizationId(),id,customerId,now);
        audit.record(actor,"LEAD_CONVERTED","LEAD",id,Map.of("status",before.status()),Map.of("status","CONVERTED","customerId",customerId,"customerCreated",created,"movedInteractions",movedInteractions,"movedTasks",movedTasks),now);
        LeadResponse converted=findConvertible(actor,id);
        CustomerResponse customer=customers.find("c.organization_id=? AND c.archived_at IS NULL",List.of(actor.organizationId()),customerId).orElseThrow();
        return new LeadConversionResponse(converted,customer,created);
    }
    private int moveWork(String table,UUID organization,UUID lead,UUID customer,List<OpportunityOwner> opportunities,Instant now,boolean openTasks) {
        String allowed = opportunities.isEmpty() ? "opportunity_id IS NULL" :
                "(opportunity_id IS NULL OR opportunity_id IN ("+String.join(",",Collections.nCopies(opportunities.size(),"?"))+"))";
        String sql="UPDATE "+table+" SET customer_id=?,lead_id=NULL,updated_at=? WHERE organization_id=? AND lead_id=? AND "+allowed
                +(openTasks?" AND status IN ('OPEN','IN_PROGRESS')":"");
        var args=new ArrayList<Object>(); Collections.addAll(args,customer,Timestamp.from(now),organization,lead);
        opportunities.forEach(opportunity->args.add(opportunity.id()));
        return jdbc.update(sql,args.toArray());
    }
    private LeadResponse findConvertible(ActorContext actor,UUID id) {
        var roles=new ArrayList<RoleCode>();
        for(RoleCode role:List.of(RoleCode.MANAGER,RoleCode.SALES_REP))
            if(crm.roleWith(actor,role,PermissionCode.LEAD_WRITE)&&crm.roleWith(actor,role,PermissionCode.CUSTOMER_WRITE)) roles.add(role);
        if(roles.isEmpty()) FoundationAccess.denyPermission();
        var parts=new ArrayList<String>();var args=new ArrayList<Object>();args.add(actor.organizationId());
        for(RoleCode role:roles) {
            if(role==RoleCode.SALES_REP&&!actor.hasPermission(PermissionCode.OFFERING_READ)) continue;
            var s=crm.subjectRoleScope(actor.userId(),role,true);parts.add("("+s.where()+")");args.addAll(s.args());
        }
        if(parts.isEmpty()) FoundationAccess.denyScope();
        return leads.find("l.organization_id=? AND l.archived_at IS NULL AND ("+String.join(" OR ",parts)+")",args,id)
                .orElseGet(()->{FoundationAccess.denyScope();return null;});
    }
    private boolean customerInSalesScope(UUID organization,UUID id,UUID user) {
        var s=crm.subjectRoleScope(user,RoleCode.SALES_REP,false); var args=new ArrayList<Object>(); args.add(organization);args.addAll(s.args());
        return customers.find("c.organization_id=? AND c.archived_at IS NULL AND ("+s.where()+")",args,id).isPresent();
    }
    private UUID visibleCustomer(ActorContext actor,UUID id) {
        try { var s=crm.scope(actor,false,false); return customers.find(s.where(),s.args(),id).isPresent()?id:null; }
        catch(AuthorizationDeniedException ignored) { return null; }
    }
    private record OpportunityOwner(UUID id,UUID owner) {}
}
