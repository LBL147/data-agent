package com.pangruixin.llm.mentor.agentx.tools;

import com.agentx.ai.core.agent.internal.ToolCallExecutor;
import com.agentx.ai.core.hook.HookManager;
import com.agentx.ai.core.model.RunnableParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangruixin.llm.mentor.agentx.service.EmbeddingService;
import com.pangruixin.llm.mentor.agentx.service.FileManageService;
import com.pangruixin.llm.mentor.agentx.service.MultimodalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AnalyzeFileToolIdentityContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ToolCallback callback;

    @BeforeEach
    void setUp() {
        AnalyzeFileTool tool = new AnalyzeFileTool(
                mock(FileManageService.class),
                mock(EmbeddingService.class),
                mock(MultimodalService.class)
        );
        ToolCallback[] callbacks = ToolCallbacks.from(tool);
        assertEquals(1, callbacks.length);
        callback = callbacks[0];
    }

    @Test
    void schemaExposesUserIdForFrameworkInjection() {
        String schema = callback.getToolDefinition().inputSchema();
        assertTrue(schema.contains("\"userId\""), schema);
    }

    @Test
    void frameworkOverwritesModelSuppliedUserIdWithTrustedValue() throws Exception {
        String modelArguments = objectMapper.writeValueAsString(Map.of(
                "fileId", "file-1",
                "question", "summarize",
                "userId", "999"
        ));
        RunnableParams params = RunnableParams.builder()
                .conversationId("conv-1")
                .userId("1")
                .addToolParam("userId", "1")
                .build();

        String merged = replaceToolParams(callback, modelArguments, params);
        JsonNode mergedJson = objectMapper.readTree(merged);

        assertEquals("1", mergedJson.path("userId").asText());
        assertEquals("file-1", mergedJson.path("fileId").asText());
    }

    private String replaceToolParams(ToolCallback toolCallback, String arguments,
                                     RunnableParams params) throws Exception {
        ToolCallExecutor executor = new ToolCallExecutor(
                Map.of("analyzeFile", toolCallback),
                objectMapper,
                null,
                HookManager.EMPTY,
                null,
                0
        );
        Method method = ToolCallExecutor.class.getDeclaredMethod(
                "replaceToolParams", ToolCallback.class, String.class, RunnableParams.class);
        method.setAccessible(true);
        return (String) method.invoke(executor, toolCallback, arguments, params);
    }
}
