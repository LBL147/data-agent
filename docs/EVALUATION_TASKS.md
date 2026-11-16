# Dodo-Agentx 评测工程实施需求

## 人机协作交互规范

- **AI 的角色**：Codex 同时是导师和执行者。每次开始新的 Session，先核对学习交接记录，再做微理论科普；不能直接开始写代码、迁移或批跑。本节约束 S1–S9 全部会话。
- **理论逐条讲解原则**：各 Session 的【🎓 学习目标与微理论】是待讲清单，不是一次性授课稿。按 1、2、3 的顺序，每次只讲一个序号，结合真实代码说明概念、为什么本项目需要它，并给出一个反例。讲完问“这一点听懂了吗？”，停下来等用户回复“继续”或提问；提问只授权解释当前点，答疑后再次等待，不能自行推进下一点。绝对不要一次性把全部理论讲完。
- **理论到实操的门槛**：所有理论点都获得用户明确理解确认后，单独询问是否进入 Step 1；最后一个理论点后的“继续”可作为进入 Step 1 的确认，但不替代代码修改或数据库执行方案的专项确认。阅读清单、沉默、等待超时、完成本文改写，都不代表理论过关。
- **分步执行原则**：实操以 Step 1、Step 2 等推进，每次只做一个 Step；若一步包含多个独立改动，先拆成 Step 1.1、Step 1.2 等小步，一次只做一个小步。每步先解释“为什么改这里”“改了有什么作用”，列出涉及文件、具体方案和本步验收；获得本步所需确认后，只完成该范围并展示实际结果，然后停下来等待确认，才能进入下一步。禁止一次写完整个 Session 的代码或顺手完成后续步骤。
- **确认边界**：S1 的工具契约、数据库迁移和文件权限修复必须先给具体方案再等待确认。SQL 应先展示适用于目标库的语句和执行目标，确认后才能执行；写好迁移文件不等于获准连接数据库执行。当前小步获准后，其范围内必要编译、测试和报错修复可继续，不能借此扩大范围。用户明确批准的一组剩余路径可按批准范围处理；默认仍逐路径推进。
- **每轮交互格式**：理论轮次为“当前理论编号 → 项目代码锚点 → 一个反例 → 理解确认并停止”；实操轮次为“当前 Step → 原因/方案 → 等待必要确认 → 本步执行与验证 → 结果解释 → 下一步预告并停止”。不能在等待期间并行补写下游脚手架、提前跑批或代替人做语义裁决。
- **进度持久化**：在对应 `docs/evaluation/S<n>_HANDOFF.md` 记录已确认理论、当前理论/Step、待确认方案、用户批准范围、实际验证和下一动作，字段见 3.4。首次尚无交接文件时先在对话中建立进度，经确认进入实操后再创建文件。重开会话先恢复检查点，不重复已确认内容，不跳过未确认项。记录进度属于当前小步收尾，不能借机执行下一步。
- **文档与执行区分**：下面完整列出课程提纲和未来任务，便于长期执行；列出它们不代表已讲解或已实施。本次文档更新后仅开始 S1 理论 1，不执行任何 S1 工程变更。

## 0. 全局上下文与前置修复项

### 0.1 文档定位与项目现状

- 本文件是后续 S1–S9 开发会话的唯一执行蓝图。每次执行指定 Session 前，先读取本文件、该 Session 的交接记录、当前源码和工作区差异；未通过验收的事项不得标记完成。
- 文档创建日期：2026-11-17，时区 Asia/Shanghai。源码基准：`7ddf011e8adb602267f3797ea009ff9936261382` 加创建时已有的未提交改动；后续运行必须重新记录实际源码指纹。
- 当前项目是单模块 Java Web 应用，使用 Java 21、Spring Boot 3.5.6、Spring AI 1.1.0 和外部依赖 `spring-ai-agentx-core:1.0.1-M2`。MySQL 主数据源同时承担业务、用户权限、会话和框架持久化。
- 已有 `AgentController → DodoAgent → ReactAgent → ToolCallback` 链路，以及 SQL 校验、权限改写、EXPLAIN 预检查、JDBC 执行、脱敏、计算、文件解析、Skills 和 MCP 能力；尚缺本文件要求的完整评测工程。
- 当前默认使用静态 YAML Schema，权限和脱敏开启，查询行数上限 200、JOIN 上限 3，工具表格只预览前 20 行。预览数据不能作为完整执行结果评分。
- PgVector 有实现但当前配置未提供 `embeddings.store.*`，真实 RAG 可用性未确认；图片通过多模态服务生成文字描述并缓存，再交给主 Agent。Tavily、图表 MCP 和模型服务的实际可用性须由运行前检查确认。
- `agentx-core` 源码未内置于当前仓库。本地 JAR 字节码确认存在 `TraceStore`、LLM/TOOL span、工具耗时和 Hook 接口，trace 默认开启；实际数据库是否已迁移、span 是否落库仍未确认。不得将“依赖具备接口”写成“线上运行已验证”。
- 本文件创建只交付需求文档，不代表下面的源码修复、迁移、脚本或评测已实施。初始状态全部为 `TODO`。

### 0.2 源码证据与路径约定

以下路径相对仓库根目录，便于在其他机器复用。本次仓库根目录为 `C:/Users/l'b'l/Desktop/dodoAgent`。Java 包路径统一记为 `src/main/java/com/pangruixin/llm/mentor/agentx/`，下表中的 Java 文件均位于此包路径下。

| 领域 | 已有文件/类 | 执行时必须保留的边界 |
| --- | --- | --- |
| 构建与配置 | `pom.xml`、`src/main/resources/application.yml` | JDK 21；依赖和配置需要记录实际版本与 hash |
| 入口与编排 | `controller/AgentController.java`、`domain/dto/ChatRequest.java`、`DodoAgent.java` | 登录身份由服务端注入；当前 `.dataSource(dataSource)` 用于框架持久化，不能整体替换为公开评测库 |
| 提示词与 Skill | `prompt/SystemPrompt.java`、`skills/data-analysis/SKILL.md`、`skill/SkillManager.java` | Skill 内容、启用状态和工具列表是实验输入；提示词要求不等于后端强制约束 |
| SQL 执行 | `tools/ExecuteSqlTool.java`、`tools/ValidateSqlTool.java`、`service/SqlSafetyGuard.java`、`service/ExplainPrecheckService.java`、`service/ReadOnlyQueryRunner.java`、`domain/entities/SqlResult.java` | 工具返回文本；内部有结构化结果；JDBC 有内部重试；EXPLAIN 的部分异常 fail-open |
| Schema 与术语 | `service/SchemaProvider.java`、`service/YamlSchemaProvider.java`、`service/SchemaCatalogService.java`、`service/MschemaProvider.java`、`service/MschemaIntrospector.java`、`service/MschemaCacheService.java`、`src/main/resources/schema/dodo_agentx.yml` | YAML 路径固定；MSchema 绑定主数据源，Redis key 固定为 `data-agent:mschema:schema`；现有术语带有 Sakila 业务语义 |
| 权限与脱敏 | `common/datascope/DataScopeResolver.java`、`service/DataScopeRewriter.java`、`service/permission/*`、`service/SensitiveFilter.java`、`service/UserContextBuilder.java` | 当前 SQL 行权限规则主要覆盖 `payment/rental/sys_user/user_profile`，不能宣称所有表均受保护 |
| 登录与角色 | `config/SaTokenConfig.java`、`config/StpInterfaceImpl.java`、`auth/service/impl/AuthServiceImpl.java` | 区分登录、管理员功能权限和 SQL 数据范围；多角色采用最大数据范围 |
| 文件与 RAG | `controller/FileController.java`、`service/FileManageService.java`、`domain/entities/AgentxFile.java`、`tools/AnalyzeFileTool.java`、`service/FileParserService.java`、`service/EmbeddingService.java`、`service/MultimodalService.java`、`service/MinioService.java`、`config/PgVectorConfig.java` | 文件访问不自动受 SQL 权限规则保护；RAG 与图片缓存需分别验收 |
| 计算与外部工具 | `tools/CalculateTool.java`、`tools/TimeTool.java`、`config/AgentxMcpConfig.java`、`service/ChartMcpToolProvider.java` | 模型可见结果形态不统一；MCP 工具 Schema 在运行时发现；图表已有失败降级与冷却重连 |
| 回放与 trace | `service/SessionService.java`、`controller/SessionController.java`、`domain/entities/AgentxSession.java`、`domain/entities/AgentxConversation.java`、`sql/init.sql` | 回放从 `original_messages` 重建；消息顺序和 SSE 接收时间不能代替真实 span 耗时 |
| 已有测试 | `src/test/java/com/pangruixin/llm/mentor/agentx/service/ChartMcpToolProviderTest.java`、`src/test/js/chart-results.test.cjs` | 测试集中在图表 MCP 和前端解析；不得据此宣称 SQL、权限、RAG 或评测已覆盖 |

本地已核对 JAR：`C:/maven/repository/com/agentx/ai/spring-ai-agentx-core/1.0.1-M2/spring-ai-agentx-core-1.0.1-M2.jar`，SHA-256 为 `C596AD97CCB41F189CEE77A04B6965D2BE15DD763D06FFF4865A22171B38FCEF`。这是创建时的证据，执行时须按实际 Maven 解析结果重新获取，不硬编码本机路径。

### 0.3 必须最先解决的三个已知源码隐患

