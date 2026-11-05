package com.pose.backend.customer360;

import com.pose.backend.api.PageResponse;
import com.pose.backend.identity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.util.*;

@Service
public class Customer360ReconciliationService {
    public record Difference(UUID customerId, List<String> columns) {}
    public record Reconciliation(Map<String,Long> totals, long matched, long mismatched, PageResponse<Difference> differences) {}
    private static final List<String> COLUMNS=List.of("opportunity_count","interaction_count","feedback_count","open_task_count","open_support_count","source_count","last_interaction_at");
    private final JdbcTemplate jdbc;
    public Customer360ReconciliationService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(readOnly=true)
    public Reconciliation reconcile(ActorContext actor,int page,int size) {
        PageResponse.validate(page,size);
        if(!actor.hasPermission(PermissionCode.CUSTOMER_360_READ) || !(actor.hasRole(RoleCode.ADMIN)||actor.hasRole(RoleCode.DATA_STAFF))) FoundationAccess.denyPermission();
        String sql="""
                WITH opp AS (SELECT organization_id,customer_id,count(*) n FROM opportunities WHERE customer_id IS NOT NULL GROUP BY organization_id,customer_id),
                inter AS (SELECT organization_id,customer_id,count(*) n,max(occurred_at) last_at FROM interactions WHERE customer_id IS NOT NULL GROUP BY organization_id,customer_id),
                fb AS (SELECT organization_id,customer_id,count(*) n FROM feedbacks WHERE customer_id IS NOT NULL GROUP BY organization_id,customer_id),
                task AS (SELECT organization_id,customer_id,count(*) n FROM crm_tasks WHERE customer_id IS NOT NULL AND status IN ('OPEN','IN_PROGRESS') GROUP BY organization_id,customer_id),
                support AS (SELECT organization_id,customer_id,count(*) n FROM support_requests WHERE status NOT IN ('RESOLVED','CLOSED') GROUP BY organization_id,customer_id),
                src AS (SELECT organization_id,subject_id,count(*) n FROM source_record_links WHERE subject_type='CUSTOMER' AND status='ACTIVE' GROUP BY organization_id,subject_id)
                SELECT c.id,COALESCE(opp.n,0) d_opportunity_count,v.opportunity_count v_opportunity_count,
                COALESCE(inter.n,0) d_interaction_count,v.interaction_count v_interaction_count,
                COALESCE(fb.n,0) d_feedback_count,v.feedback_count v_feedback_count,
                COALESCE(task.n,0) d_open_task_count,v.open_task_count v_open_task_count,
                COALESCE(support.n,0) d_open_support_count,v.open_support_count v_open_support_count,
                COALESCE(src.n,0) d_source_count,v.source_count v_source_count,
                inter.last_at d_last_interaction_at,v.last_interaction_at v_last_interaction_at
                FROM customers c JOIN customer_360_summary v ON v.customer_id=c.id AND v.organization_id=c.organization_id
                LEFT JOIN opp ON opp.organization_id=c.organization_id AND opp.customer_id=c.id
                LEFT JOIN inter ON inter.organization_id=c.organization_id AND inter.customer_id=c.id
                LEFT JOIN fb ON fb.organization_id=c.organization_id AND fb.customer_id=c.id
                LEFT JOIN task ON task.organization_id=c.organization_id AND task.customer_id=c.id
                LEFT JOIN support ON support.organization_id=c.organization_id AND support.customer_id=c.id
                LEFT JOIN src ON src.organization_id=c.organization_id AND src.subject_id=c.id
                WHERE c.organization_id=? ORDER BY c.id
                """;
        Map<String,Long> totals=new LinkedHashMap<>();COLUMNS.subList(0,6).forEach(c->totals.put(c,0L));
        List<Difference> all=new ArrayList<>();long[] total={0};
        jdbc.query(sql,r->{
            total[0]++;
            List<String> mismatch=new ArrayList<>();
            for(String name:COLUMNS) {
                if(name.equals("last_interaction_at")) {
                    if(!Objects.equals(r.getTimestamp("d_"+name),r.getTimestamp("v_"+name))) mismatch.add(name);
                } else {
                    long direct=r.getLong("d_"+name), view=r.getLong("v_"+name);
                    totals.merge(name,direct,Long::sum);
                    if(direct!=view) mismatch.add(name);
                }
            }
            if(!mismatch.isEmpty()) all.add(new Difference(r.getObject("id",UUID.class),List.copyOf(mismatch)));
        },actor.organizationId());
        int start=(int)Math.min((long)page*size,all.size()),end=Math.min(start+size,all.size());
        return new Reconciliation(totals,total[0]-all.size(),all.size(),new PageResponse<>(all.subList(start,end),page,size,all.size()));
    }
}
