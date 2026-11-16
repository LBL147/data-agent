# AntV 图表 MCP 接入与服务器配置

## 当前项目行为

- `ChartMcpToolProvider` 不在 Spring 启动时访问图表 MCP，而在聊天构造工具时按需连接；其他功能不会因图表服务故障而无法启动。
- 初次握手默认 10 秒；MCP 请求默认 60 秒。失败后默认冷却 30 秒，在下一次聊天中重试，不在后台不断请求。
- 保留服务实际返回的原始工具名和 schema。图表按 `tool_search` 精确类型搜索，不需要一次把全部工具塞入模型上下文。
- 保留完整 MCP 结果，包括 `isError`、`content`、`structuredContent`。工具报错不伪装成出图成功，也不丢失签名链接的查询参数。
- 实时消息、历史会话、旧 `chart_image` 事件使用同一解析器。普通图片展示为图片卡片，地图 HTML 页面和资源链接展示为新窗口链接，不在应用里执行工具返回的 HTML。
- 若地图工具只返回原始配置数据、没有图片或页面 URL，仍可在工具详情查看；项目不会伪造地图图片。图片加载失败会提供原链接，历史图片过期需要重新生成。

本地 `application.yml` 已被 Git 忽略。图表部分配置为：

```yaml
data-agent:
  chart:
    enabled: ${CHART_MCP_ENABLED:true}
    mcp-url: ${CHART_MCP_URL:http://localhost:3033/mcp}
    bearer-token: ${CHART_MCP_BEARER_TOKEN:}
    initialization-timeout: ${CHART_MCP_INITIALIZATION_TIMEOUT:10s}
    request-timeout: ${CHART_MCP_REQUEST_TIMEOUT:60s}
    retry-delay: ${CHART_MCP_RETRY_DELAY:30s}
```

这份工作区的本地文件已默认连接你现有服务器；其他机器应通过 `CHART_MCP_URL` 指定自己的地址。`CHART_MCP_ENABLED=false` 可完全禁止图表调用。

## 收回公网端口：SSH 隧道方案

`deploy/chart-mcp/compose.yaml` 只把 MCP 映射到服务器 `127.0.0.1:1122`，使用你已构建好的 `mcp-server-chart:stable` 镜像，不影响 MySQL、MinIO 或其他容器。

这些是服务器操作步骤，本次项目修改没有自动在你的服务器执行。假设把本项目的 `deploy/chart-mcp` 目录上传到 `/opt/dodo-chart`：

```bash
# 仅停止原图表容器，释放端口；保留原容器和文件供回退。
cd /opt/mcp-server-chart
docker compose stop mcp-server-chart

# 启动新的仅本机可访问配置。
cd /opt/dodo-chart
docker compose up -d
docker compose ps
docker compose logs --tail=30
```

在自己的电脑建立隧道并保持终端打开（使用你的 SSH 用户和已配置的密钥）：

```powershell
ssh -N -L 3033:127.0.0.1:1122 root@36.151.145.166
```

IDEA 的环境变量改成 `CHART_MCP_URL=http://localhost:3033/mcp`，再启动或重启应用。公网安全组也应取消 MCP 1122 的公开放行。如果不使用隧道而使用认证代理，则配置代理的 HTTPS 地址及 `CHART_MCP_BEARER_TOKEN`。只有代理实际校验这个令牌才构成认证；原始 AntV 服务不会因为客户端设置了令牌就自动获得保护。

## 私有渲染与数据外发

将 MCP 部署在自己的 Docker 中不等于图表渲染链路完全私有。当前已验证的服务仍使用默认渲染链路，本次修改没有替你部署私有渲染服务。

若禁止业务图表数据发往第三方，先设置 `CHART_MCP_ENABLED=false`，然后部署并验证自己的 GPT-Vis-SSR 图表生成 API。在图表 Compose 目录创建 `.env`，填写私有 API 的完整地址（路径和响应结构须与 AntV 当前版本一致）：

```dotenv
VIS_REQUEST_SERVER=http://<私有渲染服务主机>:<端口>/<图表生成API路径>
```

重建图表容器以应用环境变量，再用虚构数据验证图片 URL、网络出口和存储位置。确认链路满足要求后才重新启用图表。不能仅填一个未经验证的地址就认为数据已私有化。地图服务可能还有单独的远程接口，应单独检查。

## 验证

```bash
# Maven 使用 JDK 21；测试使用本机模拟 MCP，不调用业务数据库或大模型。
mvn test
node --test src/test/js/chart-results.test.cjs
node --check src/main/resources/static/js/chat.js
```

本次另以两条虚构分类数据实际请求现有服务器，确认普通柱状图返回了可被新解析器识别的无文件后缀图片 URL；这不代替整套 Web 聊天的登录、数据库和模型调用联调。
