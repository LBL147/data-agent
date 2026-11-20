package com.pangruixin.llm.mentor.agentx.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculateToolTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private ToolCallback calculateTool() {
        ToolCallback[] callbacks = ToolCallbacks.from(new CalculateTool(new ObjectMapper()));
        assertEquals(1, callbacks.length, "CalculateTool 应注册为一个工具");
        return callbacks[0];
    }

    @Test
    void exposesCalculateContractThroughSpringAiToolCallback() {
        assertEquals("calculate", calculateTool().getToolDefinition().name());
    }

    @Test
    void executesWithVariablesJsonString() throws Exception {
        String result = calculateTool().call(mapper.writeValueAsString(Map.of(
                "expression", "(curr - prev) / prev * 100",
                "variablesJson", "{\"curr\":3298,\"prev\":3105}"
        )));

        assertTrue(result.contains("✅"), result);
        assertTrue(result.contains("6.21578"), result);
        assertTrue(result.contains("curr=3298"), result);
        assertTrue(result.contains("prev=3105"), result);
    }

    @Test
    void reportsInvalidVariablesJsonAsBusinessFailure() throws Exception {
        String result = calculateTool().call(mapper.writeValueAsString(Map.of(
                "expression", "a + b",
                "variablesJson", "{not-json}"
        )));

        assertTrue(result.contains("❌"), result);
        assertTrue(result.contains("variablesJson 解析失败"), result);
    }

    @Test
    void reportsMissingVariablesAsBusinessFailure() throws Exception {
        String result = calculateTool().call(mapper.writeValueAsString(Map.of(
                "expression", "(curr - prev) / prev * 100",
                "variablesJson", "{}"
        )));

        assertTrue(result.contains("❌"), result);
        assertTrue(result.contains("表达式无效"), result);
    }

    @Test
    void reportsDivisionByZeroAsBusinessFailure() throws Exception {
        String result = calculateTool().call(mapper.writeValueAsString(Map.of(
                "expression", "1 / x",
                "variablesJson", "{\"x\":0}"
        )));

        assertTrue(result.contains("❌"), result);
        assertTrue(result.contains("算术异常"), result);
        assertTrue(result.contains("除数为 0"), result);
    }
}
