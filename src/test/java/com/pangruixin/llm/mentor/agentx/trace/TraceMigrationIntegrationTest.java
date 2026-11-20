package com.pangruixin.llm.mentor.agentx.trace;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TraceMigrationIntegrationTest {
    // 连接信息只从环境变量读取，缺失时测试自动跳过；禁止在仓库中硬编码真实库地址与凭据。
    private static final String URL = System.getenv("TRACE_MIGRATION_TEST_URL");
    private static final String USER = System.getenv("TRACE_MIGRATION_TEST_USER");
    private static final String PASSWORD = System.getenv("TRACE_MIGRATION_TEST_PASSWORD");

    @Test
    void upgradesOldTableIdempotentlyAndPreservesHistory() throws Exception {
        assumeEnvConfigured();
        String databaseName = databaseName(URL);
        assumeTrue(databaseName.endsWith("_migration_test"), "只允许连接以 _migration_test 结尾的隔离测试库");

        try (Connection connection = DriverManager.getConnection(rootUrl(URL), USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS `" + databaseName + "`");
            statement.executeUpdate("CREATE DATABASE `" + databaseName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }

        String migration = Files.readString(Path.of("sql/migrations/20261120_001_upgrade_agentx_trace.sql"),
                StandardCharsets.UTF_8);

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            createOldTraceTable(statement);
            statement.executeUpdate("INSERT INTO agentx_trace VALUES (1, 100, 'conversation-legacy', 1, 'input', 'output', 'think', 0, 0, 0, 1, NULL, NULL)");
        }

        runMigration(URL, migration);
        runMigration(URL, migration);

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            List<String> names = new ArrayList<>();
            try (ResultSet columns = statement.executeQuery(
                    "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agentx_trace' ORDER BY ORDINAL_POSITION")) {
                while (columns.next()) {
                    names.add(columns.getString(1));
                }
            }
            assertTrue(names.contains("user_id"));
            assertTrue(names.contains("span_type"));
            assertTrue(names.contains("tool_name"));
            assertTrue(names.contains("tool_call_id"));
            assertTrue(names.contains("compact_id"));

            try (ResultSet rows = statement.executeQuery(
                    "SELECT id, user_id, span_type, tool_name, tool_call_id, compact_id FROM agentx_trace ORDER BY id")) {
                assertTrue(rows.next());
                assertEquals(1L, rows.getLong(1));
                assertEquals("LLM", rows.getString(3));
                assertEquals(null, rows.getString(2));
                assertEquals(null, rows.getString(4));
                assertEquals(null, rows.getString(5));
                assertEquals(null, rows.getObject(6));
            }
            try (ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM agentx_trace")) {
                assertTrue(count.next());
                assertEquals(1, count.getInt(1));
            }
        }
    }

    @Test
    void initializesNewTableFromInitSqlAndMigrationIsIdempotent() throws Exception {
        assumeEnvConfigured();
        String databaseName = databaseName(URL);
        assumeTrue(databaseName.endsWith("_migration_test"), "只允许连接以以 _migration_test 结尾的隔离测试库");

        try (Connection connection = DriverManager.getConnection(rootUrl(URL), USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS `" + databaseName + "`");
            statement.executeUpdate("CREATE DATABASE `" + databaseName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }

        String initTable = Files.readString(Path.of("sql/init.sql"), StandardCharsets.UTF_8)
                .lines()
                .dropWhile(line -> !line.contains("DROP TABLE IF EXISTS `agentx_trace`"))
                .takeWhile(line -> !line.contains("DROP TABLE IF EXISTS `category`"))
                .reduce("", (left, right) -> left + right + System.lineSeparator());
        String migration = Files.readString(Path.of("sql/migrations/20261120_001_upgrade_agentx_trace.sql"),
                StandardCharsets.UTF_8);

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(initTable);
        }
        runMigration(URL, migration);
        runMigration(URL, migration);

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery(
                     "SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agentx_trace' AND COLUMN_NAME IN ('user_id','span_type','tool_name','tool_call_id','compact_id','created_at')")) {
            int checked = 0;
            while (columns.next()) {
                checked++;
                if ("created_at".equals(columns.getString(1))) {
                    assertEquals("timestamp(3)", columns.getString(2));
                } else {
                    assertTrue(columns.getString(1).matches("user_id|span_type|tool_name|tool_call_id|compact_id"));
                }
            }
            assertEquals(6, checked);
        }
    }

    private static void assumeEnvConfigured() {
        assumeTrue(URL != null && USER != null && PASSWORD != null,
                "未配置 TRACE_MIGRATION_TEST_URL / TRACE_MIGRATION_TEST_USER / TRACE_MIGRATION_TEST_PASSWORD 环境变量，跳过集成测试");
    }

    private static void createOldTraceTable(Statement statement) throws Exception {
        statement.executeUpdate("""
                CREATE TABLE agentx_trace (
                  id bigint NOT NULL,
                  session_id bigint NOT NULL,
                  conversation_id varchar(100) NOT NULL,
                  round int NOT NULL,
                  input_data longtext,
                  output_data longtext,
                  think longtext,
                  prompt_tokens int DEFAULT 0,
                  completion_tokens int DEFAULT 0,
                  duration_ms bigint DEFAULT 0,
                  success int DEFAULT 1,
                  error_message longtext,
                  created_at timestamp NULL DEFAULT NULL,
                  PRIMARY KEY (id),
                  KEY idx_agentx_trace_session (session_id),
                  KEY idx_agentx_trace_conv (conversation_id)
                ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """);
    }

    private static void runMigration(String url, String migration) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(migration);
        }
    }

    private static String databaseName(String url) {
        int start = url.lastIndexOf('/') + 1;
        int end = url.indexOf('?', start);
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }

    private static String rootUrl(String url) {
        String databaseName = databaseName(url);
        int start = url.lastIndexOf('/') + 1;
        return url.substring(0, start - 1) + "/" + "?"
                + url.substring(url.indexOf('?') + 1).replace("allowMultiQueries=true", "");
    }
}
