package com.pose.backend.crm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class CrmWorkReferences {
    private CrmWorkReferences() {}

    public record Subject(UUID id, String name) {}
    public record Opportunity(UUID id, String title) {}

    public static Subject customer(ResultSet row) throws SQLException {
        UUID id = row.getObject("work_customer_id", UUID.class);
        return id == null ? null : new Subject(id, row.getString("work_customer_name"));
    }
    public static Subject lead(ResultSet row) throws SQLException {
        UUID id = row.getObject("work_lead_id", UUID.class);
        return id == null ? null : new Subject(id, row.getString("work_lead_name"));
    }
    public static Opportunity opportunity(ResultSet row) throws SQLException {
        UUID id = row.getObject("work_opportunity_id", UUID.class);
        return id == null ? null : new Opportunity(id, row.getString("work_opportunity_title"));
    }
}