1. **Skill 契约不一致：`variables` 与 `variablesJson`。**
   - 证据：`skills/data-analysis/SKILL.md` 示例传入 `variables` 对象；`tools/CalculateTool.java` 实际接收 `String expression, String variablesJson`。
   - 要求：统一文档、提示词示例和真实工具 Schema。优先修正示例，避免无理由修改已有 API；示例必须明确 JSON 字符串的编码形态。
   - 验收：示例通过真实 `ToolCallback` 调用得到预期结果；非法 JSON、缺少变量、除零均有业务失败记录；不能只直接调用 Java 方法来证明模型侧契约正确。
2. **Trace 表结构不兼容本地 `agentx-core` JAR。**
   - 证据：`sql/init.sql` 中旧 `agentx_trace` 缺少本地 JAR 写入使用的 `user_id/span_type/tool_name/tool_call_id/compact_id` 等字段；JAR 具备建表能力，不代表会升级既有表。
   - 要求：先检查目标数据库实际 DDL 与当前 JAR 所需字段，再提供增量、可重复执行的兼容迁移；核对 `session_id` 含义、类型和索引。兼顾全新初始化与存量升级，不通过重建表丢弃历史数据。
   - 验收：旧表升级和新库初始化均能写入、查询 LLM 与 TOOL span；重复执行迁移不报错且保留已有记录；注入保存失败时评测健康检查必须报错，不能以 Agent 能回答替代 trace 验收。
3. **文件查询未校验用户归属：`FileManageService`。**
   - 证据：`AgentxFile` 没有文件所有者字段；`getFileInfo/findByFileId` 按 `fileId` 查询，关联会话、内容读取、下载、缓存写入等方法未统一校验归属；`AnalyzeFileTool` 会在异步工具链调用这些入口。
   - 要求：建立文件所有者与会话归属模型，从可信服务端上下文传递用户身份。覆盖上传、元数据、内容、预览、下载、RAG、图片缓存读写、关联会话/轮次和删除。不能接受 LLM 自报身份，也不能仅依赖异步线程中的 Sa-Token ThreadLocal。
   - 要求：存量归属只能依据可靠记录回填；无法确认归属的文件默认拒绝访问并列入人工核查清单，禁止统一归给某个用户。关联操作须同时验证文件和既有目标会话归属，混合合法/非法 fileId 的请求应整体拒绝，避免部分修改。
   - 要求：检查 `FileController` 的公共缓存策略及 MinIO 直链是否绕过后端授权；若存在绕过，纳入本项修复和兼容说明。
   - 验收：用户 A 能访问自己的文件，用户 B 和缺失身份请求不能读取、关联或改写 A 的文件；在异步工具、RAG 和图片缓存路径重复验证。至少覆盖元数据、内容、预览、下载、会话关联、RAG、图片缓存、删除八类路径；断言拒绝前没有调用外部读取/推理或发生数据修改。

三个隐患须在 S1 前或 S1 中修复并留下回归证据。文件归属跨多个入口，S1 包含这部分工作后不应按一篇文章的工作量估算。只完成 Runner 而未关闭这三项，S1 仍为未完成。

### 0.4 统一工程契约与产物约定

- **复用分类**：已有可直接使用的能力记为 `REUSE`；现有代码需改造记为 `ADAPT`；仓库没有的能力记为 `NEW`。以下拟新增路径是要求，不是已有能力证据。
- **建议目录**：`eval/schemas/` 保存 JSON Schema，`eval/config/` 保存套件和预算配置，`eval/datasets/` 保存受版本管理的小型用例与数据 manifest，`eval/scripts/` 保存入口脚本，`eval/judge/` 保存 rubric 和校准材料，`eval/runs/<run_id>/` 保存运行包。大数据与密钥不进入 Git，按 manifest 定位。
- **新增 Java 代码**：应用集成组件放在现有根包下的 `eval` 子包；生产功能修复仍放在原模块。继续使用单 Maven 模块，除非在本文件中记录了有证据支持的调整。
- **命令入口**：提供 Windows PowerShell 可直接运行的预检查、执行、续跑、评分、比较、trace 查询和重放命令。README 中必须写明参数、环境变量、退出码；不将不可运行的伪命令作为交付。
- **Run manifest 必填**：`schema_version/run_id/suite_id/dataset_version/dataset_hash/subset_hash/code_revision/worktree_diff_hash/dependency_hashes/config_hash/schema_hash/skill_hashes/tool_schema_hashes/model_id/model_parameters/evidence_mode/identity_fixture/time_anchor/engine_version/comparator_version/budgets/started_at`。不支持固定 seed 的模型明确记录，不承诺逐字复现。
- **运行记录最少字段**：`run_id/case_id/variant_id/attempt_id/conversation_id/session_id/target_id/user_fixture_id/status/error_kind/answer/evidence_ids/started_at/ended_at`。某能力未启用时相关字段应显式为 `null` 或声明不适用，不伪造值。
- **状态语义**：终态为 `completed/failed/timed_out/cancelled/interrupted/policy_blocked/skipped`。`completed` 仅代表执行完成，不代表答案正确；预期拒绝可以是业务用例通过，但必须保留实际拦截状态；跳过原因和数量必须报告。
- **状态分层**：区分框架调用完成、工具业务成功、策略允许、答案正确和评测用例通过。正常返回的错误文本、MCP `isError`、保护性拒绝都不能一律标成工具业务成功。
- **关联与时间**：以 `run_id → case_id/variant_id/attempt_id → conversation_id/session_id → tool_call_id/span_id` 关联，保留真实服务端时间与耗时。`session_id` 表示框架本次调用 ID，不凭旧表注释假定为某条消息行主键；身份切换必须使用独立会话。
- **SQL 阶段**：保留原始候选 SQL、Guard 处理后 SQL、权限改写后 SQL、实际执行 SQL、最终提交引用及其 hash。探针、失败、重试、最终答案查询必须可区分；不默认选择最后一次 SQL。
- **结果采集**：从结构化 `SqlResult` 和业务事件采集，不能反向解析 Markdown 预览作为完整结果。业务数据只持久化授权且脱敏后的证据，敏感命中记录列名/数量/hash；不要为评测新增明文敏感快照。
- **运行包**：至少包含 `manifest.json/cases.jsonl/events.jsonl/trace-index.jsonl/summary.json`；S3 起增加 `predictions.jsonl/verification.jsonl`；S7 起增加 `scores.jsonl/report.md/report.html`。离线评分只读这些运行产物，不再次调用被测 Agent。
- **隔离**：业务回归和公开 BIRD 使用不同套件、提示词、数据与策略声明。主库承载认证/会话/trace，公开目标只读；不得全局替换主数据源、关闭生产权限或把 gold 暴露给被测模型。目标由可信 Runner 的白名单注册表选择。
- **首批运行即有边界**：S1 提供最小 wall-time、轮数和并发限制，S4 批跑前必须启用；S9 再将其扩展为跨 Agent、JDBC 重试和外部调用的一致预算。不能等 S9 才限制早期批跑。
- **实操小步约定**：S2–S9 下列任务编号分别对应 Step 1、Step 2 等，不代表一次执行的整包。每个 Step 均须先说明代码位置、原因、影响与验证方式，必要时继续拆子步；完成当前小步后停止，等待用户确认。批跑的 20→100→完整集、校准材料准备→判分→分析也必须分开确认。
- **可观测性验收**：本地依赖已有 span 优先复用，新增业务阶段事件补缺；在 S1 没有落库证据前不得宣称 trace 接通。框架 Hook 的精确接口以锁定 JAR/源码为准，不臆造扩展 API。
- **安全的 gold**：BIRD 参考 SQL、人工裁决和业务 oracle 只供验证器/审核使用，必须与模型可访问的 Schema、文件系统工具和诊断工具隔离。

### 0.5 会话依赖与完成状态

```text
S1 → S2 → S3 → S4 ─┐
 └→ S5 → S6 ────────┼→ S7 → S8 → S9
          S5 ────────┘
```

| Session | 初始状态 | 必要依赖 | 完成证据 |
| --- | --- | --- | --- |
| S1 | TODO | 可用隔离环境；三项前置修复纳入本会话 | 待填写 |
| S2 | TODO | S1 | 待填写 |
| S3 | TODO | S2 | 待填写 |
| S4 | TODO | S3 | 待填写 |
| S5 | TODO | S1 | 待填写 |
| S6 | TODO | S5；人工参考标注 | 待填写 |
| S7 | TODO | S4、S5、S6 | 待填写 |
| S8 | TODO | S7 | 待填写 |
| S9 | TODO | S8；成本与停止策略确认 | 待填写 |

允许的状态为 `TODO/IN_PROGRESS/PENDING_HUMAN/BLOCKED/DONE`。只有本文件对应验收全部通过才可标记 `DONE`。运行服务或数据缺失要写明具体阻塞，不把“脚本已写”当作“评测已跑通”。

## 1. 核心实施会话（S1 - S9）

### S1：执行与 trace 冒烟闭环

#### 【🎓 学习目标与微理论】

学习目标：能区分回答、过程证据与业务判定，并看懂一次真实调用的证据链。以下逐项讲，每次只展开一个序号，确认后才能继续。

