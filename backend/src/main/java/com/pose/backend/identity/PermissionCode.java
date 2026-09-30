package com.pose.backend.identity;

import java.util.Arrays;

public enum PermissionCode {
    IDENTITY_USER_MANAGE("identity.user.manage"),
    IDENTITY_USER_READ("identity.user.read"),
    IDENTITY_ROLE_MANAGE("identity.role.manage"),
    ORGANIZATION_TEAM_MANAGE("organization.team.manage"),
    OFFERING_MANAGE("offering.manage"),
    SALES_ASSIGNMENT_MANAGE("sales_assignment.manage"),
    CUSTOMER_READ("customer.read"),
    CUSTOMER_WRITE("customer.write"),
    LEAD_READ("lead.read"),
    LEAD_WRITE("lead.write"),
    OPPORTUNITY_READ("opportunity.read"),
    OPPORTUNITY_WRITE("opportunity.write"),
    OPPORTUNITY_REOPEN("opportunity.reopen"),
    INTERACTION_READ("interaction.read"),
    INTERACTION_WRITE("interaction.write"),
    INTERACTION_SENSITIVE_READ("interaction.sensitive.read"),
    FEEDBACK_WRITE("feedback.write"),
    TASK_MANAGE("task.manage"),
    CUSTOMER_360_READ("customer_360.read"),
    IMPORT_PREPARE("import.prepare"),
    IMPORT_APPROVE("import.approve"),
    IMPORT_RETRY("import.retry"),
    DASHBOARD_READ("dashboard.read"),
    AUDIT_READ("audit.read"),
    SUPPORT_REQUEST_MANAGE("support_request.manage"),
    SUPPORT_REQUEST_ESCALATION_HANDLE("support_request.escalation.handle"),
    HANDOVER_REQUEST("handover.request"),
    HANDOVER_APPROVE("handover.approve");

    private final String code;

    PermissionCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PermissionCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(permission -> permission.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown permission code"));
    }
}
