package com.ofd.converter.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Applies lightweight schema migrations that are not covered by schema.sql
 * (SQLite schema.sql uses IF NOT EXISTS, so adding columns needs to be conditional).
 */
@Component
public class DatabaseMigration implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public DatabaseMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        addFilenameColumnIfMissing();
    }

    private void addFilenameColumnIfMissing() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pragma_table_info('operation_log') WHERE name = 'filename'",
            Integer.class);
        if (count != null && count == 0) {
            jdbc.execute("ALTER TABLE operation_log ADD COLUMN filename TEXT");
        }
    }
}
