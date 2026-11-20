package com.pangruixin.llm.mentor.agentx.trace;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TraceWriteHealthProbeIntegrationTest {

    // 连接信息只从环境变量读取，缺失时测试自动跳过；禁止在仓库中硬编码真实库地址与凭据。
    private static final String URL = System.getenv("TRACE_HEALTH_TEST_URL");
    private static final String USER = System.getenv("TRACE_HEALTH_TEST_USER");
    private static final String PASSWORD = System.getenv("TRACE_HEALTH_TEST_PASSWORD");

    @Test
    void successfulProbeAcceptsNegativeIdAndLeavesNoRow() throws Exception {
        assertIsolatedDatabase();
        resetSchema();
        createTraceTable(true);

        DriverManagerDataSource dataSource = new DriverManagerDataSource(URL, USER, PASSWORD);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertEquals("bigint", columnType(jdbcTemplate, "id"));
        assertEquals("timestamp(3)", columnType(jdbcTemplate, "created_at"));

        long before = rowCount(jdbcTemplate);
        TraceWriteHealthProbe probe = new TraceWriteHealthProbe(
                dataSource,
                new DataSourceTransactionManager(dataSource)
        );

        TraceWriteHealthProbe.TraceHealthResult result = probe.check();

        assertTrue(result.isUp());
        assertEquals("UP", result.status());
        assertTrue(result.rollbackVerified());
        assertEquals(before, rowCount(jdbcTemplate));
    }

    @Test
    void missingCompactIdInIsolatedSchemaReturnsDownWithoutResidualRow() throws Exception {
        assertIsolatedDatabase();
        resetSchema();
        createTraceTable(false);

        DriverManagerDataSource dataSource = new DriverManagerDataSource(URL, USER, PASSWORD);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        TraceWriteHealthProbe probe = new TraceWriteHealthProbe(
                dataSource,
                new DataSourceTransactionManager(dataSource)
        );

        TraceWriteHealthProbe.TraceHealthResult result = probe.check();

        assertFalse(result.isUp());
        assertEquals("DOWN", result.status());
        assertEquals("TRACE_WRITE_FAILED", result.errorCode());
        assertTrue(result.rollbackVerified());
        assertEquals(0, rowCount(jdbcTemplate));
    }

    private static void assertIsolatedDatabase() {
        assumeTrue(URL != null && USER != null && PASSWORD != null,
                "未配置 TRACE_HEALTH_TEST_URL / TRACE_HEALTH_TEST_USER / TRACE_HEALTH_TEST_PASSWORD 环境变量，跳过集成测试");
        String databaseName = databaseName(URL);
        assumeTrue(databaseName.endsWith("_health_test"),
                "只允许连接以 _health_test 结尾的隔离健康检查库");
        assumeTrue(!"dodo_agentx".equals(databaseName), "禁止连接目标库 dodo_agentx");
    }

    private static void resetSchema() throws Exception {
        String databaseName = databaseName(URL);
        try (Connection connection = DriverManager.getConnection(rootUrl(URL), USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS `" + databaseName + "`");
            statement.executeUpdate("CREATE DATABASE `" + databaseName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
    }

    private static void createTraceTable(boolean includeCompactId) throws Exception {
        String compactColumn = includeCompactId
                ? "  compact_id bigint DEFAULT NULL,\n"
                : "";
        String ddl = """
                CREATE TABLE agentx_trace (
                  id bigint NOT NULL,
                  session_id bigint NOT NULL,
                  conversation_id varchar(100) NOT NULL,
                  user_id varchar(100) DEFAULT NULL,
                  round int NOT NULL,
                  span_type varchar(8) NOT NULL DEFAULT 'LLM',
                  tool_name varchar(128) DEFAULT NULL,
                  tool_call_id varchar(100) DEFAULT NULL,
                  input_data longtext,
                  output_data longtext,
                  think longtext,
                  prompt_tokens int DEFAULT 0,
                  completion_tokens int DEFAULT 0,
                  duration_ms bigint DEFAULT 0,
                  success int DEFAULT 1,
                  error_message longtext,
                  created_at timestamp(3) NULL DEFAULT NULL,
                """ + compactColumn + """
                  PRIMARY KEY (id),
                  KEY idx_agentx_trace_session (session_id),
                  KEY idx_agentx_trace_conv (conversation_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """;
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
    }

    private static String columnType(JdbcTemplate jdbcTemplate, String columnName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agentx_trace' AND COLUMN_NAME = ?
                """,
                String.class,
                columnName
        );
    }

    private static long rowCount(JdbcTemplate jdbcTemplate) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agentx_trace", Long.class);
        return count == null ? -1 : count;
    }

    private static String databaseName(String url) {
        int start = url.lastIndexOf('/') + 1;
        int end = url.indexOf('?', start);
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }

    private static String rootUrl(String url) {
        int start = url.lastIndexOf('/') + 1;
        return url.substring(0, start - 1) + "/?" + url.substring(url.indexOf('?') + 1);
    }
}
