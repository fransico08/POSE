package com.pose.backend.identity;

import java.util.Arrays;

public enum RoleCode {
    ADMIN("System Admin"),
    MANAGER("Manager"),
    SALES_REP("Sales Staff"),
    CUSTOMER_CARE("Customer Care Staff"),
    DATA_STAFF("Data Staff");

    private final String displayName;

    RoleCode(String displayName) {
        this.displayName = displayName;
    }

    public String code() {
        return name();
    }

    public String displayName() {
        return displayName;
    }

    public static RoleCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(role -> role.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role code"));
    }
}