1. **为什么 Agent 评测不能只看最终回答？** 代码锚点：`ExecuteSqlTool` 的权限改写、脱敏与 `CalculateTool`。讲解重点：相同回答可能来自不同数据范围或计算过程。反例：本应查询本人收入，却查询全公司后给出看似合理的数字，只看回答会漏掉越权。
2. **什么是 Trace（链路追踪）？** 代码锚点：`DodoAgent`、依赖 `TraceStore` 与 `sql/init.sql` 的 `agentx_trace`。讲解重点：按调用 ID 串联模型、工具、输入输出引用与耗时；实际落库仍需验证。反例：页面显示回答成功，但 trace 保存失败，事后无法确认执行过哪条 SQL。
3. **为什么“工具返回成功”不等于“业务成功”？** 代码锚点：`CalculateTool.calculate`、`ExecuteSqlTool.executeSql` 的错误文本返回。讲解重点：函数正常返回、业务成功、策略允许和用例通过分别判断。反例：除零返回“❌ 结果为无穷大”，没有抛异常，不能因此判为计算正确；写库被拦截则可能符合该用例预期。

- **目标**：关闭三个前置源码隐患，交付能够真实运行、记录终态并查询 trace 的 12 样本最小评测闭环。
- **涉及模块/文件**：`DodoAgent`、`AgentController`、`SessionService`、`CalculateTool`、`skills/data-analysis/SKILL.md`、`FileManageService`、`AgentxFile`、`FileController`、`AnalyzeFileTool`、`EmbeddingService`、`sql/init.sql`、`pom.xml`；复用本地 `TraceStore`。新增 Runner、记录 Schema、兼容迁移和文件授权回归测试。
- **前置依赖**：无其他 Session；需 JDK 21、隔离 MySQL 数据副本、可用登录/角色夹具、模型连接和必要的 Redis。文件归属验证需测试对象存储/文件夹具；涉及真实外部服务的验证必须如实记录。不能连接生产数据执行测试初始化。
- **具体任务拆解**：

  **Step 1：修复工具契约。**

  - Step 1.1（只读定位与方案）：打开 `tools/CalculateTool.java` 和 `skills/data-analysis/SKILL.md`，指出真实签名为 `calculate(String expression, String variablesJson)`，Skill 却传 `variables` 对象。解释错误参数会让评测把契约故障误归为模型计算能力不足。优先修 Skill 示例并核对工具参数描述，不无理由修改 API。展示以下候选参数及预期约为 `6.2158%`，然后等待确认，不能直接改：

    ```json
    {"expression":"(curr - prev) / prev * 100","variablesJson":"{\"curr\":3298,\"prev\":3105}"}
    ```

  - Step 1.2（确认后修改与验证）：只改获批契约范围，通过真实 Spring AI `ToolCallback` 测试上述参数；验证非法 JSON、缺少变量和除零能被记录为业务失败。先确认本步 JDK/Maven 可用，不把直接调用 Java 方法当成完整契约测试。展示差异、结果和异常含义，等用户确认再进入 Step 2。

  **Step 2：数据库迁移。**

  - Step 2.1（核对与展示）：解释旧 trace 表缺列可能使 LLM/TOOL 写入失败，即使回答仍返回也会丢失证据。读取隔离目标的实际 DDL、数据库版本和锁定 JAR，核对字段、`session_id` 含义、类型、索引和历史数据。先给下面的 ALTER 草案；正式执行前必须按目标现状只保留确实缺少的列，并展示最终 SQL、目标库和历史数据保留方案，等待用户确认：

    ```sql
    -- 待核对草案：仅适用于下列五列全部缺失且与当前 JAR 类型匹配的旧表。
    -- 不在本次文档改写中执行；已有列不得重复 ADD。
    ALTER TABLE agentx_trace
      ADD COLUMN user_id VARCHAR(100) DEFAULT NULL,
      ADD COLUMN span_type VARCHAR(8) NOT NULL DEFAULT 'LLM',
      ADD COLUMN tool_name VARCHAR(128) DEFAULT NULL,
      ADD COLUMN tool_call_id VARCHAR(100) DEFAULT NULL,
      ADD COLUMN compact_id BIGINT DEFAULT NULL;
    ```

  - Step 2.2（确认后生成迁移工程件）：形成增量迁移和 `sql/init.sql` 新库兼容方案，迁移入口通过元数据检查避免重复 ADD；不假定 MySQL 支持所有方言的 `ADD COLUMN IF NOT EXISTS`。在隔离夹具验证旧表升级、新库初始化、二次执行不报错及历史记录保留。展示结果并停止；目标数据库执行仍需确认具体 SQL 与目标。
  - Step 2.3（确认后执行与验收）：仅对获批隔离库执行最终 SQL，验证真实 LLM/TOOL span 的写入与查询、调用 ID 关联，以及保存失败时健康检查能报警。逐步解释查询结果；没有模型服务时只记录迁移测试通过，真实 span 验收仍待办。等确认再进入 Step 3。

  **Step 3：文件越权修复。**

  - Step 3.1（只读定位与方案）：解释“读到别人文件后回答正确”仍是安全失败，会污染回答质量和 RAG 结论。先展示以下八类需要收口的访问路径；它们是八类入口，不是仅改八个文件。提出可信身份传递、文件所有者字段/迁移与公共授权入口的最小方案，等待确认。

    | 路径 | 核心文件/方法 | 必须验证 |
    | --- | --- | --- |
    | 1. 元数据 | `FileManageService.getFileInfo/findByFileId`、`AgentxFile` | A 可读自己的文件，B/缺失身份不可读 |
    | 2. 文本内容 | `FileManageService.getFileContent`、`AnalyzeFileTool.analyzeFile` 的直读分支 | 拒绝发生在内容返回之前 |
    | 3. 预览 | `FileController.preview` | 鉴权、公共缓存和直链不绕过归属校验 |
    | 4. 下载 | `FileManageService.downloadFileBytes`、`MinioService` | 未授权时不读取对象存储 |
    | 5. 会话关联与列表 | `linkToConversation/linkToSession/listByConversationId/listBySessionId` | 同时校验文件和目标会话归属，混合非法 ID 请求整体拒绝 |
    | 6. RAG 检索 | `AnalyzeFileTool.analyzeFile → EmbeddingService.ragRetrieve` | 未授权时不检索向量或调用推理 |
    | 7. 图片缓存读写 | `AnalyzeFileTool.handleImage`、`FileManageService.saveExtractedText` | 缓存命中/未命中都鉴权，拒绝时不识别或改写缓存 |
    | 8. 删除 | `FileController.delete → FileManageService.deleteFile` | 未授权时不删除对象或数据库记录 |

  - Step 3.2（只修核心一路）：获批后先实现路径 1 的最小闭环：在 `getFileInfo` 收口服务端可信身份与所有者校验，补充所有者模型、上传时写入可信归属等必要基础，写一组聚焦单测。至少验证 A 自己可读、B 被拒绝、缺失身份被拒绝、未知历史归属被拒绝四种情况。异步身份不能仅靠 Sa-Token ThreadLocal，也不能接受模型自报 `userId`。先展示测试通过与实际改动，停下来等确认；此时不得声称八路修复完成。
  - Step 3.3–3.9（确认后扩展其余七路）：用户确认核心路径后，复用统一授权入口，按表逐路增加改动及正/反例，每次只做一路并等待确认；只有用户明确批准剩余路径的分组范围才可成组改造。图片缓存读写和 RAG 不能漏掉；每路断言拒绝前无外部读取/推理/修改。可靠记录才能回填历史归属，无法确认则默认拒绝并交人工核查。八路汇总验收后再请求进入 Step 4。测试删除只处理明确的单个隔离文件，不批量清理。

  **Step 4：搭第一版冒烟跑批。**

  - Step 4.1（只准备一个样本）：确认 JDK 21、Maven/JAR hash、隔离 MySQL/Redis、模型、登录和角色夹具；默认 `java` 曾指向 JDK 17，不能只看 IDE。展示一个合成数据收入聚合样本及独立核验的预期，只规划最小脚本、最小记录字段和执行边界，等确认后再写；不一口气搭完整框架。
  - Step 4.2（确认后只跑一个样本）：先写最简单的 PowerShell 入口，经真实登录身份调用应用/Agent，使用新会话、并发 1、有限轮数和 wall-time。逐段展示“输入 → 身份注入 → 模型/工具 → 终态 → trace 落库与查询”，能定位真实 LLM 与 TOOL span。记录业务错误，不能只看 SSE 结束。脚本写好而服务不可用时记录阻塞，不虚构成功。讲清一个样本的证据包后，停下来确认用户理解了完整流程。
  - Step 4.3（确认后补最小工程契约）：将已跑通记录整理为 `RunManifest/RunRecord/ToolEvent` Schema、Runner 适配与 trace 导出，补 `preflight.ps1` 和保存故障检查；仍只验证一个样本。记录输入指纹、身份/会话/尝试 ID、终态、输入输出引用与耗时，采集异常必须暴露。若需多处独立改动，先拆小步逐个确认，不借整理之名实现全套评测平台。
  - Step 4.4（确认后扩展到 12 个）：先展示六类问题及两个身份的用例矩阵、oracle 和拒绝条件，确认后才扩展与运行：收入聚合、部门/关联查询、敏感字段带别名查询、两个聚合值后的增长率计算、合法空结果、应拒绝的写库请求；以 `SELF`、`DEPT_AND_SUB` 分别执行，形成“6 类问题 × 2 个身份 = 12 个样本”。oracle 不调用被测权限改写器生成；增长率用合成数据独立核算。每次尝试新建会话，保证可信异步身份和会话隔离。
  - Step 4.5（确认后故障检查与交接）：另外注入一次可控失败/超时，验证终态、证据保留及后续样本可继续，不计入 12 样本覆盖数。生成实际运行包、冒烟报告、命令说明和 S1 交接记录，逐项解释完成/失败/拒绝的差异。只有全部验收通过才完成 S1，不宣称已经完成 BIRD 或完整业务评测。

