package com.ofd.converter.config;

import com.ofd.converter.model.OperationLog;
import com.ofd.converter.model.Task;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

/**
 * Registers the SQLite dialect with Spring Data JDBC (overrides the extension point;
 * Spring Data JDBC 3.3.5 has no built-in SQLite dialect) and an AfterConvertCallback so
 * that Task entities loaded from the DB are treated as not-new (assigned-ID entities need
 * this so subsequent saves UPDATE instead of INSERT).
 */
@Configuration
public class JdbcConfig extends AbstractJdbcConfiguration {

    @Override
    public Dialect jdbcDialect(NamedParameterJdbcOperations operations) {
        return SqliteJdbcDialect.INSTANCE;
    }

    @Bean
    AfterConvertCallback<Task> taskAfterConvertCallback() {
        return task -> {
            task.markNotNew();
            return task;
        };
    }

    @Bean
    AfterConvertCallback<OperationLog> operationLogAfterConvertCallback() {
        return log -> {
            log.markNotNew();
            return log;
        };
    }
}
