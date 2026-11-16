# dodo-agentx

`dodo-agentx` 是基于 `spring-ai-agentx` 构建的 Java Web 智能体项目，当前最核心的能力是 **data-agent 数据分析智能体**。

用户可以用自然语言查询业务数据库，系统会自动理解问题、查看表结构、生成 SQL、校验 SQL、执行查询，并结合数据权限、敏感字段脱敏、复杂计算和图表生成，最终输出一份数据分析报告。

除了 data-agent，项目还内置了用户管理、Skills、文件问答和联网查询等能力，用来展示一个完整业务智能体在真实 Web 项目中的落地方式。

## 当前功能

### data-agent 数据分析

这是 dodo-agentx 当前最核心的功能。

- 自然语言查询示例业务库。
- 自动查看表结构、生成 SQL、校验 SQL、执行 SQL。
- SQL 安全校验：只允许只读查询，拦截危险语句，强制 LIMIT。
- 数据权限：根据当前登录用户的角色和部门自动改写 SQL，限制可见数据范围。
- 敏感字段脱敏：密码、身份证号、家庭住址等字段返回 `********`。
- 复杂计算：通过 `CalculateTool` 计算环比、占比、贡献度等指标。
- 图表生成：通过 AntV MCP Server Chart 生成图片或可视化页面链接；文件上传仍使用 MinIO。

### 用户管理

- 基于 Sa-Token 实现登录、退出、当前用户查询。
- 支持用户、角色、部门管理。
- 内置 `sys_user`、`sys_role`、`sys_dept`、`sys_user_role`、`sys_user_dept` 等系统表。
- 支持 `ALL`、`DEPT_AND_SUB`、`DEPT`、`SELF` 四种数据范围。

### Skills

- 支持 Skills 列表展示、启用、停用、上传、删除。
- 支持本地 `skills/` 目录和数据库记录同步。
- 内置示例 Skill：`data-analysis`、`skill-creator`、`域名创意生成器`。

### 文件问答

- 支持文件上传、解析、预览和多轮追问。
- 支持 PDF、Office、Markdown、HTML、纯文本等格式。
- 小文件直接读取，大文件自动切分并走向量检索。
- 支持图片多模态理解。

### 联网查询

- 通过 Tavily MCP 做实时联网搜索。
- 前端提供联网开关。
- 关闭联网时不注入搜索工具，开启后 Agent 按需调用 Tavily。

## spring-ai-agentx 说明

`dodo-agentx` 基于 `spring-ai-agentx` 框架开发。

GitHub 地址：

```text
https://github.com/bigchuidw3/spring-ai-agentx
```

为了解决部分同学无法连接 GitHub，或者不想额外下载并手动编译 `spring-ai-agentx` 的问题，当前课程项目已经把 `spring-ai-agentx-core` 的源码放到了本仓库：

```text
ai-framework/spring-ai-agentx-core
```

这样直接编译 `LLMentor` 时，会一起编译本地的 `spring-ai-agentx-core`，不需要再单独执行：

```bash
git clone https://github.com/bigchuidw3/spring-ai-agentx.git
mvn install
```

如果想切换到 GitHub 上的最新 `spring-ai-agentx`，可以修改 `agent/dodo-agentx/pom.xml` 里的依赖坐标，把课程内置版本切换为外部框架版本。

## 环境要求

| 组件 | 用途 |
|---|---|
| JDK 21 | 编译和运行项目 |
| Maven 3.9+ | 构建项目 |
| MySQL 8.0+ | 主数据库、示例业务数据、用户权限数据 |
| Redis | Sa-Token 会话、Schema 缓存 |
| PostgreSQL + pgvector | 大文件 RAG 和语义记忆 |
| MinIO | 文件存储、图表图片存储 |
| Docker Compose | 运行 AntV MCP Server Chart 图表服务 |

## 快速启动

以下命令默认在仓库根目录 `LLMentor/` 下执行。

### 初始化数据库

创建数据库：

