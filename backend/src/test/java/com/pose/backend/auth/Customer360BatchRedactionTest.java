package com.pose.backend.auth;

import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.crm.CrmAccess;
import com.pose.backend.crm.CrmWorkAccess;
import com.pose.backend.identity.*;
import com.pose.backend.interaction.*;
import com.pose.backend.task.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Customer360BatchRedactionTest {
    private static final UUID ID=new UUID(0,981);
    private static final Instant NOW=Instant.parse("2026-10-01T00:00:00Z");
    private static final ActorContext DATA=new ActorContext(ID,ID,Set.of(RoleCode.DATA_STAFF),
            Set.of(PermissionCode.INTERACTION_READ,PermissionCode.TASK_MANAGE),Set.of());

    @Test void permissionChecksDoNotGrowWithInteractionOrTaskListSize() {
        long oneInteraction=interactionChecks(1), manyInteractions=interactionChecks(10);
        long oneTask=taskChecks(1), manyTasks=taskChecks(10);
        assertThat(oneInteraction).isPositive(); assertThat(manyInteractions).isEqualTo(oneInteraction);
        assertThat(oneTask).isPositive(); assertThat(manyTasks).isEqualTo(oneTask);
    }
    private long interactionChecks(int count) {
        var work=mock(CrmWorkAccess.class); var repository=mock(InteractionRepository.class);
        when(work.subjectScope(eq(DATA),eq(PermissionCode.INTERACTION_READ),eq(false),eq("i"),eq("i.customer_id"),eq("i.lead_id")))
                .thenReturn(new CrmAccess.Scope("1=1",List.of()));
        var item=new InteractionResponse(ID,ID,null,null,ID,"User","CALL","PHONE","subject","content",null,
                "CARE_VISIBLE",NOW,NOW,NOW,null,null,null);
        when(repository.list(anyString(),anyList(),eq(0),eq(10))).thenReturn(Collections.nCopies(count,item));
        when(repository.count(anyString(),anyList())).thenReturn((long)count);
        var service=new InteractionService(repository,work,mock(FoundationAccess.class),mock(FoundationAuditRepository.class),mock(JdbcTemplate.class),Clock.fixed(NOW,ZoneOffset.UTC));
        var result=service.list(DATA,0,10,ID,null,null,null,null,null,null,null);
        assertThat(result.items()).hasSize(count).allSatisfy(row->assertThat(row.content()).isNull());
        return mockingDetails(work).getInvocations().stream().filter(i->i.getMethod().getName().equals("hasRolePermission")).count();
    }
    private long taskChecks(int count) {
        var work=mock(CrmWorkAccess.class); var repository=mock(TaskRepository.class);
        when(work.taskScope(DATA,false)).thenReturn(new CrmAccess.Scope("1=1",List.of()));
        var item=new TaskResponse(ID,ID,null,null,ID,"User",ID,"title","description","MEDIUM","OPEN",NOW,
                null,null,"MANUAL",null,NOW,NOW,null,null,null);
        when(repository.list(anyString(),anyList(),eq(0),eq(10))).thenReturn(Collections.nCopies(count,item));
        when(repository.count(anyString(),anyList())).thenReturn((long)count);
        var service=new TaskService(repository,work,mock(CrmAccess.class),mock(FoundationAccess.class),mock(FoundationAuditRepository.class),mock(JdbcTemplate.class),Clock.fixed(NOW,ZoneOffset.UTC));
        var result=service.list(DATA,0,10,ID,null,null,null,null,null,null,null);
        assertThat(result.items()).hasSize(count).allSatisfy(row->assertThat(row.description()).isNull());
        return mockingDetails(work).getInvocations().stream().filter(i->i.getMethod().getName().equals("hasRolePermission")).count();
    }
}
