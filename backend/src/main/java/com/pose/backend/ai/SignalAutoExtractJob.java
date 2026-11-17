package com.pose.backend.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;

@Component
public class SignalAutoExtractJob {
    private record Candidate(UUID organizationId,UUID messageId,String content,UUID leadId,UUID customerId,Instant occurredAt) {}
    private final JdbcTemplate jdbc;
    private final SignalExtractionProcessor processor;
    private final AiSettings settings;
    private final Clock clock;
    private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"signal-auto-extract");thread.setDaemon(true);return thread;});
    public SignalAutoExtractJob(JdbcTemplate jdbc,SignalExtractionProcessor processor,AiSettings settings,Clock clock) {
        this.jdbc=jdbc;this.processor=processor;this.settings=settings;this.clock=clock;
        if(settings.signalAutoExtract()) {
            settings.signalAutoBatch();
            java.time.Duration.ofHours(settings.signalAutoLookbackHours());
            settings.signalAutoMaxAttempts();
            settings.signalAutoRetryDelayMinutes();
        }
    }

    @Scheduled(fixedDelayString="${pose.ai.signal.auto-interval-ms:60000}",initialDelayString="${pose.ai.signal.auto-interval-ms:60000}")
    public void schedule() {
        if(!begin())return;
        try {worker.execute(this::processStarted);}
        catch(RuntimeException ex){running.set(false);throw ex;}
    }
    public void process() {
        if(!begin())return;
        processStarted();
    }
    private boolean begin() {return settings.enabled() && settings.signalAutoExtract() && running.compareAndSet(false,true);}
    @PreDestroy public void shutdown() {worker.shutdownNow();}
    private void processStarted() {
        try {
            int limit=settings.signalAutoBatch(),maxAttempts=settings.signalAutoMaxAttempts(),processed=0;
            Instant now=clock.instant(),since=now.minus(java.time.Duration.ofHours(settings.signalAutoLookbackHours()));
            Instant retryBefore=now.minus(java.time.Duration.ofMinutes(settings.signalAutoRetryDelayMinutes()));
            Set<UUID> blocked=new HashSet<>(); Candidate cursor=null;
            while(processed<limit) {
                String seek=cursor==null?"":" AND (m.occurred_at<? OR (m.occurred_at=? AND m.id<?))";
                List<Object> args=new ArrayList<>(List.of(Timestamp.from(since),Timestamp.from(now),maxAttempts,Timestamp.from(retryBefore)));
                if(cursor!=null){args.add(Timestamp.from(cursor.occurredAt()));args.add(Timestamp.from(cursor.occurredAt()));args.add(cursor.messageId());}
                args.add(50);
                List<Candidate> candidates=jdbc.query("""
                        SELECT m.organization_id,m.id,m.content,m.occurred_at,c.lead_id,c.customer_id
                        FROM conversation_messages m JOIN conversations c ON c.organization_id=m.organization_id AND c.id=m.conversation_id
                        WHERE m.direction='INBOUND' AND m.occurred_at>=? AND m.occurred_at<=?
                        AND ((c.lead_id IS NOT NULL AND c.customer_id IS NULL) OR (c.customer_id IS NOT NULL AND c.lead_id IS NULL))
                        AND NOT EXISTS (SELECT 1 FROM customer_signal_extractions e WHERE e.organization_id=m.organization_id AND e.message_id=m.id AND e.status='SUCCEEDED')
                        AND (SELECT count(*) FROM customer_signal_extractions e WHERE e.organization_id=m.organization_id AND e.message_id=m.id AND e.status='FAILED' AND e.error_code='INVALID_OUTPUT')<2
                        AND (SELECT count(*) FROM customer_signal_extractions e WHERE e.organization_id=m.organization_id AND e.message_id=m.id AND e.status='FAILED')<?
                        AND NOT EXISTS (SELECT 1 FROM customer_signal_extractions e WHERE e.organization_id=m.organization_id AND e.message_id=m.id AND e.status='FAILED' AND e.created_at>?)
                        """+seek+" ORDER BY m.occurred_at DESC,m.id DESC LIMIT ?",
                        (r,n)->new Candidate(r.getObject("organization_id",UUID.class),r.getObject("id",UUID.class),r.getString("content"),r.getObject("lead_id",UUID.class),r.getObject("customer_id",UUID.class),r.getTimestamp("occurred_at").toInstant()),args.toArray());
                if(candidates.isEmpty())break;
                for(Candidate candidate:candidates) {
                    cursor=candidate;
                    if(blocked.contains(candidate.organizationId()))continue;
                    String type=candidate.leadId()!=null?"LEAD":"CUSTOMER";
                    UUID subject=candidate.leadId()!=null?candidate.leadId():candidate.customerId();
                    var outcome=processor.processForAuto(candidate.organizationId(),type,subject,candidate.messageId(),candidate.content());
                    var result=outcome.extraction();
                    processed++;
                    if("DISABLED".equals(result.status()))return;
                    if("FAILED".equals(result.status()) && outcome.providerError()!=null && Set.of("TIMEOUT","PROVIDER_ERROR","RATE_LIMIT").contains(outcome.providerError()))return;
                    if("BLOCKED_BUDGET".equals(result.status()))blocked.add(candidate.organizationId());
                    if(processed>=limit)break;
                }
                if(candidates.size()<50)break;
            }
        } finally {running.set(false);}
    }
}