```sql
CREATE DATABASE dodo_agentx DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

导入初始化脚本：

```bash
mysql -u root -p dodo_agentx < agent/dodo-agentx/sql/init.sql
```

`init.sql` 包含项目运行需要的表、示例业务数据、用户角色部门数据。

### 准备配置文件

复制配置模板：

```bash
cp agent/dodo-agentx/src/main/resources/application-example.yml agent/dodo-agentx/src/main/resources/application.yml
```
修改 `application.yml` 中的占位符：

| 配置项 | 说明 |
|---|---|
| `spring.datasource.*` | MySQL 配置 |
| `spring.data.redis.*` | Redis 配置 |
| `spring.ai.deepseek.api-key` | DeepSeek API Key，主模型使用 |
| `spring.ai.openai.api-key` | DashScope API Key，多模态和 Embedding 使用 |
| `skills.directory` | 本地 `skills` 目录绝对路径 |
| `minio.*` | MinIO 配置 |
| `embeddings.store.*` | PgVector 配置 |
| `tavily.api-key` | Tavily API Key，不使用联网可保持占位 |
| `data-agent.chart.mcp-url` | AntV MCP 地址，由 `CHART_MCP_URL` 覆盖，默认 `http://localhost:3033/mcp` |

### 启动 MinIO

按你的环境启动 MinIO，并保证 `application.yml` 中的 `minio.*` 配置正确。

MinIO 主要用于存储上传文件和图表图片。

### 启动 AntV MCP Server Chart

在有 Docker Compose 的服务器上部署：

```bash
git clone https://github.com/antvis/mcp-server-chart.git
cd mcp-server-chart
```

上游默认 Compose 使用 SSE。`dodo-agentx` 使用 Streamable HTTP，因此将 `docker-compose.yaml` 中 `--transport` 后的 `sse` 改为 `streamable`，并确认端口映射为 `1122:1122`，再执行：

```bash
docker compose up -d --force-recreate
docker compose logs --tail=30
```

日志应显示服务监听 `/mcp`。若 dodo-agentx 在本机运行，可在 IDE 运行配置中设置：

```text
CHART_MCP_URL=http://<MCP服务器地址>:1122/mcp
```

若 dodo-agentx 也在同一个 Docker Compose 网络内运行，使用 MCP 服务名作为主机名，例如 `http://mcp-server-chart:1122/mcp`。不要在应用容器中用 `localhost` 访问另一个容器。

AntV 默认会把图表配置和数据发送到其远程渲染服务。包含敏感数据时，应先部署自有 GPT-Vis-SSR 并通过 `VIS_REQUEST_SERVER` 配置；同时限制 MCP 端口的访问来源，生产环境建议通过 HTTPS 和认证代理访问，不要直接暴露无认证的 HTTP 端口。

前端兼容 AntV 图表、关系图、地图/表格页面链接、旧工具名及带客户端前缀的历史记录；支持完整 MCP/JSON、Markdown、无后缀图片 URL 和 MCP image。图表 MCP 改为按需连接，故障时继续分析并在后续聊天重试。

详细配置、SSH 隧道、仅本机端口的 Docker Compose、私有渲染要求和测试方法见 [图表 MCP 接入说明](docs/chart-mcp.md)。其中服务器端操作需要另行执行，本次项目修改不会自动调整现有服务器。

### 编译并启动 dodo-agentx

在仓库根目录先编译整个项目：

```bash
mvn clean install -DskipTests
```

编译成功后，直接在 IDE 中运行 dodo-agentx 的启动类：

```text
agentx.mentor.llm.com.pangruixin.DodoAgentxApplication
```

启动成功后访问：

```text
http://localhost:8889/
```

## 默认账号

初始化脚本内置了示例账号：

| 账号 | 密码        | 说明 |
|---|-----------|---|
| `admin` | `123456`  | 管理员，数据范围为 `ALL` |

其他用户管理中的内置用户，密码均为：123456
可以用两个账号问同一个 data-agent 问题，对比数据权限和敏感字段脱敏效果。

## 常见问题

### 找不到 spring-ai-agentx-core

请确认你是在仓库根目录执行过完整编译：

```bash
mvn clean install -DskipTests
```

这样会把 `ai-framework/spring-ai-agentx-core` 一起编译安装到本地 Maven 仓库，dodo-agentx 才能正常依赖它。

### 图表生成失败

优先检查：

- AntV 图表容器是否运行，日志是否显示 Streamable HTTP `/mcp`。
- `CHART_MCP_URL` 是否指向可达的 MCP 地址，`CHART_MCP_ENABLED` 是否开启。
- 工具调用和图片加载是两段链路：浏览器也须能访问返回的图片 URL。
- 图表生成不依赖本项目 MinIO 配置；私有渲染 API 及图片存储需在 MCP 服务侧设置。
- MCP 连接失败后，等待默认 30 秒冷却，再发起新的聊天请求重试。

### 不想启用联网搜索

可以不配置 Tavily API Key，并在前端关闭联网开关。关闭后不会注入 Tavily 工具。
