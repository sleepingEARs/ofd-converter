package com.ofd.converter.config;

import org.springframework.data.jdbc.core.convert.JdbcArrayColumns;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.relational.core.dialect.AnsiDialect;

/**
 * SQLite dialect for Spring Data JDBC.
 *
 * Spring Data JDBC 3.3.5 has no built-in SQLite dialect (only Postgres/MySql/SqlServer/Db2),
 * so the auto-detection fails with "Cannot determine a dialect". SQLite is close enough to
 * ANSI SQL for our simple schema (no arrays, no exotic types), so we extend AnsiDialect and
 * implement JdbcDialect.
 */
public class SqliteJdbcDialect extends AnsiDialect implements JdbcDialect {

    public static final SqliteJdbcDialect INSTANCE = new SqliteJdbcDialect();

    @Override
    public JdbcArrayColumns getArraySupport() {
        return JdbcArrayColumns.Unsupported.INSTANCE;
    }
}