- **验收标准**：
  - 三个理论点均有用户理解确认；所有 Step/小步的方案、批准范围、实际结果与等待点均可从交接记录核对，不能事后补写为已确认。
  - 保留“先 1 样本闭环，确认理解后才扩成 12 样本”的两阶段真实运行证据；未获得确认不得进入扩跑。
  - 三个前置隐患各有通过的自动回归证据；文件归属至少覆盖 0.3 定义的八类路径及缺失身份。
  - 12/12 样本均有唯一身份/会话/尝试 ID、输入指纹、一个明确终态和可定位的预期结果；样本不全通过也可形成有效报告，但不得将实际失败写成通过。
  - 至少一次正常模型/工具运行能在真实数据库中查询到 LLM 与 TOOL span，且所有已落库工具 span 可通过调用 ID 关联；保存故障能被健康检查发现。
  - 12 样本的执行完成、业务判定、保护性拒绝分别统计；可控失败后其他样本仍能运行，超时不永久悬挂。
  - 同一命令可重新运行并生成独立运行包；重跑不读取前一次会话记忆；用户 B 不能查询用户 A 的文件或运行证据。
- **交接给下游的产物**：
  - 三项前置修复代码、增量迁移、新库初始化适配及对应测试。
  - `eval/schemas/run-manifest.schema.json`、`run-record.schema.json`、`tool-event.schema.json`。
  - `eval/scripts/preflight.ps1`、`run-eval.ps1`、`export-trace.ps1` 与 `eval/README.md` 中可复制的调用方式。
  - `eval/datasets/smoke/cases.jsonl`、身份/数据夹具、人工核验记录以及 `eval/runs/<run_id>/` 的实际 12 样本运行包。
  - `docs/evaluation/S1_HANDOFF.md`，列出环境、命令、验收结果、接口、未确认项和下游注意事项。
  - 一个样本的教学运行包与解释记录、八类文件访问路径覆盖矩阵、逐理论/Step 确认检查点；交接字段遵循 3.4。

### S2：BIRD 数据与 Schema 接入

#### 【🎓 学习目标与微理论】

学习目标：认识公开 SQL 数据、比较口径和 Schema 输入，能解释为什么必须隔离目标库。每次只讲一个序号。

1. **什么是 BIRD 评测？** 代码锚点：拟新增 BIRD manifest、现有 `SchemaProvider` 与 `ReadOnlyQueryRunner`。讲解重点：以锁定的自然语言问题、数据库和参考 SQL 评公开结果，当前仓库尚未接入。反例：直接把 Sakila 业务问题命名为 BIRD，或让 Agent 读取参考 SQL，再报告公开成绩。
2. **什么是 EX（执行准确率）？** 代码锚点：拟新增 S3 独立验证器、锁定官方比较器。讲解重点：按照固定协议比较预测与参考 SQL 的执行结果，并保留分母。反例：SQL 能执行就记为正确，忽略查错部门；这既不能证明 EX 正确，也不能证明权限正确。
3. **为什么 Schema 改造是 NL2SQL 的核心难点？** 代码锚点：`SchemaCatalogService` 的固定 YAML、`MschemaCacheService` 的固定 key 与业务 glossary。讲解重点：模型看到的表、列、关系和口径须对应当前目标。反例：切换 `db_id` 却复用上一库 Schema，生成语法正确但目标错误的 SQL。

- **目标**：将锁定版本的 BIRD Mini-Dev MySQL 数据和 Schema 接入隔离目标，确保公开数据查询与应用管理持久化互不串用。
- **涉及模块/文件**：`SchemaProvider`、`YamlSchemaProvider`、`SchemaCatalogService`、`MschemaProvider`、`MschemaIntrospector`、`MschemaCacheService`、`DodoAgent`、`ReadOnlyQueryRunner`、`ExplainPrecheckService`、`application.yml`；新增 `EvaluationTarget`、目标注册表、BIRD Schema Provider 和转换脚本。
- **前置依赖**：S1 完成；取得可用的官方数据、数据库说明及其版本/来源记录。默认路线为 Mini-Dev MySQL，具体 revision、完整集大小和官方比较脚本在本会话核实；不能沿用未核实的历史数量。
- **具体任务拆解**：
  1. **Step 1**：固定数据版本、来源、文件 hash、数据库引擎版本、evidence 开关和子集选择方法；保存官方比较器 revision。记录标注修订信息，不自动跟随上游更新。
  2. **Step 2**：构建隔离 MySQL 目标与只读查询连接，管理数据源继续服务认证、Skills、会话、暂停和 trace；不能直接替换 `DodoAgent` 的持久化 DataSource。
  3. **Step 3**：建立可信 `target_id/db_id → 连接、catalog/schema、表映射` 注册表。以所选数据包实际布局为准，允许同实例多库或逻辑目标映射；不能假定每个 `db_id` 必须对应一个新数据源。
  4. **Step 4**：改造查询与 Schema 获取的目标上下文，覆盖 Guard 后的 EXPLAIN、JDBC 和 Schema 自省；使用显式不可变上下文或经过测试的异步传播机制，避免全局可变数据源与未传播的 ThreadLocal。
  5. **Step 5**：通过已有 `SchemaProvider` 接口接入表、列、类型、主外键、描述和受控样例。解除静态字典路径与固定缓存 key 对评测的限制；缓存至少按数据版本、引擎、目标和 Schema hash 隔离。
  6. **Step 6**：防止 Sakila glossary、默认业务时间口径和管理表串入 BIRD 提示词。样例值采集不得读取 gold，业务模式使用 MSchema 时还须检查样例值是否泄露未授权行。
  7. **Step 7**：创建独立 `benchmark-bird` 配置与提示词声明，保留只读与危险语句防护；公开评测需要的 LIMIT/JOIN/EXPLAIN 差异必须显式记录，不能全局修改业务模式。
  8. **Step 8**：固定覆盖至少两个 `db_id` 的 20 题工程子集，导出 Schema、工具 Schema、目标映射和预检查报告。SQLite/PostgreSQL 适配不属于本次默认交付，若切换路线须更新本文件与验收条件。
- **验收标准**：
  - 20/20 题都有稳定 `question_id/db_id/target_id` 映射；相同输入重复导出 Schema 的 hash 一致。
  - 至少两个 `db_id` 的表/字段/外键与数据库元数据或正式数据包描述逐项核对；交替及并发访问不混用 Schema/连接。
  - 业务库与公开目标隔离验证通过：公开查询不出现管理表，认证/会话/trace 仍写入管理库；未知或越权目标 ID 被拒绝。
  - 缓存 key、Schema 快照与模型输入不包含参考 SQL、标准答案或人工审核材料。
  - 公开目标写入尝试被拒绝；默认业务权限和脱敏配置未因 benchmark 配置而全局失效。
- **交接给下游的产物**：
  - `EvaluationTarget`、目标注册表、BIRD Provider 及数据源/缓存隔离测试。
  - `eval/datasets/bird/manifest.json`、`subset-20.jsonl`、Schema 快照、数据安装/校验脚本及引擎版本记录。
  - `eval/config/benchmark-bird.yaml` 与专用提示词、evidence 和策略差异声明。
  - `docs/evaluation/S2_HANDOFF.md`，交接 20 题、目标布局、只读连接契约与未确认标注。

### S3：最终 SQL 提交与执行验证

#### 【🎓 学习目标与微理论】

学习目标：能指出哪条 SQL 才是正式预测，并理解为什么验证器必须独立于被测链路。每次只讲一个序号。

1. **什么是探针 SQL？为什么绝不能取最后一条 SQL？** 代码锚点：`ExecuteSqlTool`、拟新增 `EvalSubmission` 与 SQL 阶段事件。讲解重点：探针用于探索字段/样例，不一定回答问题；必须显式提交最终 SQL，不能按调用位置挑选。反例：算完收入后又查询一行样例，拿最后的探针计算 EX。
2. **什么是独立执行验证器？** 代码锚点：`SqlResult`、`ReadOnlyQueryRunner`、拟新增验证器。讲解重点：在冻结快照上独立执行预测和 gold，用固定比较协议判分，避免预览/被测改写器充当 oracle。反例：把 `ExecuteSqlTool` 的前 20 行 Markdown 或被测权限重写结果同时当预测和标准答案。

