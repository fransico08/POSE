package com.pose.backend.customer360;

import com.pose.backend.customer.CustomerResponse;
import com.pose.backend.interaction.InteractionResponse;
import com.pose.backend.task.TaskResponse;
import java.time.*;
import java.util.*;

public record Customer360Response(Customer customer, Sources sources, Leads leads, Opportunities opportunities,
                                  Interactions interactions, Tasks tasks, Metric feedback, Metric support, Instant refreshedAt) {
    public record Name(UUID id, String name) {}
    public record Customer(CustomerResponse profile, Name owner, List<Name> ownerTeams, List<Name> careAssignees) {}
    public record Sources(long count, List<String> systems) {}
    public record Lead(UUID id, String name, String status, Instant convertedAt) {}
    public record Leads(boolean restricted, Long count, List<Lead> items) {}
    public record Opportunity(UUID id, String title, String stage, String expectedValue, LocalDate expectedCloseDate) {}
    public record Opportunities(boolean restricted, Map<String,Long> stages, String openExpectedValue,
                                String wonFinalValue, List<Opportunity> items) {}
    public record Interactions(boolean restricted, Long count, Instant lastInteractionAt, List<InteractionResponse> items) {}
    public record Tasks(boolean restricted, Map<String,Long> statuses, Long overdue, List<TaskResponse> items) {}
    public record Metric(boolean restricted, Long count, Double averageRating, List<Object> items) {
        public Metric(boolean restricted, Long count, Double averageRating) { this(restricted,count,averageRating,List.of()); }
    }
}
