# 站点④：Tika 统一解析 —— 一份文本，两种长度

> 课程：第 2 课 · 文件与联网搜索的重构
> 链路位置：上传链第 4 站（MinIO 落盘之后、切分向量化之前）
> 核心文件：`FileParserService.java`（73 行）

## 1. 一句话结论

`parse()` 只做一件事——把任意格式变成纯文本；**真正的设计在返回类型 `ParseResult`：一次解析产出两个长度版本，全量给索引，限长给模型。**

## 2. 要解决的问题

模型只认文字。但"文字"在链路里有两个用途，体量差一个数量级：

| 用途 | 需要多少 | 原因 |
|---|---|---|
| 做索引（切分 → 向量化 → 检索） | 全量，一字不能少 | 少一段就永久检索不到 |
| 当原文（塞给模型读） | 一小截 | 上下文窗口是成本，也是上限 |

而 Tika 解析一个大 PDF 要几秒 CPU——**不能为两个版本解析两遍**。这就是 `ParseResult` 存在的理由。

## 3. 涉及的文件与类

| 文件 | 角色 |
|---|---|
| `FileParserService.java`（73 行） | 本节主角：Tika 封装 + 截断 + 双产出 |
| `FileManageService.java:221-242` | 消费者：拆两份、分别落库/切分 |
| `FileManageService.java:45-52` | 格式白名单 `TEXT_EXTS` / `IMAGE_EXTS` |
| `AnalyzeFileTool.java:105-128` | 终点：限长文本怎么进模型上下文 |
| `pom.xml:26-27,153-163` | 依赖：tika-core + tika-parsers-standard-package 2.9.2 |

## 4. 精简链路

```text
uploadFile → MinIO 落盘 → isTextFile(后缀)? → FileParserService.parse
                                                      ↓
                                          ParseResult(fullText, truncated)
                                            ↓                      ↓
                             isLargeFile(fullText)          extracted_text 列
                                  ↓        ↓                      ↓
                          ≥5000 切分    不足5000              analyzeFile 直读
                                  ↓                               ↓
                          vector_file_info                 模型上下文
```

## 5. 关键代码走读

### 5.1 类头与依赖（`:13-25`）

```java
@Slf4j
@Service
public class FileParserService {
    @Value("${file.text-max-length:20000}")
    private int maxTextLength;

    private final Tika tika = new Tika();
```

- `new Tika()` 是**字段初始化**：Tika 无状态、线程安全，单例复用；放进方法里每次都要重建 `AutoDetectParser` + 加载魔数表。
- **不注入 `TikaConfig`**：接受默认。代价是 `tika-parsers-standard-package` 会拖进 POI、PDFBox 等一堆传递依赖。
- 阈值走 `@Value`：**只影响直读路径**，不影响切分器（500/50 硬编码在 `FileManageService:230`）。

### 5.2 parse 主体（`:31-51`）

```java
try (InputStream is = file.getInputStream()) {
    String fullText = tika.parseToString(is);
    if (fullText == null) { fullText = ""; }
    fullText = fullText.trim();

    String truncated = truncate(fullText);
    log.info("... 全量长度={} | 截断长度={}", fullText.length(), truncated.length());
    return new ParseResult(fullText, truncated);
} catch (Exception e) {
    throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
}
```

- **传流不传 File**：`parseToString` 内部包成 `TikaInputStream`，需要回退时 spool 到临时文件（探测魔数要 reset、zip 容器要跳转，纯流做不到）。代价是**一次额外磁盘拷贝**。
- **后缀不参与类型判断**：Tika 只看内容。改名成 `.txt` 的 docx 照样正确解析；后缀只决定"要不要进这道门"。
- **长度上限的谣言**：Tika 1.x 默认 10 万字符，**2.9 的 `writeLimit` 默认 -1，不限制**。真正卡长度的是 `truncate`。
- `null` 兜底 + `trim`：空文档返回 null；尾部空白会在切分器里产生空块。

### 5.3 truncate（`:53-59`）

```java
if (text.length() <= maxTextLength) { return text; }
return text.substring(0, maxTextLength)
     + "\n\n... (内容已截断，文件过长；细节可通过 RAG 检索)";
```

- 末尾那句中文**是写给 LLM 的指令**，不是注释——它随正文返回，告诉模型"你看到的是残卷，去调 RAG"。不加这句，模型会拿 2 万字硬答并声称"文档未提及"。
- 三个短板：`<=` 边界（正好 20000 不截）、**硬切不回退句号**、**按 char 不按 token**（2 万中文 ≈ 1.2~2 万 token，2 万英文 ≈ 5000 token，差 4 倍）。

### 5.4 ParseResult（`:61-72`）

```java
@Data
@AllArgsConstructor
public static class ParseResult {
    private String fullText;       // 全量，只喂向量化
    private String truncatedText;  // 限长，只喂直读
}
```

可选 `record`（Java 21 支持，不可变 + 自带 equals），项目用了 `@Data`——风格惯性，无对错。命名小瑕疵：小于 20000 时 `truncatedText` 其实没被截断过。