- **目标**：将多步工具调用转为可追溯的最终预测，并用独立执行验证器完成公开 EX 与业务结果的明确区分。
- **涉及模块/文件**：`ExecuteSqlTool`、`ValidateSqlTool`、`SqlSafetyGuard`、`DataScopeRewriter`、`ExplainPrecheckService`、`ReadOnlyQueryRunner`、`SqlResult`、`DodoAgent`；新增 `EvalSubmission`、SQL 阶段事件、结果验证器和比较器契约测试。
- **前置依赖**：S2 完成，使用 S2 冻结的 20 题、目标映射、Schema、公开数据版本与比较器 revision。
- **具体任务拆解**：
  1. **Step 1**：定义最终提交契约，至少有 `case_id/final_sql/source_tool_call_id/sql_stage/submission_status`；Agent 最终 SQL 必须对应实际候选/调用证据。基线没有工具调用时显式标注 `source=baseline`，不能伪造调用 ID。
  2. **Step 2**：采集候选、安全、权限改写及实际执行 SQL 的阶段快照，记录权限规则、EXPLAIN 判定、策略失败和脱敏命中；业务失败不只保留难以解析的自然语言错误。
  3. **Step 3**：明确探针与答案查询的选择规则。多次修复、多条聚合、最终一次调用为探针等情况必须有显式提交，不使用“最后一个 SQL 代码块/最后一次 executeSql”启发式兜底。
  4. **Step 4**：新增独立验证器，在同一冻结数据快照上执行最终预测与 gold；被测 Agent 不接触 gold。公开 EX 使用声明的最终候选 SQL，实际执行链路另外记录 Guard/权限/行数限制的影响，不能悄悄替换 SQL 阶段。
  5. **Step 5**：固定所选官方方言比较脚本及其行为；原样计算官方 EX，再提供独立业务比较规则。核实集合/多重集合、排序、列位置、NULL、数值精度及日期规则，禁止将自定义比较分数冠以官方 EX。
  6. **Step 6**：脱离模型文本预览采集结构化结果；明确 `LIMIT` 注入、JDBC 截断、展示上限三种差异。不能以 `truncated=false` 推断 Guard 前查询已返回全量。
  7. **Step 7**：gold 自执行检查，分类记录参考 SQL 无法执行、目标不匹配、问题歧义和疑似标注错误；保留原始文件，争议提交人工裁决。两边偶然同为空集不能单独证明语义正确。
  8. **Step 8**：为验证器配置真实可终止的超时/取消；验证器失败、预测缺失、超时、策略阻断分别记录，不静默剔除难题。
- **验收标准**：
  - 20/20 题均产生合法最终预测或明确无预测终态；每个 Agent 提交可追溯到 SQL 阶段和工具证据。
  - 至少覆盖探针后最终提交、失败后修复、最终调用为探针、缺少显式提交四类选择测试；不能错误使用其他调用的 SQL。
  - 20/20 gold 均完成执行健康检查；异常均进入审核清单。异常未裁决时不出具“可信完整分数”。
  - 比较器至少覆盖重复行、行顺序、NULL、空集、列顺序、decimal/float、日期、超时八类固定用例；官方与业务语义差异可从报告识别。
  - 对超过 20 行及超过 200 行的合成结果验证评分不受前端预览影响；注入 LIMIT 导致结果改变时可见且不修改 gold。
- **交接给下游的产物**：
  - `eval/schemas/eval-submission.schema.json`、`sql-event.schema.json`、`verification.schema.json`。
  - 最终预测提交适配、SQL 阶段采集、独立执行器、比较器及其测试；锁定官方脚本来源/revision/hash。
  - `eval/scripts/verify-sql.ps1`、20 题 `predictions.jsonl/verification.jsonl` 和 gold 健康/争议记录。
  - `docs/evaluation/S3_HANDOFF.md`，说明预测阶段、比较语义、资源限制及错误分类。

### S4：完整批跑与 Baseline 对比

#### 【🎓 学习目标与微理论】

学习目标：能复核两组实验是否真正可比，并解释重跑与续跑的计分差别。每次只讲一个序号。

1. **什么是可复现的评测？** 代码锚点：`DodoAgent` 的模型/Skill 配置、S1 manifest 与拟新增检查点。讲解重点：冻结数据、输入和计分协议，保留每次尝试；复现不承诺随机模型逐字一致。反例：重跑时数据、提示词、会话记忆都变了，却把分数差归为代码提升。
2. **什么是 Baseline（基线）？怎么保证对比公平？** 代码锚点：`DodoAgent.buildChatModel`、拟新增直接 NL2SQL Runner、S3 验证器。讲解重点：同题、同数据/Schema/evidence 和比较器，披露方法与预算差异。反例：Agent 用新 Schema 和多次修复，基线用旧 Schema，只报谁分高而不披露差异。

- **目标**：在冻结且公平声明的实验条件下，批量运行 Agent 与直接 NL2SQL 基线，形成逐题可配对、可续跑的公开评测结果。
- **涉及模块/文件**：`DodoAgent.buildChatModel/buildReactAgent`、S1 Runner、S2 目标/Schema、S3 提交与比较器；新增可复用模型构建入口、Baseline Runner、批跑调度、检查点和基础对比报告。
- **前置依赖**：S3 完成；模型和目标服务可用；实验输入、并发与总预算已明确。正式统一报告尚在 S7，本会话先交付可核对的基础结果与差异表。
- **具体任务拆解**：
  1. **Step 1**：抽取可复用模型创建/配置入口，构建直接 NL2SQL 基线；基线输出走相同 `EvalSubmission` 和验证器，不能绕过数据版本和比较协议。
  2. **Step 2**：固定模型 ID、参数、数据、Schema 信息、evidence 开关和工具定义。Agent 与基线的提示词、工具交互、token/调用预算差异逐项披露；不能声称两种方法的实际调用成本天然相等。
  3. **Step 3**：每题独立身份/会话，禁止跨题记忆污染；业务口径不混入 BIRD，gold 不进入模型上下文或可读目录。
  4. **Step 4**：实现逐题检查点、断点续跑与幂等计分。基础设施重试和模型修复尝试全部保留，预先规定采用哪次有效尝试，禁止事后选择最高分尝试。
  5. **Step 5**：以 20 题冒烟、100 题扩大、锁定完整集三阶段推进。完整集样本数从 manifest 读取；完成每阶段后检查耗时、缺失、失败分布和预算再进入下一阶段。
  6. **Step 6**：输出 EX、实际执行状态、轮数/调用数、重试、可得 token、延迟分布和预算消耗。缺失 token/价格不可当成零，记录测量来源与覆盖率。
  7. **Step 7**：配对比较同一题的两组结果，列出双对、双错、Agent 独对、Baseline 独对；固定统计 seed 生成配对差异置信区间，保存可复算输入。
  8. **Step 8**：外部公开 baseline 若缺少同版本逐题预测，只作背景参考；不以历史汇总分归因本次代码改造提升。
- **验收标准**：
  - 两组均完成 20、100 和锁定完整集的运行/明确终态记录；完整集内每题都有记录，不能只导出成功预测。
  - 每阶段满足 `manifest 样本数 = 各终态计数之和`；重复读取检查点或重复评分不改变已选尝试和样本数。
  - 在小集故意中断后续跑，已完成样本不重复计分，未完成样本可恢复；身份/会话不跨题共享。
  - 对比表逐题 ID 全部对齐，配置差异有记录，置信区间可用固定输入重复计算；20 题结果仅作为工程验收，不作总体提升结论。
  - 模型/数据库不可用时输出明确基础设施失败和未完成阶段，不将部分批跑标为完整评测。
- **交接给下游的产物**：
  - Baseline Runner、共享模型工厂、续跑与幂等测试。
  - `eval/config/baseline.yaml`、冻结的 Agent/基线实验配置、子集清单和预算记录。
  - `eval/scripts/resume-eval.ps1`、`compare-runs.ps1`、两组完整运行包、逐题差异和统计 JSON。
  - `docs/evaluation/S4_HANDOFF.md`，交接可比性声明、基础报告、差异题与基础设施失败。

### S5：业务数据集与规则评分

#### 【🎓 学习目标与微理论】

学习目标：能区分独立用例、身份变体、可信 gold 与业务硬约束。每次只讲一个序号。

1. **如何构建 Golden Dataset（黄金数据集）？** 代码锚点：`DataScopeResolver`、`SensitiveFilter`、`CalculateTool`、文件链路与拟新增业务用例 Schema。讲解重点：从独立夹具和人工核验建立问题、预期、规则与证据；40 个独立问题不等于 40 个身份变体。反例：一个问题复制 40 个账号就声称有 40 个独立问题，或用被测改写器生成自己的 gold。
2. **为什么权限、脱敏必须用硬编码规则判定，不能用大模型判定？** 代码锚点：`DataScopeRewriter`、`SensitiveFilter` 与拟新增规则评分器。讲解重点：“硬编码”指确定性、可版本化的程序断言，可配置策略，不是写死用户 ID；业务政策由人确认，LLM 不能覆盖安全硬门禁。反例：回答写得流畅，Judge 给高分，就忽略出现未授权行或明文敏感字段。

