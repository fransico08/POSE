package com.pose.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.api.JsonInput;
import com.pose.backend.customer360.Customer360Service;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.lead.LeadService;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class AiContextBuilder {
    private final Customer360Service customers; private final LeadService leads; private final ObjectMapper json;
    public AiContextBuilder(Customer360Service customers,LeadService leads,ObjectMapper json) {this.customers=customers;this.leads=leads;this.json=json;}
    public String build(ActorContext actor,String subjectType,UUID subjectId) { return build(actor,subjectType,subjectId,AiUseCase.C360_SUMMARY); }
    public String build(ActorContext actor,String subjectType,UUID subjectId,AiUseCase useCase) {
        Map<String,Object> data=new LinkedHashMap<>();
        if("CUSTOMER".equals(subjectType)) {
            var c=customers.profile(actor,subjectId);
            data.put("name",c.customer().profile().displayName());
            if(useCase==AiUseCase.C360_SUMMARY || useCase==AiUseCase.HANDOVER_BRIEF) {data.put("email",c.customer().profile().email());data.put("phone",c.customer().profile().phone());}
            data.put("notes",c.customer().profile().notes());
            data.put("status",c.customer().profile().status());
            data.put("interactions",c.interactions().items().stream().map(i->{Map<String,Object> row=new LinkedHashMap<>();row.put("subject",i.subject());row.put("content",i.content());row.put("occurredAt",i.occurredAt());return row;}).toList());
            if(useCase==AiUseCase.C360_SUMMARY || useCase==AiUseCase.HANDOVER_BRIEF) data.put("tasks",c.tasks().items().stream().map(t->{Map<String,Object> row=new LinkedHashMap<>();row.put("title",t.title());row.put("status",t.status());row.put("dueAt",t.dueAt());return row;}).toList());
        } else if("LEAD".equals(subjectType)) {
            var l=leads.get(actor,subjectId); data.put("name",l.displayName());
            if(useCase==AiUseCase.C360_SUMMARY || useCase==AiUseCase.HANDOVER_BRIEF) {data.put("email",l.email());data.put("phone",l.phone());}
            data.put("source",l.source());data.put("notes",l.notes());data.put("status",l.status());data.put("offering",l.offeringName());
        } else throw JsonInput.invalid("subjectType","Loại hồ sơ không hợp lệ.");
        try {return json.writeValueAsString(data);} catch(Exception ex) {throw new IllegalStateException("Cannot serialize AI context",ex);}
    }
}
