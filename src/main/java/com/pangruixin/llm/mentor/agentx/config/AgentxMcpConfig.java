package com.pangruixin.llm.mentor.agentx.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * agentx 所有 MCP 客户端集中装配。
 * <p>
 * 目前两个 MCP 服务：
 * - 图表 MCP 由 ChartMcpToolProvider 按需连接，不阻塞 Spring 启动
 * - Tavily 搜索（远程 HTTP + Bearer 认证）：联网搜索工具
 */
@Slf4j
@Configuration
public class AgentxMcpConfig {

    @Bean(destroyMethod = "close", name = "tavilyMcpSyncClient")
    public McpSyncClient tavilyMcpSyncClient(
            @Value("${tavily.api-key:}") String apiKey,
            @Value("${tavily.mcp-url:https://mcp.tavily.com/mcp/}") String mcpUrl) {

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[dodo-agentx] Tavily API key 未配置（tavily.api-key），联网功能不可用");
            return null;
        }
        try {
            HttpClientStreamableHttpTransport transport =
                    HttpClientStreamableHttpTransport.builder(mcpUrl)
                            .customizeRequest(rb -> rb.header("Authorization", "Bearer " + apiKey))
                            .build();
            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(300))
                    .build();
            client.initialize();
            log.info("[dodo-agentx] Tavily MCP 客户端初始化完成：{}", mcpUrl);
            return client;
        } catch (Exception e) {
            log.warn("[dodo-agentx] Tavily MCP 初始化失败，联网功能不可用：{}", e.getMessage());
            return null;
        }
    }
}