- **目标**：构建首批 40 个独立业务用例及身份变体，用独立 oracle 验证权限、脱敏、计算、文件和外部工具契约。
- **涉及模块/文件**：`sql/init.sql`、业务 YAML、`DataScopeResolver`、`DataScopeRewriter`、`service/permission/*`、`SensitiveFilter`、`UserContextBuilder`、`CalculateTool`、`FileManageService`、`AnalyzeFileTool`、`EmbeddingService`、`MultimodalService`、`SkillManager`、`AgentxMcpConfig`、`ChartMcpToolProvider`；新增业务用例 Schema、数据夹具与规则注册表。
- **前置依赖**：S1 完成；业务负责人确认受保护表、数据范围、敏感字段、指标口径、文件授权与工具使用要求。工程依赖不要求 S2–S4，但教学时仍须由用户明确指定切换到 S5，不在等待其他步骤确认时并行推进；不依赖 BIRD 分数生成业务 gold。
- **具体任务拆解**：
  1. **Step 1**：定义用例字段：`case_id/question/fixtures/identity_variants/expected_outcome/oracle/required_evidence/forbidden_behaviors/comparison_rules/label_status/reviewer`；身份变体不能重复计作独立问题。
  2. **Step 2**：建立首批 40 个独立问题，建议分配为：SQL 结果 8、数据权限 10、脱敏 6、指标/计算 6、文件/RAG/图片 6、Skills/Tavily/图表契约 4。调整分配须记录覆盖理由，独立问题总数不能低于 40。
  3. **Step 3**：数据权限覆盖 ALL/DEPT_AND_SUB/DEPT/SELF、多角色最大范围、多部门并集、无部门/部门树异常及 JOIN/LEFT JOIN/CTE/子查询；对未受规则管理的表明确业务期望，不能假定已受保护。
  4. **Step 4**：脱敏覆盖原列、别名、派生表达式、驱动元数据缺失、用户上下文注入和导出证据；断言敏感值不出现在模型输入、工具返回、报告或可查 trace 中。
  5. **Step 5**：计算用例覆盖 SQL 聚合后标量、占比/增长率、零分母、空值、精度和错误参数。逐题标注 calculate 是必须、可选或不适用，不能将未调用工具直接等同于结果错误。
  6. **Step 6**：文件用例区分小文件直读、大文件真实检索、图片首次识别/缓存、多文件指代和跨用户拒绝。记录 file/chunk ID、召回证据、缓存状态；真实 PgVector 未启用时标记集成验证阻塞，不能以文本回退冒充 RAG 通过。
  7. **Step 7**：Skills 开关、Tavily online 开关及图表失败降级使用确定性服务夹具验证；真实服务烟测另列服务版本、时间与结果，不能把假服务契约测试当作实时搜索质量或真实绘图质量证明。
  8. **Step 8**：gold 由固定夹具独立查询/计算并经人工核验，禁止复用被测改写器作为唯一 oracle。标注状态分为 `trusted/disputed/invalid`，保留原始版本、裁决理由和差异。
  9. **Step 9**：实现规则评分，输出证据 ID、命中规则、实际/预期、严重程度和硬性失败；语义结果正确与流程契约分别记录，不将路径差异自动归成能力失败。
- **验收标准**：
  - 至少 40 个独立 `case_id`，均有可重建夹具、gold 来源、明确预期和审核记录；身份变体数量单独报告。
  - 四种 scope、多角色、多部门、LEFT JOIN、脱敏别名/派生列、calculate 契约、文件跨用户拒绝和外部服务降级均有明确用例映射。
  - 规则评分器覆盖确定的正例与反例；故意注入越权/泄露时必须产生硬性失败，不能被其他得分抵消。
  - 所有必需用例可执行并有结果；RAG 等真实集成前提未满足时保留具体阻塞，不能把该覆盖声明为完成。
  - 抽查任意 5 个 gold 可从夹具独立复算；所有争议标注均有人工复核入口，未经裁决不进入可信主榜。
- **交接给下游的产物**：
  - `eval/schemas/business-case.schema.json`、`score-card.schema.json` 和规则配置。
  - `eval/datasets/business/cases.jsonl`、可重建合成数据/文件/角色夹具、受保护 oracle、标注及裁决记录。
  - 业务规则评分器、夹具/规则测试、覆盖矩阵和实际运行包。
  - `docs/evaluation/S5_HANDOFF.md`，交接 40 题、身份变体、硬失败政策、集成能力边界和人工签认。

### S6：LLM-as-Judge 校准

#### 【🎓 学习目标与微理论】

学习目标：能说明 Judge 的适用边界，并看懂人工校准与留出验证。每次只讲一个序号。

1. **什么是 LLM-as-Judge？它有哪些偏见（位置偏见、长度偏见）？** 代码锚点：`SystemPrompt`/Skill 报告规范、S5 oracle、拟新增 Judge rubric/Schema。讲解重点：模型按证据和 rubric 评回答，可能偏好靠前或更长的答案，不能兼任权限裁决。反例：同一对答案交换顺序后优胜者变化，或错误长答案胜过准确短答案。
2. **为什么必须先人工校准裁判？** 代码锚点：拟新增 `calibrate-judge.ps1`、人工参考与校准/留出分区。讲解重点：先用双人标注检验量具，再冻结用于正式评 Agent。反例：Judge 自称高置信度就上线，换模型后继续沿用原分数，未发现严重事实错误漏判。

- **目标**：先校准回答评分工具，再将有明确适用边界和版本的 Judge 用于正式回答质量评估。
- **涉及模块/文件**：`SystemPrompt`、数据分析 Skill 的报告规范、S5 业务数据与 oracle、S1 工具/trace 证据；新增 Judge 调用器、JSON Schema、rubric、人工参考标注、校准与漂移脚本。不得假定仓库已有 Judge 能力。
- **前置依赖**：S5 完成；人工提供参考判定并裁决标注分歧；Judge 模型、证据范围和调用预算明确。
- **具体任务拆解**：
  1. **Step 1**：明确 Judge 仅评价有证据支持的事实、完整性、解释和错误说明；SQL 执行正确、行权限和敏感泄露优先由确定性规则判定，Judge 不得覆盖硬性失败。
  2. **Step 2**：定义结构化判分：`judge_version/rubric_version/model_id/case_id/dimensions/evidence_ids/abstain/brief_reason`；每维 0–4 分，弃权时相应分数为 null；限制未知字段和非法枚举。
  3. **Step 3**：准备至少 20 条校准材料与 20 条互不重叠的留出材料，分别由两名人工独立标注并裁决分歧。材料包含正确/错误/证据不足、长短答案、数值矛盾及形式漂亮但事实错误的反例。
  4. **Step 4**：校准材料可用于修改 rubric；留出材料不得用于反复调参。若据留出结果改规则，必须更新版本并另建留出集。
  5. **Step 5**：隐去被测模型身份，固定 Judge 提示词、模型参数和证据裁剪；至少 5 组对比交换顺序检查位置偏差，检查长度偏差。不得使用 Judge 自报置信度替代人工校准。
  6. **Step 6**：输出维度误差、通过/失败/弃权一致率、严重错误漏判、弃权覆盖和分歧例；从留出集固定 10 条各重复 3 次检查波动，记录调用成本及缺失。
  7. **Step 7**：默认起始门槛：合法 JSON 100%，与人工通过/失败/弃权判定一致率至少 85%，0–4 维度平均绝对误差不高于 0.5，留出集中人工指定严重错误遗漏为 0；10 条重复样本中至少 9 条的通过/失败/弃权判定不翻转。门槛必须由人事先确认，若调整保留理由，不能看分后无记录放宽。
  8. **Step 8**：建立人工标注的漂移哨兵，模型、rubric、证据格式变动时重新校准；漂移告警后暂停正式 Judge 分数发布，规则分和原始运行证据仍可生成。
- **验收标准**：
  - 校准/留出至少各 20 条且无重叠，人工分歧有裁决；材料中的错误答案不是仅由被测模型自评生成。
  - 达到步骤 7 由人确认的全部门槛；不足则状态为 `PENDING_HUMAN` 或继续修订，不标记校准通过。
  - 对证据不足样本明确弃权，越权/泄露硬失败不受回答文风或 Judge 高分影响；所有分数可指向实际证据 ID。
  - 重复性与顺序/长度偏差报告、适用边界和小样本局限完备，人工确认后才冻结正式版本。
- **交接给下游的产物**：
  - `eval/schemas/judge-verdict.schema.json`、`eval/judge/rubric.yaml`、版本化 Judge 提示词与调用配置。
  - 人工参考标注、校准/留出分区、重复和偏差检查输出、校准报告及人工签认。
  - `eval/scripts/calibrate-judge.ps1`、漂移哨兵与验证命令。
  - `docs/evaluation/S6_HANDOFF.md`，说明通过门槛、Judge 版本、有效范围、成本和重新校准触发条件。

### S7：统一评分与报告

#### 【🎓 学习目标与微理论】

学习目标：能从分母、覆盖和分维报告识别被掩盖的失败。每次只讲一个序号。

1. **怎么看评测报告？为什么要看分母和覆盖率？** 代码锚点：S1 `RunRecord`、S4 运行包、S6 弃权、拟新增汇总器。讲解重点：每个百分比同时看纳入样本与缺失/超时/弃权，不能静默删题。反例：12 题中只评成功的 6 题并全对，报告“100%”却隐藏另外 6 题。
2. **为什么绝对不能出一个“综合加权总分”？** 代码锚点：S5 硬失败、S6 Judge 与本文件四维报告契约。讲解重点：本项目顶层四维分别报告，越权/泄露不能被 EX 或回答文风抵消。反例：泄露他人收入，因 SQL 准确和回答漂亮，仍靠平均分被标成整体通过。

- **目标**：将公开结果、真实执行、业务约束和校准后的回答判分合并为可离线复算且逐项可追溯的报告。
- **涉及模块/文件**：S4 两组公开评测运行包、S5 规则评分器、S6 Judge 输出、`SqlResult` 与 trace 索引；新增评分汇总、配对统计、JSON/Markdown/HTML 报告器。
- **前置依赖**：S4、S5、S6 完成；正式 Judge 版本经人工确认。只有用户明确批准独立的模板准备小步，才可提前开发模板；缺少依赖数据或校准结果时不能宣称正式验收通过。
- **具体任务拆解**：
  1. **Step 1**：实现离线评分入口，读取不可变运行包、规则版本与既有 Judge verdict；汇总阶段不能重新调用 Agent 或隐式调用新版本 Judge。
  2. **Step 2**：按第 2 节输出四维报告，保留硬性失败、错误类别、证据链接和 Judge 覆盖率；禁止发布跨四维的综合加权总分。
  3. **Step 3**：明确每项指标分母。对公开套件缺失预测、超时、策略阻断、基础设施失败按预先声明协议统计；原始官方口径和人工审核后口径分列，列出排除数量、题号与理由。
  4. **Step 4**：业务预期拒绝按该题预期评分，同时保留执行拦截状态；Judge 缺失/弃权记为不可评分及覆盖率变化，不默认为零分或满分。
  5. **Step 5**：输出按 db_id、题型、角色、难度、失败组件、缓存/外部服务状态的切片；展示样本数，避免对极小切片下总体结论。
  6. **Step 6**：汇总 p50/p95 延迟、轮数、工具/JDBC 尝试、token 和可核实成本；缺失、测量范围与价格来源/版本显式说明。
  7. **Step 7**：将 S4 配对对比纳入统一报告，保留重采样 seed 与输入；生成可分发的脱敏报告和受权限控制的详细证据索引。
