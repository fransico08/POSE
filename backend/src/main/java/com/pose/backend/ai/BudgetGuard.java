package com.pose.backend.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;

@Component
public class BudgetGuard {
    private final JdbcTemplate jdbc; private final AiSettings settings; private final Clock clock;
    public BudgetGuard(JdbcTemplate jdbc,AiSettings settings,Clock clock){this.jdbc=jdbc;this.settings=settings;this.clock=clock;}
    public BigDecimal spent(UUID org) {
        Instant now=clock.instant(),start=now.atZone(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        return jdbc.queryForObject("SELECT COALESCE(sum(cost_estimate),0) FROM ai_invocations WHERE organization_id=? AND created_at>=? AND created_at<=?",BigDecimal.class,org,Timestamp.from(start),Timestamp.from(now));
    }
    public boolean blocked(UUID org) {return spent(org).compareTo(settings.budget())>=0;}
    public BigDecimal remaining(UUID org) {return settings.budget().subtract(spent(org)).max(BigDecimal.ZERO);}
}
