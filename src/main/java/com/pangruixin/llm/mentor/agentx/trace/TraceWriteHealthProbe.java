package com.pangruixin.llm.mentor.agentx.trace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 通过真实数据库写入验证 agentx_trace 的保存契约。
 *
 * <p>探针在独立事务中插入一条合成 TOOL span，随后强制回滚；事务结束后
 * 再用新的数据库连接确认该 span 不存在。任何写入或回滚验证失败都会返回 DOWN。
 */
@Slf4j
@Component
public class TraceWriteHealthProbe {

    private static final String PROBE_CONVERSATION_ID = "__trace_health_probe__";
    private static final String PROBE_USER_ID = "__trace_health_probe__";
    private static final String PROBE_TOOL_NAME = "__trace_health_probe__";

    private static final String INSERT_PROBE_SQL = """
            INSERT INTO agentx_trace (
                id, session_id, conversation_id, user_id, round, span_type,
                tool_name, tool_call_id, input_data, output_data, think,
                prompt_tokens, completion_tokens, duration_ms, success,
                error_message, created_at, compact_id
            )
            VALUES (?, ?, ?, ?, 0, 'TOOL', ?, ?, ?, ?, NULL, 0, 0, 0, 1, NULL, CURRENT_TIMESTAMP(3), NULL)
            """;

    private static final String COUNT_PROBE_SQL = "SELECT COUNT(*) FROM agentx_trace WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public TraceWriteHealthProbe(DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(2);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(2);
    }

    public TraceHealthResult check() {
        long startedAt = System.nanoTime();
        long probeId = randomNegativeId();
        long probeSessionId = randomNegativeId();
        String toolCallId = "trace-health-" + probeId;
        boolean writeSucceeded = false;
        String errorCode = null;

        try {
            Integer affected = transactionTemplate.execute(status -> {
                int rows = jdbcTemplate.update(
                        INSERT_PROBE_SQL,
                        probeId,
                        probeSessionId,
                        PROBE_CONVERSATION_ID,
                        PROBE_USER_ID,
                        PROBE_TOOL_NAME,
                        toolCallId,
                        "{}",
                        "{}"
                );
                if (rows != 1) {
                    throw new IllegalStateException("trace probe insert affected " + rows + " rows");
                }
                status.setRollbackOnly();
                return rows;
            });
            writeSucceeded = affected != null && affected == 1;
            if (!writeSucceeded && errorCode == null) {
                errorCode = "TRACE_WRITE_FAILED";
            }
        } catch (Exception e) {
            errorCode = "TRACE_WRITE_FAILED";
            log.warn("[trace-health] 写入探针失败: probeId={}, error={}", probeId, e.getMessage());
        }

        boolean rollbackVerified = false;
        boolean residualFound = false;
        try {
            Long remaining = jdbcTemplate.queryForObject(COUNT_PROBE_SQL, Long.class, probeId);
            rollbackVerified = remaining != null && remaining == 0;
            residualFound = remaining != null && remaining > 0;
        } catch (Exception e) {
            if (errorCode == null) {
                errorCode = "TRACE_ROLLBACK_UNVERIFIED";
            }
            log.warn("[trace-health] 回滚复查失败: probeId={}, error={}", probeId, e.getMessage());
        }

        if (residualFound) {
            errorCode = "TRACE_PROBE_ROLLBACK_FAILED";
            log.error("[trace-health] 探针事务回滚后仍有残留记录: probeId={}", probeId);
        }

        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
        boolean up = writeSucceeded && rollbackVerified;
        TraceHealthResult result = new TraceHealthResult(
                up ? "UP" : "DOWN",
                up ? "PASS" : "FAIL",
                rollbackVerified,
                latencyMs,
                up ? null : errorCode
        );
        if (up) {
            log.info("[trace-health] trace 写入探针通过: latencyMs={}", latencyMs);
        } else {
            log.warn("[trace-health] trace 写入探针未通过: errorCode={}, rollbackVerified={}, latencyMs={}",
                    result.errorCode(), rollbackVerified, latencyMs);
        }
        return result;
    }

    private static long randomNegativeId() {
        long value;
        do {
            value = ThreadLocalRandom.current().nextLong();
        } while (value >= 0);
        return value;
    }

    public record TraceHealthResult(
            String status,
            String writeProbe,
            boolean rollbackVerified,
            long latencyMs,
            String errorCode
    ) {
        @JsonIgnore
        public boolean isUp() {
            return "UP".equals(status);
        }
    }
}
