package com.pangruixin.llm.mentor.agentx.trace;

import com.pangruixin.llm.mentor.agentx.controller.EvalHealthController;
import com.pangruixin.llm.mentor.agentx.trace.TraceWriteHealthProbe.TraceHealthResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TraceHealthCheckTest {

    @Test
    void forcedTransactionFailureReturnsReal503() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("forced database failure"));

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any()))
                .thenThrow(new CannotCreateTransactionException("forced transaction failure"));

        TraceWriteHealthProbe probe = new TraceWriteHealthProbe(dataSource, transactionManager);
        TraceHealthResult result = probe.check();

        assertFalse(result.isUp());
        assertEquals("DOWN", result.status());
        assertEquals("TRACE_WRITE_FAILED", result.errorCode());
        assertFalse(result.rollbackVerified());

        EvalHealthController controller = new EvalHealthController(probe);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mockMvc.perform(get("/api/eval/health/trace"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void successfulProbeReturnsReal200() throws Exception {
        TraceWriteHealthProbe probe = mock(TraceWriteHealthProbe.class);
        TraceHealthResult result = new TraceHealthResult("UP", "PASS", true, 4, null);
        when(probe.check()).thenReturn(result);

        EvalHealthController controller = new EvalHealthController(probe);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mockMvc.perform(get("/api/eval/health/trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.up").doesNotExist());
    }
}
