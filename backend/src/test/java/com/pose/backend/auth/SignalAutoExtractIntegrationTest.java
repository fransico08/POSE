package com.pose.backend.auth;

import com.pose.backend.ai.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@TestPropertySource(properties={"pose.ai.enabled=true","pose.ai.provider=mock","pose.ai.signal.auto-extract=true","pose.ai.signal.auto-batch=2","pose.ai.signal.auto-interval-ms=86400000","pose.sales-assignment.expiry-scan-delay-ms=86400000"})
class SignalAutoExtractIntegrationTest extends FoundationTestSupport {
    @Autowired SignalAutoExtractJob job;
    @MockitoSpyBean SignalExtractionProcessor processor;
    @Autowired AiSettings settings;
    @Autowired AiManagementService management;
    @Autowired BudgetGuard budget;
    @MockitoSpyBean MockProvider mock;
    private static final UUID CUSTOMER=id(3501),ACCOUNT=id(3502),CONVERSATION=id(3503),UNLINKED=id(3504);
    @BeforeEach void fixtures() {
        mock.setNextReply(null);
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Customer','auto@example.test','auto@example.test','ACTIVE',?,?)",CUSTOMER,ORG_ONE,USER_ONE,time(),time());
        jdbc.update("INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,status,secret_ref,created_at,updated_at) VALUES (?,?,'TELEGRAM','Test','auto','ACTIVE','TEST_SECRET',?,?)",ACCOUNT,ORG_ONE,time(),time());
        jdbc.update("INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,customer_id,status,created_at,updated_at) VALUES (?,?,?,'linked',?,'OPEN',?,?),(?,?,?,'unlinked',NULL,'OPEN',?,?)",CONVERSATION,ORG_ONE,ACCOUNT,CUSTOMER,time(),time(),UNLINKED,ORG_ONE,ACCOUNT,time(),time());
    }
    private void message(UUID id,UUID conversation,long seconds,String direction) {
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,?,?,'text','Xin hỏi giá 0912 345 678','RECEIVED',?,?)",id,ORG_ONE,conversation,direction,id.toString(),Timestamp.from(clock.instant().plusSeconds(seconds)),time());
    }
    @Test void newestBatchIsBoundedScopedAndIdempotentWithSystemActor() {
        message(id(3511),CONVERSATION,-3,"INBOUND");message(id(3512),CONVERSATION,-2,"INBOUND");message(id(3513),CONVERSATION,-1,"INBOUND");
        message(id(3514),CONVERSATION,-90000,"INBOUND");message(id(3515),UNLINKED,-1,"INBOUND");message(id(3516),CONVERSATION,-1,"OUTBOUND");
        job.process();
        assertThat(jdbc.queryForList("SELECT message_id FROM customer_signal_extractions ORDER BY message_id",UUID.class)).containsExactly(id(3512),id(3513));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE actor_type='SYSTEM' AND actor_user_id IS NULL AND organization_id=?",Integer.class,ORG_ONE)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals WHERE source='AI' AND status='PROPOSED'",Integer.class)).isEqualTo(2);
        assertThat(mock.lastInput()).contains("[PHONE_1]").doesNotContain("0912 345 678");
        var metricsActor=new com.pose.backend.identity.ActorContext(USER_ONE,ORG_ONE,java.util.Set.of(),java.util.Set.of(com.pose.backend.identity.PermissionCode.AI_METRICS_READ),java.util.Set.of());
        assertThat(management.metrics(metricsActor,clock.instant().minusSeconds(1),clock.instant().plusSeconds(1)).get("counts").toString()).contains("SIGNAL_EXTRACT", "SUCCEEDED");
        jdbc.update("UPDATE ai_invocations SET cost_estimate=1 WHERE organization_id=? AND actor_type='SYSTEM'",ORG_ONE);
        assertThat(budget.spent(ORG_ONE)).isEqualByComparingTo("2");
        job.process();job.process();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE status='SUCCEEDED'",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals",Integer.class)).isEqualTo(3);
        jdbc.update("UPDATE conversations SET customer_id=? WHERE organization_id=? AND id=?",CUSTOMER,ORG_ONE,UNLINKED);
        job.process();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND status='SUCCEEDED'",Integer.class,id(3515))).isEqualTo(1);
    }
    @Test void blockedOrganizationIsSkippedWhileAnotherTenantContinues() {
        UUID otherCustomer=id(3541),otherAccount=id(3542),otherConversation=id(3543),otherMessage=id(3544);
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Other','other-auto@example.test','other-auto@example.test','ACTIVE',?,?)",otherCustomer,ORG_TWO,USER_TWO,time(),time());
        jdbc.update("INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,status,secret_ref,created_at,updated_at) VALUES (?,?,'TELEGRAM','Other','other-auto','ACTIVE','TEST_SECRET',?,?)",otherAccount,ORG_TWO,time(),time());
        jdbc.update("INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,customer_id,status,created_at,updated_at) VALUES (?,?,?,'other-thread',?,'OPEN',?,?)",otherConversation,ORG_TWO,otherAccount,otherCustomer,time(),time());
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND','other-message','text','Xin chào','RECEIVED',?,?)",otherMessage,ORG_TWO,otherConversation,Timestamp.from(clock.instant().minusSeconds(3)),time());
        message(id(3545),CONVERSATION,-1,"INBOUND");message(id(3546),CONVERSATION,-2,"INBOUND");
        jdbc.update("INSERT INTO ai_invocations(id,organization_id,actor_user_id,use_case,subject_type,subject_id,provider,cost_estimate,status,created_at) VALUES (?,?,?,'SIGNAL_EXTRACT','CUSTOMER',?,'mock',6,'SUCCEEDED',?)",id(3547),ORG_ONE,USER_ONE,CUSTOMER,Timestamp.from(clock.instant()));
        assertThat(budget.spent(ORG_ONE)).isEqualByComparingTo("6");
        assertThat(budget.blocked(ORG_ONE)).isTrue();
        job.process();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=? AND status='BLOCKED_BUDGET' AND actor_type='SYSTEM'",Integer.class,ORG_ONE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE organization_id=?",Integer.class,ORG_ONE)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals WHERE organization_id=? AND message_id=?",Integer.class,ORG_TWO,otherMessage)).isEqualTo(1);
    }
    @Test void emptyInvalidAndTransientResultsFollowManualRetryRules() {
        message(id(3521),CONVERSATION,-1,"INBOUND");
        mock.setNextReply(AiProvider.Reply.failed("TIMEOUT"));job.process();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE error_code='TIMEOUT'",Integer.class)).isEqualTo(1);
        clock.now=clock.now.plusSeconds(15*60);
        mock.setNextReply(new AiProvider.Reply("{}",1,1,null));job.process();
        clock.now=clock.now.plusSeconds(15*60);
        mock.setNextReply(new AiProvider.Reply("{}",1,1,null));job.process();job.process();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE error_code='INVALID_OUTPUT'",Integer.class)).isEqualTo(2);
        message(id(3522),CONVERSATION,-2,"INBOUND");mock.setNextReply(new AiProvider.Reply("{\"signals\":[]}",1,1,null));job.process();job.process();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND status='SUCCEEDED'",Integer.class,id(3522))).isEqualTo(1);
    }
    @Test void temporaryFailureWaitsForBackoffAndAllowsOlderMessagesThrough() {
        message(id(3561),CONVERSATION,-1,"INBOUND");message(id(3562),CONVERSATION,-2,"INBOUND");
        mock.setNextReply(AiProvider.Reply.failed("TIMEOUT"));job.process();
        verify(mock,times(1)).generate(any());
        job.process();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND status='SUCCEEDED'",Integer.class,id(3562))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=?",Integer.class,id(3561))).isEqualTo(1);
        clock.now=clock.now.plusSeconds(15*60-1);job.process();verify(mock,times(2)).generate(any());
        clock.now=clock.now.plusSeconds(1);job.process();verify(mock,times(3)).generate(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND status='SUCCEEDED'",Integer.class,id(3561))).isEqualTo(1);
    }
    @Test void exhaustedAttemptsDoNotBlockOlderMessages() {
        message(id(3571),CONVERSATION,-1,"INBOUND");message(id(3572),CONVERSATION,-2,"INBOUND");
        for(int attempt=0;attempt<3;attempt++) {
            mock.setNextReply(AiProvider.Reply.failed("PROVIDER_ERROR"));job.process();
            clock.now=clock.now.plusSeconds(15*60);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND status='FAILED'",Integer.class,id(3571))).isEqualTo(3);
        job.process();job.process();
        verify(mock,times(4)).generate(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND status='SUCCEEDED'",Integer.class,id(3572))).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"TIMEOUT","PROVIDER_ERROR","RATE_LIMIT"})
    void providerFailuresStopTheEntireBatch(String error) {
        message(id(3581),CONVERSATION,-1,"INBOUND");message(id(3582),CONVERSATION,-2,"INBOUND");
        mock.setNextReply(AiProvider.Reply.failed(error));job.process();
        verify(mock,times(1)).generate(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND error_code=?",Integer.class,id(3581),error)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=?",Integer.class,id(3582))).isZero();
    }
    @Test void providerFailureStopsTheBatchEvenWhenAttemptCannotBeStored() {
        message(id(3583),CONVERSATION,-1,"INBOUND");message(id(3584),CONVERSATION,-2,"INBOUND");
        jdbc.execute("ALTER TABLE customer_signal_extractions ADD CONSTRAINT test_auto_failure_block CHECK (status<>'FAILED')");
        try {
            mock.setNextReply(AiProvider.Reply.failed("TIMEOUT"));job.process();
            verify(mock,times(1)).generate(any());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=?",Integer.class,id(3584))).isZero();
        } finally {jdbc.execute("ALTER TABLE customer_signal_extractions DROP CONSTRAINT test_auto_failure_block");}
    }
    @Test void providerFailureStopsTheBatchWhenInvocationCannotBeStored() {
        message(id(3585),CONVERSATION,-1,"INBOUND");message(id(3586),CONVERSATION,-2,"INBOUND");
        jdbc.execute("ALTER TABLE ai_invocations ADD CONSTRAINT test_auto_invocation_block CHECK (status<>'FAILED')");
        try {
            mock.setNextReply(AiProvider.Reply.failed("TIMEOUT"));job.process();
            verify(mock,times(1)).generate(any());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND error_code='STORAGE_ERROR'",Integer.class,id(3585))).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=?",Integer.class,id(3586))).isZero();
        } finally {jdbc.execute("ALTER TABLE ai_invocations DROP CONSTRAINT test_auto_invocation_block");}
    }
    @ParameterizedTest @ValueSource(strings={"TIMEOUT","PROVIDER_ERROR","RATE_LIMIT","NETWORK","STORAGE_ERROR","CONTEXT_ERROR","BUDGET_ERROR"})
    void everyFailureCodeCountsTowardTheAutomaticLimit(String error) {
        message(id(3591),CONVERSATION,-1,"INBOUND");message(id(3592),CONVERSATION,-2,"INBOUND");
        for(int i=0;i<3;i++)jdbc.update("INSERT INTO customer_signal_extractions(id,organization_id,message_id,status,error_code,created_at) VALUES (?,?,?,'FAILED',?,?)",id(3593+i),ORG_ONE,id(3591),error,Timestamp.from(clock.instant().minusSeconds(901)));
        job.process();verify(mock,times(1)).generate(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND status='SUCCEEDED'",Integer.class,id(3591))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND status='SUCCEEDED'",Integer.class,id(3592))).isEqualTo(1);
    }
    @Test void concurrentInstancesCannotPersistDuplicateSignals() throws Exception {
        message(id(3531),CONVERSATION,-1,"INBOUND");
        var second=new SignalAutoExtractJob(jdbc,processor,settings,clock);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await();job.process();return null;});var b=pool.submit(()->{start.await();second.process();return null;});
            start.countDown();a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND status='SUCCEEDED'",Integer.class,id(3531))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals WHERE message_id=?",Integer.class,id(3531))).isEqualTo(1);
    }
    @Test void oneInstanceDoesNotRunOverlappingBatches() throws Exception {
        message(id(3551),CONVERSATION,-1,"INBOUND");
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{started.countDown();if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Timed out waiting for test");return call.callRealMethod();}).when(mock).generate(any());
        try(var pool=Executors.newSingleThreadExecutor()) {
            var first=pool.submit(job::process);
            try {assertThat(started.await(20,TimeUnit.SECONDS)).isTrue();job.process();}
            finally {release.countDown();}
            first.get(30,TimeUnit.SECONDS);
        }
        verify(mock,times(1)).generate(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND status='SUCCEEDED'",Integer.class,id(3551))).isEqualTo(1);
    }
    @Test void scheduledTriggerReturnsWhileProviderRunsAndDoesNotQueueAnotherBatch() throws Exception {
        message(id(3552),CONVERSATION,-1,"INBOUND");
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        doAnswer(call->{started.countDown();if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Timed out waiting for test");return call.callRealMethod();}).when(mock).generate(any());
        doAnswer(call->{try{return call.callRealMethod();}finally{finished.countDown();}}).when(processor).processForAuto(any(),any(),any(),any(),any());
        try {job.schedule();assertThat(started.await(20,TimeUnit.SECONDS)).isTrue();job.schedule();}
        finally {release.countDown();}
        assertThat(finished.await(30,TimeUnit.SECONDS)).isTrue();
        verify(mock,times(1)).generate(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE message_id=? AND status='SUCCEEDED'",Integer.class,id(3552))).isEqualTo(1);
    }
}
