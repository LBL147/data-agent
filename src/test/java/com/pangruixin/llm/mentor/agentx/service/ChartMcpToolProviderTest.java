package com.pangruixin.llm.mentor.agentx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ChartMcpToolProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ChartMcpToolProvider> providers = new ArrayList<>();
    private final AtomicInteger posts = new AtomicInteger();
    private HttpServer server;
    private String url;
    private volatile boolean unavailable;
    private volatile boolean toolError;
    private volatile String authorization;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
        server.createContext("/mcp", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("POST")) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                posts.incrementAndGet();
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (unavailable) {
                    exchange.sendResponseHeaders(503, -1);
                    return;
                }
                var request = mapper.readTree(exchange.getRequestBody());
                String method = request.path("method").asText();
                if (method.startsWith("notifications/")) {
                    exchange.sendResponseHeaders(202, -1);
                    return;
                }
                Object result = switch (method) {
                    case "initialize" -> Map.of("protocolVersion", "2025-03-26",
                            "capabilities", Map.of("tools", Map.of()),
                            "serverInfo", Map.of("name", "fake-chart", "version", "1.0"));
                    case "tools/list" -> Map.of("tools", List.of(Map.of(
                            "name", "generate_bar_chart", "description", "bar chart",
                            "inputSchema", Map.of("type", "object", "properties", Map.of()))));
                    case "tools/call" -> Map.of("isError", toolError,
                            "content", List.of(Map.of("type", "text", "text",
                                    toolError ? "render failed" : "https://example.test/chart.png?sig=abc")),
                            "structuredContent", Map.of("url", "https://example.test/chart.png?sig=abc"));
                    default -> Map.of();
                };
                byte[] bytes = mapper.writeValueAsBytes(Map.of("jsonrpc", "2.0",
                        "id", request.get("id"), "result", result));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private ChartMcpToolProvider provider(boolean enabled, Duration retryDelay) {
        var provider = new ChartMcpToolProvider(mapper, enabled, url, "test-only-token",
                Duration.ofSeconds(2), Duration.ofSeconds(2), retryDelay);
        providers.add(provider);
        return provider;
    }

    @AfterEach
    void closeResources() {
        providers.forEach(ChartMcpToolProvider::close);
        server.stop(0);
    }

    @Test
    void doesNotConnectDuringConstructionAndCanBeDisabled() {
        var provider = provider(false, Duration.ZERO);
        assertEquals(0, posts.get());
        assertEquals(0, provider.getToolCallbacks().length);
        assertEquals(0, posts.get());
    }

    @Test
    void preservesOriginalNamesCompleteResultsAndAuthHeader() throws Exception {
        var provider = provider(true, Duration.ZERO);
        assertEquals(0, posts.get());
        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertEquals(1, callbacks.length);
        assertEquals("generate_bar_chart", callbacks[0].getToolDefinition().name());
        assertEquals("Bearer test-only-token", authorization);
        var result = mapper.readTree(callbacks[0].call("{}"));
        assertFalse(result.path("isError").asBoolean());
        assertEquals("https://example.test/chart.png?sig=abc",
                result.path("structuredContent").path("url").asText());
        int requests = posts.get();
        assertEquals(1, provider.getToolCallbacks().length);
        assertEquals(requests, posts.get(), "工具列表应被缓存");
        callbacks[0] = null;
        assertNotNull(provider.getToolCallbacks()[0], "外部不能改坏缓存");
    }

    @Test
    void discoveryFailureDoesNotThrowAndCanRecoverOnLaterRequest() {
        unavailable = true;
        var provider = provider(true, Duration.ZERO);
        assertEquals(0, provider.getToolCallbacks().length);
        unavailable = false;
        assertEquals(1, provider.getToolCallbacks().length);
    }

    @Test
    void failedConnectionsRespectRetryCooldown() {
        unavailable = true;
        var provider = provider(true, Duration.ofSeconds(30));
        assertEquals(0, provider.getToolCallbacks().length);
        int requests = posts.get();
        assertEquals(0, provider.getToolCallbacks().length);
        assertEquals(requests, posts.get());
    }

    @Test
    void toolErrorsStayErrorsAndDoNotDiscardWorkingConnection() throws Exception {
        var provider = provider(true, Duration.ZERO);
        var tool = provider.getToolCallbacks()[0];
        toolError = true;
        assertTrue(mapper.readTree(tool.call("{}")).path("isError").asBoolean());
        toolError = false;
        assertFalse(mapper.readTree(tool.call("{}")).path("isError").asBoolean());
    }

    @Test
    void brokenToolConnectionFallsBackAndReconnects() throws Exception {
        var provider = provider(true, Duration.ZERO);
        var tool = provider.getToolCallbacks()[0];
        unavailable = true;
        assertTrue(mapper.readTree(tool.call("{}")).path("isError").asBoolean());
        unavailable = false;
        assertEquals(1, provider.getToolCallbacks().length);
        assertFalse(mapper.readTree(provider.getToolCallbacks()[0].call("{}"))
                .path("isError").asBoolean());
    }

    @Test
    void rejectsInvalidArgumentsWithoutBreakingConnectionAndClosesCleanly() throws Exception {
        var provider = provider(true, Duration.ZERO);
        var tool = provider.getToolCallbacks()[0];
        assertTrue(mapper.readTree(tool.call("not-json")).path("isError").asBoolean());
        assertFalse(mapper.readTree(tool.call("{}")).path("isError").asBoolean());
        provider.close();
        assertEquals(0, provider.getToolCallbacks().length);
    }
}