### 5.5 消费者怎么用（`FileManageService:221-231`）

```java
var parseResult = fileParserService.parse(file);   // :222 唯一一次解析
String fullText  = parseResult.getFullText();      // :223
String truncated = parseResult.getTruncatedText(); // :224

entity.setExtractedText(truncated);                // :226 ← 只有这里用 truncated
if (isLargeFile(fullText) && ...) {                // :228 ← 用 fullText 判断
    ... new Document(fullText) ...                 // :231 ← 用 fullText 切分
```

**三行代码两种票，代码里没有一行注释说明为什么，但用错任何一处都是事故：**

| 误写成 | 后果 |
|---|---|
| `isLargeFile(truncated)` | 判据从"真实长度"变成"封顶长度"，阈值一调就全线失效 |
| `new Document(truncated)` | **最严重**：20001 字之后永久检索不到；那句"可 RAG 检索"被切块入索引，成为永久噪声 |

正确读法：**truncated 是给眼睛看的，fullText 是给耳朵听的。**

## 6. 三个设计决策

| # | 问题 | 方案 | 代价 |
|---|---|---|---|
| 1 | 几十种格式，各库 API 不同 | 一个 `parseToString` 全兜，白名单管准入 | 结构全丢：Excel 行列、PPT 页码、表格单元格全部拍平 |
| 2 | 索引要全文，模型要节选 | 一次解析返回两份 | 内存同时驻留两份；**fullText 无上限保护**，100MB 文本文件有堆压力 |
| 3 | 模型不知道看到的是残卷 | 把"残卷"写进正文 | 提示语是"假内容"，一旦二次索引/导出就成噪声 |

## 7. 框架边界

- **Tika（Apache）**：魔数探测、按 MIME 选解析器、编码探测、SAX → String、写入长度控制。
- **项目代码**：白名单、5000 阈值、20000 截断 + 提示语、双产出结构、truncated/fullText 的分流。
- **Spring**：`@Value` 外部化、`@Service` 单例、`@Transactional` 回滚语义。

一句话：**Tika 负责"能读"，项目负责"读多少、给谁读"。**

## 8. 与已学知识的对应

| 已学 | 本节的增量 |
|---|---|
| RAG 的"切分 → 向量化 → topK" | 切分前的**文本来源**必须无损；metadata 过滤依赖 `fileid` |
| MCP / Function Calling | 这里没有工具调用，纯数据准备；但产物的元数据（`embed` 标志）决定了 `analyzeFile` 走哪条路 |
| Spring 事务 | `@Transactional` 的语义在这里**反噬**了 FAILED 状态的意图（见第 10 段） |

## 9. 实践思考题

`maxTextLength` 按字符算。若产品要求改成"按 token 给，上限 8000 token"，你改哪几处？

提示：解析发生在上传那一刻，而**那时系统还不知道这份文件会被哪个模型读**（模型是请求级选的）。这会把"截断"从"解析问题"变成"上下文装配问题"——更合理的位置或许是"存全文，在 `analyzeFile` 返回时按当前模型窗口预算截"。那 `extracted_text` 该存多少？

## 10. 断点问题与三个待验证疑点

**(A) 断点**：在 `FileParserService:44` 的日志行打断点，观察 `fullText.length()` 与 `truncated.length()`。

**(B) 疑点 1 —— 编码猜测**：两个内容相同的 1000 汉字 txt，分别存 UTF-8 和 GBK。上传后对比日志长度。`CharsetDetector` 给的是"最可能"而非确定，**短中文文本猜错率不低**，这是线上最常见的乱码来源。

**(C) 疑点 2 —— 扫描版 PDF 的空文本陷阱（疑似 bug）**：无文字层的扫描 PDF → `fullText=""` → 不向量化 → 状态仍 `SUCCESS` → `AnalyzeFileTool:122` 走 `content != null` 分支 `append("")` → 模型收到空内容。

注意 `FileManageService.getFileContent:263` **有**兜底（`isBlank → "该文件没有可识别的内容"`），但直读路径没走这个方法，直接取字段。一两行可修，用户体感极差。

**(D) 疑点 3 —— FAILED 记录其实会被回滚**：`:170` 挂着 `@Transactional(rollbackFor = Exception.class)`，`:217` 又抛 `RuntimeException`。整事务回滚会连 `:216` 写的 FAILED 一起撤销 → **DB 里查不到失败记录，MinIO 里留下孤儿对象**。

验证：截断一个 docx 改名 `xxx.txt` 上传，然后

```sql
SELECT file_id, status, created_at FROM agentx_file ORDER BY created_at DESC LIMIT 5;
```

## 11. 下节预告

返回整课：站点⑤ 5000 字符分界——`isLargeFile(fullText) && embeddingService.isAvailable()` 这个"且"是**能力 × 意愿**，`@Autowired(required=false)` 的软装配如何让 PgVector 缺席时静默降级。
