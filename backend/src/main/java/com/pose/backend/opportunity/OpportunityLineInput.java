package com.pose.backend.opportunity;

import java.math.BigDecimal;
import java.util.UUID;

record OpportunityLineInput(UUID offeringId, int quantity, BigDecimal unitPrice,
                            BigDecimal discountPercent, BigDecimal lineValue) {
}
