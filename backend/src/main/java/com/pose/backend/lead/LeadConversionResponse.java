package com.pose.backend.lead;

import com.pose.backend.customer.CustomerResponse;

public record LeadConversionResponse(LeadResponse lead, CustomerResponse customer, boolean customerCreated) {}