- **验收标准**：
  - 同一运行包、规则和 Judge 版本重复评分，除生成时间外，分数、样本数、配对差异与报告关键数据一致。
  - 每项指标都有 `numerator/denominator/coverage` 或明确不适用说明，所有终态合计与 manifest 一致；失败不会静默消失。
  - 至少用预测缺失、Judge 弃权、预期拒绝、gold 争议、重复尝试、基础设施失败六类固定输入验证分母与状态处理。
  - 每条低分/硬失败可追溯到 case、attempt、规则与证据；越权/泄露在报告显著列出，不能被其他分数抵消。
  - JSON 输出通过 Schema 校验，Markdown/HTML 链接可定位实际证据；共享报告不包含真实敏感值、令牌或 gold 明文泄漏。
- **交接给下游的产物**：
  - `eval/schemas/score-report.schema.json`、`eval/scripts/score-run.ps1`、确定性汇总与分母回归测试。
  - `eval/report-templates/` 中的 Markdown/HTML 模板及实际 `scores.jsonl/summary.json/report.md/report.html`。
  - 逐题失败集、切片和配对统计输出、Judge/规则版本及原始/审核口径说明。
  - `docs/evaluation/S7_HANDOFF.md`，交接报告生成命令、待诊断问题和证据访问方式。

### S8：trace 查询与失败重放

#### 【🎓 学习目标与微理论】

学习目标：能沿证据定位最早可观测偏离，并区分离线重放与在线重跑。每次只讲一个序号。

1. **什么是“最早偏离点”？** 代码锚点：`ExecuteSqlTool` 的 Guard/权限/EXPLAIN/执行/脱敏阶段、`SessionService` 与 span。讲解重点：逐段比较预期与实际，找最早有证据支持的偏离，缺证据则标未确认。反例：回答数字错就归因计算，实际更早的目标库或权限范围已经选错。
2. **如何做失败重放和归因分析？** 代码锚点：`SessionService`、拟新增 `FailureCase` 与 `replay-case.ps1`。讲解重点：离线重评原证据，在线新会话验证假设，保留父运行与人工归因。反例：在线重跑偶然答对就覆盖原失败，或把并发消息存储顺序当执行先后。

- **目标**：通过授权 trace 查询和单题重放，将报告中的失败转化为有证据、可验证的修复任务。
- **涉及模块/文件**：`SessionService`、`SessionController`、`SaTokenConfig`、`DodoAgent`、SQL 各阶段、S1 trace 索引、S7 失败集；复用依赖 `TraceStore`，新增 `TraceQueryService/TraceQueryTool`、查询 API/CLI、`FailureCase` 和重放器。
- **前置依赖**：S7 完成；已具备真实 span、业务阶段事件和可重建输入。实际失败不足 10 个时补充已知故障夹具，明确区分自然失败和注入失败。
- **具体任务拆解**：
  1. **Step 1**：提供按 `run_id/case_id/attempt_id/session_id/tool_call_id` 的参数化查询、分页、结果长度限制与脱敏输出；当前用户只能访问自己的授权记录，管理员访问按明确角色规则处理并留审计。
  2. **Step 2**：`TraceQueryTool` 仅在诊断入口装配，不默认注入被测 Agent；不能接受任意 SQL，不能让被测模型查询 gold 或其他运行答案。
  3. **Step 3**：重建服务端 LLM/TOOL span 与业务事件，展示候选/改写/执行 SQL、策略命中、重试、计算输入输出及最终答案关联；并发执行使用真实时间和调用 ID，不用消息顺序臆造先后。
  4. **Step 4**：定义 `FailureCase`，包含问题、预期、实际、最早偏离点、证据、假设、复现命令、人工归因和后续动作。分类覆盖 Schema/目标、提示词/工具契约、权限、EXPLAIN/JDBC、脱敏、计算、RAG/图片、外部服务、报告和 Judge；无法确定时标记 `unconfirmed`。
  5. **Step 5**：交付两种重放：已采集事件的离线规则/评分重放，以及固定夹具上的在线 Agent 重跑；在线重跑使用新会话并保留父运行引用，不能覆盖原运行或保证模型逐字一致。
  6. **Step 6**：挑选 10 个失败逐一验证假设，优先找最早可观测偏离而非仅看最终低分；对已确认问题实施受控修复并增加回归样本，失败归因由人最终签认。
  7. **Step 7**：生成每题独立证据包和统一诊断表，向 S9 交接可自动检测的重复错误、超时和资源异常条件。
- **验收标准**：
  - 10/10 失败均有预期/实际、最早偏离证据或明确未确认原因、可运行重放命令及人工复核状态；每题至少完成一次离线或受控在线重放。
  - 同一失败的报告、trace、SQL 阶段和重放可通过稳定 ID 关联；缺失证据明确显示，不能编造中间结果。
  - 验证跨用户/跨运行越权查询、参数注入、超大分页/结果以及被测 Agent 获取诊断工具等负例，均不能越过权限与容量限制。
  - 离线重放的规则分一致；在线重跑记录新 ID、模型/数据版本和差异；修复后的回归用例能区分修复前后行为。
- **交接给下游的产物**：
  - `TraceQueryService/TraceQueryTool`、受控查询 API/CLI 与鉴权/容量测试。
  - `eval/schemas/failure-case.schema.json`、`eval/scripts/query-trace.ps1`、`replay-case.ps1`。
  - 10 个失败证据包、人工归因/未确认清单、受控修复和新增回归用例。
  - `docs/evaluation/S8_HANDOFF.md`，交接故障触发条件、重放方式、权限边界和 S9 预算需求。

### S9：预算、诊断与熔断

#### 【🎓 学习目标与微理论】

学习目标：能看懂累计资源预算，并用资源终止证据判断熔断是否有效。每次只讲一个序号。

1. **什么是跨层预算？** 代码锚点：`DodoAgent.maxRounds(100)`、`ReadOnlyQueryRunner` 的超时/重试、MCP 与拟新增 `EvalBudgetPolicy`。讲解重点：Agent、工具、JDBC 和外部调用共享剩余时长与累计次数，不能每层重新计满。反例：只限制 12 轮，却让每个工具及其 JDBC 重试重复获得完整超时，单题仍长时间占资源。
2. **为什么 Agent 容易死循环？熔断机制如何保护系统？** 代码锚点：`DodoAgent.interrupt`、`AgentController`、`ChartMcpToolProvider` 和 SQL 调用链。讲解重点：无进展修复/重复错误需确定性停止条件，并取消活动任务；当前图表冷却不是全链路熔断。反例：页面显示已中断，但后台 SQL 继续运行，或者错误快照被续跑后无限重试。

- **目标**：统一限制 Agent、工具、JDBC 重试和外部服务的累计资源消耗，在故障或预算耗尽时可靠停止并保留诊断证据。
- **涉及模块/文件**：`DodoAgent.interrupt/buildReactAgent`、`AgentController`、共享 `AgentTaskManager`、`ReadOnlyQueryRunner`、`ExplainPrecheckService`、`ChartMcpToolProvider`、MCP/模型客户端配置、S1 Runner 和 S8 诊断；新增 `EvalBudgetPolicy`、预算上下文、熔断状态和故障注入。
- **前置依赖**：S8 完成；人类确认默认成本、停止阈值、恢复方式与误熔断容忍度。复用 S1/S4 的最小限制，不把已有图表冷却误认为全链路熔断。
- **具体任务拆解**：
  1. **Step 1**：定义每次尝试及整个 run 的 wall-time、Agent 轮数、工具次数、SQL 提交、JDBC 实际尝试、连续相同错误、并发、token/成本等预算；默认值写配置并冻结到 manifest。
  2. **Step 2**：试运行配置可从每题 180 秒、12 轮、30 次工具调用开始，由人结合样本与成本确认；SQL 探针、修复、EXPLAIN、JDBC 内部重试均有明确计数口径。所有重试共享上层剩余预算，不能每层重新获得完整额度。
  3. **Step 3**：为重复相同错误、重复 SQL 无进展、数据库超时、MCP 断连建立有证据的熔断条件；区分应立即停止的越权/身份问题、可有限重试的瞬态错误以及可降级的图表失败。
  4. **Step 4**：预算和身份使用经过测试的异步上下文传递；并发计数具备一致性。缺少 token 或价格时成本标记不可用，用已生效的时长/调用数边界保护，不能报告零成本。
  5. **Step 5**：从 Runner/预算触发停止后，阻止新工具和重试，取消正在运行的 JDBC Statement/外部请求/后台任务或使用可验证的等效终止机制。`interrupt()` 返回成功不能替代资源停止证明。
  6. **Step 6**：明确中断快照和熔断终态的区别：自动续跑不能无条件恢复已熔断任务；恢复需有新尝试、父记录、原因及适用预算，不允许回避原失败。
  7. **Step 7**：故障注入覆盖 JDBC 超时/断连、MCP 调用断连、重复 SQL、连续错误、模型超时和并发预算耗尽，保存资源检查和 trace；在线停止依赖确定性事件，不依赖实时 Judge 的不稳定分数。
  8. **Step 8**：生成诊断摘要、熔断原因、恢复说明和回归门禁；同时验证合理的复杂查询与图表降级不会被错误解释成安全违规。
