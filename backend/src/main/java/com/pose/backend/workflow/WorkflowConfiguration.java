package com.pose.backend.workflow;

import com.pose.workflow.TransactionalOutboxPublisher;
import com.pose.workflow.jdbc.JdbcTransactionalOutboxPublisher;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Configuration
public class WorkflowConfiguration {
    @Bean
    TransactionalOutboxPublisher transactionalOutboxPublisher(DataSource dataSource) {
        return new JdbcTransactionalOutboxPublisher(() -> {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("Publishing requires a Spring transaction");
            }
            return DataSourceUtils.getConnection(dataSource);
        });
    }
}
