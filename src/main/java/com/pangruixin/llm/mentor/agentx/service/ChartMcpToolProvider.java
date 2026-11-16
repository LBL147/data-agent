package com.pangruixin.llm.mentor.agentx.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;

/**
 * 按聊天请求连接图表 MCP；保留原始工具名、完整结果和错误标记。
 * 连接失败不阻止应用启动，后续请求可在冷却时间后重试。
 */
@Slf4j
@Component
public class ChartMcpToolProvider {
    private static final ToolCallback[] EMPTY = new ToolCallback[0];
    private static final String CALL_FAILED = """
            {"isError":true,"content":[{"type":"text","text":"图表服务暂不可用。请继续输出数据和分析，说明本次图表生成失败，不要编造图片链接。后续聊天将自动重试连接。"}]}
            """;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String mcpUrl;
    private final String bearerToken;
    private final Duration initializationTimeout;
    private final Duration requestTimeout;
    private final Duration retryDelay;
    private McpSyncClient client;
    private ToolCallback[] callbacks;
    private long retryAfter;
    private boolean closed;

    public ChartMcpToolProvider(ObjectMapper objectMapper,
            @Value("${data-agent.chart.enabled:true}") boolean enabled,
            @Value("${data-agent.chart.mcp-url:http://localhost:3033/mcp}") String mcpUrl,
            @Value("${data-agent.chart.bearer-token:}") String bearerToken,
            @Value("${data-agent.chart.initialization-timeout:10s}") Duration initializationTimeout,
            @Value("${data-agent.chart.request-timeout:60s}") Duration requestTimeout,
            @Value("${data-agent.chart.retry-delay:30s}") Duration retryDelay) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.mcpUrl = mcpUrl;
        this.bearerToken = bearerToken;
        this.initializationTimeout = initializationTimeout;
        this.requestTimeout = requestTimeout;
        this.retryDelay = retryDelay;
    }

    public synchronized ToolCallback[] getToolCallbacks() {
        if (!enabled || closed) return EMPTY;
        if (callbacks != null) return callbacks.clone();
        if (retryAfter != 0 && System.nanoTime() - retryAfter < 0) return EMPTY;
        McpSyncClient candidate = null;
        try {
            var transportBuilder = HttpClientStreamableHttpTransport.builder(mcpUrl)
                    .connectTimeout(initializationTimeout);
            if (bearerToken != null && !bearerToken.isBlank()) {
                transportBuilder.customizeRequest(request ->
                        request.header("Authorization", "Bearer " + bearerToken));
            }
            candidate = McpClient.sync(transportBuilder.build())
                    .clientInfo(new McpSchema.Implementation("dodo-chart", "1.0"))
                    .initializationTimeout(initializationTimeout)
                    .requestTimeout(requestTimeout)
                    .build();
            candidate.initialize();
            var discovered = new ArrayList<ToolCallback>();
            for (var tool : candidate.listTools().tools()) {
                ToolDefinition definition = SyncMcpToolCallback.builder()
                        .mcpClient(candidate).tool(tool).prefixedToolName(tool.name()).build()
                        .getToolDefinition();
                discovered.add(wrap(candidate, definition));
            }
            client = candidate;
            callbacks = discovered.toArray(ToolCallback[]::new);
            log.info("[dodo-agentx] 图表 MCP 已就绪，发现 {} 个工具", callbacks.length);
            return callbacks.clone();
        } catch (Exception e) {
            closeClient(candidate);
            retryAfter = System.nanoTime() + retryDelay.toNanos();
            // 不记录 URL、请求头或异常正文，避免泄露令牌或图表数据。
            log.warn("[dodo-agentx] 图表 MCP 连接/发现失败 ({})；稍后聊天重试，其他功能继续可用",
                    e.getClass().getSimpleName());
            return EMPTY;
        }
    }

    private ToolCallback wrap(McpSyncClient connectedClient, ToolDefinition definition) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String input) {
                Map<String, Object> arguments;
                try {
                    arguments = objectMapper.readValue(
                            input == null || input.isBlank() ? "{}" : input, new TypeReference<>() {});
                } catch (Exception e) {
                    return "{\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"图表参数必须是 JSON 对象，请按工具 schema 修正后重试。\"}]}";
                }
                try {
                    var result = connectedClient.callTool(McpSchema.CallToolRequest.builder()
                            .name(definition.name()).arguments(arguments).build());
                    return objectMapper.writeValueAsString(result);
                } catch (Exception e) {
                    invalidate(connectedClient);
                    log.warn("[dodo-agentx] 图表调用失败 ({})，本轮继续生成分析报告",
                            e.getClass().getSimpleName());
                    return CALL_FAILED;
                }
            }
        };
    }

    private synchronized void invalidate(McpSyncClient failedClient) {
        if (client == failedClient) {
            client = null;
            callbacks = null;
            retryAfter = System.nanoTime() + retryDelay.toNanos();
            closeClient(failedClient);
        }
    }

    private void closeClient(McpSyncClient oldClient) {
        if (oldClient != null) {
            try {
                oldClient.close();
            } catch (Exception ignored) {
                // 已断开的连接不应影响报告或应用关闭。
            }
        }
    }

    @PreDestroy
    public synchronized void close() {
        closed = true;
        closeClient(client);
        client = null;
        callbacks = null;
    }
}