- **验收标准**：
  - 至少上述七类故障各有可重复自动验证，预算触发有准确计数、停止原因、最后调用和终态证据。
  - 触发后新增工具/JDBC 重试次数为 0；使用短超时夹具验证，停止后 5 秒内活动 SQL/受控后台任务降至 0。若目标服务不支持取消，须提供并验收等效终止，不以后台仍运行视为通过。
  - 并发场景不超分配既定调用/重试预算；每次 JDBC 尝试与 Agent 修复均进入累计计数，取消后的任务不会自动恢复。
  - 正常回归集仍按预期完成；任何误熔断均记录并经人确认阈值调整，不能通过抹除异常样本改善报告。
  - 成本数据具备来源/版本/覆盖率，未知值明确保留；run 级预算实际生效，熔断后仍能生成完整脱敏证据包。
- **交接给下游的产物**：
  - `EvalBudgetPolicy`、统一预算/取消/熔断实现及测试、版本化 `eval/config/budgets.yaml`。
  - `eval/scripts/inject-faults.ps1`、七类故障运行包、取消资源检查报告与正常回归报告。
  - 可持续运行的预检查、回归、批跑、评分、诊断和故障门禁命令及运行说明。
  - `docs/evaluation/S9_HANDOFF.md`，交接阈值确认、已知限制、恢复流程及全工程完成清单。

## 2. 评测报告标准（最终目标）

### 2.1 四个独立维度

| 维度 | 必须报告的内容 | 判定依据与限制 |
| --- | --- | --- |
| 公开 SQL 结果 | 数据 revision/引擎/evidence、样本数、最终预测、所锁定官方 EX、逐题对比、原始及人工审核口径 | 使用冻结比较器和同一数据快照；业务改写后的 SQL 与公开提交阶段分别记录；自定义业务分不能标成官方 EX |
| 实际执行结果 | 各终态、调用/轮数/JDBC 尝试、保护性拒绝、EXPLAIN、截断、耗时分布、可得 token/成本、缺失覆盖 | 使用真实执行事件、span 和结构化结果；工具调用正常返回不等于业务成功，20 行预览不等于完整结果 |
| 业务约束 | 角色/部门行权限、文件归属、脱敏、业务口径、逐题 CalculateTool 要求、禁用行为、硬性失败与证据 | 确定性规则与独立 oracle 优先；只有业务明确要求的流程才能计流程分，需区分安全硬约束与建议路径 |
| 回答质量 | 有证据支持的事实、完整性、解释、错误说明、Judge 维度分/弃权/覆盖率、人工分歧及 Judge 版本 | 只使用校准通过的 Judge；证据不足可弃权；正式判分漂移后停发该部分分数并保留其他维度 |

### 2.2 分报与审计原则

- 禁止生成跨四维的“综合加权总分”，禁止用一个加权总分掩盖越权或泄露。各维指标分别报告；任何硬性安全失败必须独立、显著列出，回答质量高分不能抵消。
- 每项分数展示分子、分母、样本数、覆盖率和适用范围。未运行、基础设施失败、缺失预测、争议 gold、Judge 弃权分别列明；不能静默删题。
- 原始官方口径保留；人工修订只产生带版本的审核后口径，并列出题号、证据、裁决人和影响。不能静默修改 gold 或通过改变比较器追求高分。
- BIRD 标注质量是结论边界：固定上游版本和 hash，审核疑似问题，但不能让 Judge 单独裁决 gold。20 题用于验证工程，不能推断总体模型提升。
- execution accuracy 无法证明权限、脱敏、正确工具使用或回答可信；四维之间的差异须能逐题追溯到 SQL 阶段和策略事件。
- Judge 必须“先校准量具再评 Agent”。记录模型、rubric、证据格式、人工参考、误差、重复性、偏差和哨兵结果；升级任一关键部分须按 S6 重新验证。
- 报告必须包含配置差异、外部依赖/缓存状态、基线公平性声明、配对统计与小样本局限。缺少逐题预测的外部汇总分只作背景。
- 所有低分、硬失败和人工裁决均关联 `run/case/variant/attempt/session/tool/span` 中可得 ID，并提供受权限保护的查询入口。

## 3. Agent 协作规范

### 3.1 Codex 负责执行的工作

- 读取本文件及指定 Session 的依赖交接，核对源码、运行环境和已有修改；按真实实现更新待办，不臆造项目能力。首先承担导师职责，按本 Session 的编号逐点讲理论、给项目反例、答疑并等待理解确认。
- 所有理论过关且当前 Step 获批后，在该小步范围内写脚手架、改代码与配置、补必要测试、修编译和运行报错、执行预检查/迁移验证/批跑/续跑、生成报告及证据包；不能提前把整个 Session 做完。
- 实现与验证数据隔离、Schema/工具契约、记录格式、评分脚本、trace 查询、重放、取消与预算。
- 汇总标注冲突、Judge 分歧和失败假设，给出可检查的证据与候选修复；未有证据的结论保留 `unconfirmed`。
- 只完成当前已授权小步及其必要验证。等待理论理解、Step 确认或人工语义裁决时停止后续工程操作；需要人判断时提交具体题目、证据和可选结论。不得以“可独立准备”为由绕过教学停顿。

### 3.2 人类必须负责的判断

- 标注和签认 Gold 可信度，确认问题含义、业务口径、受保护表/字段、文件归属规则与流程约束是否计分。
- 裁决 BIRD/业务争议样本，签认审核后数据版本；确定应拒绝行为与允许降级行为。
- 审核 Judge rubric 和参考分，事先确认校准门槛，判定校准是否通过及漂移后是否继续使用。
- 最终确认失败归因，区分模型、提示词、工具、数据、权限策略、基础设施和评分工具问题。
- 评估并确认成本阈值、熔断/恢复策略、误熔断容忍度和实验结果是否足以支持业务结论。
- 人工结论必须留下记录，不能仅存在聊天中；AI 推测或自报置信度不能替代人工签认。

### 3.3 每次新会话的执行与交接规则

1. 用户指定 `S<n>` 后，读取本文件、该 Session 依赖的 `docs/evaluation/S*_HANDOFF.md` 和对应实际产物，检查完成状态、当前差异与教学检查点。缺少依赖时明确缺口，给出可选准备小步并等待确认，不自行实施、不伪造通过。
2. 按该 Session 的【🎓 学习目标与微理论】和六个工程字段推进。未确认的理论一次只讲一个；全部确认后才进入实操，一次只做获批的一个 Step/子步。本文列出的新增类/路径是交付目标；如需等价实现，记录原因、实际路径和接口兼容关系，并同步更新本文，避免出现第二份冲突蓝图。
3. 保留用户已有修改，不覆盖不相关改动；运行使用隔离夹具。禁止批量删除文件或目录，禁止 `del /s`、`rd /s`、`rmdir /s`、`Remove-Item -Recurse`、`rm -rf`。只能一次删除一个明确路径文件；如确需批量清理，停止该清理并请用户手动处理，不通过其他命令或脚本规避。
4. 为本次变更运行必要且有意义的检查。报告区分本次实际运行、历史报告和静态核对；服务不可用、数据缺失或人工未签认时记录真实状态，不生成虚假分数。
5. 每个交接文件必须包含：Session/状态、3.4 教学检查点、实际代码/配置/数据版本、改动文件、输入与输出产物、启动/验收命令及结果、失败/未确认项、人工裁决、下游接口和下一步。用户理解确认与工程验收分别记录，不能相互代替。运行包/校准材料属于证据，不可取代本文件作为需求源。
6. 验收完成后更新 0.5 状态表和完成证据路径；剩余人工判断用 `PENDING_HUMAN`，外部前提缺失用 `BLOCKED` 并写原因。文档已写、测试已写或部分样本成功均不足以单独标记 `DONE`。
7. 会话结束时向用户说明交付物、实际验证结果和剩余事项，附本文件与本次交接文件的可访问路径。不得仅回复“理解了”或“计划已完成”。

### 3.4 教学检查点与恢复格式

每次暂停时更新当前 Session 的交接文件；以下是待创建记录的格式，不是已完成事实。所有 Session 在工程验收之外，还须有逐理论理解确认与逐 Step 批准/结果记录。

```yaml
session: S1
engineering_status: TODO
learning_phase: theory # theory / practice / acceptance
confirmed_theory_points: []
current_theory_point: 1
current_step: null
confirmed_steps: []
pending_confirmation: null
approved_scope: null
last_user_confirmation: null # 只记录真实回复的时间/原意/对应编号，不臆造确认
actual_validation: []
next_action: "只讲理论 1，等待理解确认"
```

`confirmed_steps` 应记录具体小步编号（如 `Step 1.1`），不能仅写“已同意 S1”。理论答疑尚未结束时保持当前编号；步骤测试失败时保留真实结果，在当前获批范围修复，需扩大范围则先给方案并等待确认。新会话从 `next_action` 继续；不把旧会话里的“继续”用于授权新范围。
