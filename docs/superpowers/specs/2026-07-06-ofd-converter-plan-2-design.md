# OFD 转换工具 - Plan 2 设计文档（OFD -> DOCX / Markdown）

> 创建日期：2026-07-06
> 状态：设计阶段
> 依赖：Plan 1（后端 MVP）已完成，Converter 接口/ConvertPipeline/ofdrw-reader API 可复用

## 1. 范围与组件

### 范围

- **OFD -> DOCX**：流式重建 + 视觉逼近，可编辑，复杂版面有损
- **OFD -> Markdown**：标题 + 表格 + 列表 + 图片占位，供 AI Agent 消费
- DOCX -> OFD 移出 Plan 2（完整排版引擎工作量极大，后续 plan 处理）
- 有损提示：API 响应带 `warning` 字段（前端弹窗部分留 Plan 3）

### 设计依据

基于 Plan 1 PoC 发现（`docs/superpowers/pocs/2026-07-05-ofd-converter-poc-findings.md`）：
- OFD->DOCX：ofdrw-reader 文本提取可行，`TextObject.getSize()` 暴露字号
- OFD->Markdown：采用 Path A（reader + 字号推断标题），Path B（HTMLExporter->flexmark）不可用（SVG-based）

### 新增组件

```
backend/src/main/java/com/ofd/converter/engine/
├── extract/
│   ├── OfdTextBlockExtractor.java   # 共享：提取文字图元(字号/坐标/页)
│   └── TextBlock.java               # 中间结构：文字块
├── structure/
│   ├── OfdStructureInferrer.java    # DOCX 用：标题/段落/表格/列表推断
│   ├── MdStructureInferrer.java     # MD 用：结构推断（表达力不同，各做）
│   ├── StructureType.java           # 枚举：HEADING/PARAGRAPH/TABLE/LIST/IMAGE_PLACEHOLDER
│   └── StructureElement.java        # 推断后的结构元素
└── converters/
    ├── Ofd2Docx.java                # OFD->DOCX：结构 -> POI XWPFDocument
    └── Ofd2Markdown.java            # OFD->MD：结构 -> Markdown 文本
```

### 复用 Plan 1

- `Converter` 接口（`source()`/`target()`/`convert()`）-- Ofd2Docx/Ofd2Markdown 实现它，`ConvertPipeline` 自动装配
- `ConvertFormat` 枚举已有 `DOCX`/`MD`
- `ConvertResult`（输出文件 + 类型）
- `ConvertService` 异步执行 + 超时 + 日志（无需改动）

## 2. 文字块提取层（共享）

`OfdTextBlockExtractor` 是 DOCX 和 Markdown 共享的基础层，只负责"从 OFD 提取带几何信息的文字块"，不做任何结构推断。

### TextBlock 中间结构

```java
public record TextBlock(
    int pageIndex,        // 所在页（0-based）
    double x,             // 文字块左上角 X（毫米，OFD 原生单位）
    double y,             // 文字块左上角 Y
    double width,         // 文字块宽度
    double height,        // 文字块高度
    Double fontSize,      // 字号（毫米，可空--部分文档未设）
    String fontRefId,     // 字体引用 ID（可空）
    String text           // 文字内容（拼接该 TextObject 所有 TextCode）
) {}
```

### 提取流程

1. `OFDReader.getPageList()` -> 遍历每页
2. `Page.getContent().getLayers()` -> 遍历图层（含模板层）
3. `CT_Layer.getPageBlocks()` -> 过滤 `TextObject`
4. 对每个 `TextObject`：取 `getBoundary()`（坐标/尺寸）+ `getSize()`（字号）+ `getFont()` + 拼接 `getTextCodes()` 的 `getContent()`
5. 返回 `List<TextBlock>`，按页排序

### 关键决策

- **坐标单位**：OFD 原生用毫米，保留毫米（结构推断按几何对齐，单位无关）
- **模板层**：OFD 模板页（如页眉页脚）的文字也提取，标记来源页（Plan 2 先一并提取，后续优化）
- **空文字块过滤**：`getTextCodes()` 内容为空的 TextObject 跳过

### 边界情况

- 字号缺失（`getSize()` 返回 null）：标记为 null，推断时归入正文基线
- TextObject 嵌套在 `CompositeObject`（复合块）内：Plan 2 先不递归复合块，记为已知限制

## 3. 结构推断层（各自实现）

DOCX 和 Markdown 的结构推断各自实现，共享 `TextBlock` 输入但推断策略不同。

### 共享概念

- `StructureType` 枚举：`HEADING` / `PARAGRAPH` / `TABLE` / `LIST` / `IMAGE_PLACEHOLDER`
- `StructureElement`：推断后的元素，含类型 + 文本 + 级别（标题用）+ 子元素（表格用）

### 通用推断算法（两转换器共用思路，实现独立）

**1. 标题推断（动态阈值）**
- 统计所有 TextBlock 的字号频次
- 频次最高的字号 = `bodyFontSize`（正文基线）
- 字号 > `bodyFontSize * 1.2` 的判为标题
- 标题分级：按字号降序映射 H1/H2/H3（最多 3 级，超出归 H3）
- 字号缺失的块归正文

**2. 段落推断**
- 非标题的连续 TextBlock，按 Y 坐标间距分段
- Y 间距 > 行高 1.5 倍 -> 新段落
- 同行（Y 接近）的多个块拼接为一行

**3. 表格推断（几何网格对齐）**
- 检测 X 坐标聚类（多行的文字块 X 对齐成列）
- 若出现 ≥2 行 × ≥2 列的网格对齐 -> 表格
- 列边界由 X 聚类中心确定
- **降级**：网格检测不确定时，降级为段落（不强行造表）

**4. 列表推断（行首模式）**
- 检测行首模式：`1.`/`2.`、`①`/`②`、`-`/`*`、`（1）` 等
- 连续匹配 -> 列表项
- **降级**：单次匹配不算列表，需连续 ≥2 项

### DOCX vs Markdown 的差异

| 维度 | Ofd2Docx | Ofd2Markdown |
|------|----------|--------------|
| 标题 | `XWPFParagraph` + `Heading1/2/3` 样式 | `#`/`##`/`###` |
| 表格 | `XWPFTable`（真实 Word 表格，可编辑） | Markdown 表格语法（`\|...\|`） |
| 列表 | `XWPFNumbering`（编号列表） | `-`/`1.` 列表语法 |
| 图片 | `IMAGE_PLACEHOLDER` -> `[图片]` 文本 | `[图片]` 占位（不提取二进制） |
| 字号/样式 | 可保留原始字号（视觉逼近） | 仅语义结构（Markdown 无字号） |

### 降级策略

- 任何推断不确定 -> 降级为段落（纯文本），保证输出可用
- 整个文档结构推断失败 -> 全部按段落输出（等价于 PoC 的纯文本提取）

## 4. 转换器实现

### Ofd2Docx（OFD -> DOCX）

```java
@Component
public class Ofd2Docx implements Converter {
    source() = OFD; target() = DOCX;
    convert(source, outputDir, sourceFilename, opts):
        1. extractor.extract(source) -> List<TextBlock>
        2. inferrer.infer(textBlocks) -> List<StructureElement>  // OfdStructureInferrer
        3. POI XWPFDocument 渲染：
           - HEADING -> createParagraph + setStyle("Heading1/2/3") + 保留原字号
           - PARAGRAPH -> createParagraph + 保留原字号
           - TABLE -> createTable(rows, cols) + 填充单元格
           - LIST -> createParagraph + numbering 或缩进
           - IMAGE_PLACEHOLDER -> "[图片]" 文本
        4. 写入 {base}.docx
        5. 返回 ConvertResult(out, base + ".docx", size, "single")
}
```

- 输出文件名：`report.ofd` -> `report.docx`（复用 `Ofd2Pdf.basename`）
- outputType = `single`
- 保留原始字号实现"视觉逼近"（可编辑 + 最大限度还原）

### Ofd2Markdown（OFD -> Markdown）

```java
@Component
public class Ofd2Markdown implements Converter {
    source() = OFD; target() = MD;
    convert(source, outputDir, sourceFilename, opts):
        1. extractor.extract(source) -> List<TextBlock>
        2. inferrer.infer(textBlocks) -> List<StructureElement>  // MdStructureInferrer
        3. 渲染 Markdown 文本：
           - HEADING -> "#".repeat(level) + " " + text
           - PARAGRAPH -> text + "\n\n"
           - TABLE -> Markdown 表格语法（表头行 + 分隔行 + 数据行）
           - LIST -> "- " + text（无序）/ "1. " + text（有序）
           - IMAGE_PLACEHOLDER -> "[图片]"
        4. 写入 {base}.md（UTF-8）
        5. 返回 ConvertResult(out, base + ".md", size, "single")
}
```

- outputType = `single`
- 表格降级：网格列数不一致时，按最大列数补空单元格

### 复用既有机制

- `ConvertPipeline` 自动装配（`@Component` + `source()`/`target()`）
- `ConvertService` 异步执行 + 超时 + 日志（无需改动）
- `ConvertFormat.DOCX`/`MD` 枚举已存在

### 已知限制

- 复合块（CompositeObject）内文字不递归提取
- 渐变、旋转文字不处理
- 表格合并单元格不还原（POI/MD 表格均不支持合并单元格语义）
- 复杂多栏版面可能误判

## 5. API 与有损提示

### /api/formats 更新

`ofd` 列表加回 `docx`/`md`：

```json
{
  "ofd": ["pdf", "png", "jpg", "txt", "docx", "md"],
  "pdf": ["ofd"],
  "image": ["ofd"]
}
```

（`docx` 作为源格式仍不出现--DOCX->OFD 已移出 Plan 2）

### 有损转换 warning 字段

OFD->DOCX 和 OFD->MD 是有损转换。任务查询返回 `warning` 字段：

`TaskResponse` 增加 `warning` 字段：
```java
public record TaskResponse(String taskId, String status, String downloadUrl, String error, String warning) {}
```

- OFD->DOCX：`warning = "版式转 DOCX 为有损转换，排版可能变化，仅供参考"`
- OFD->MD：`warning = "OFD 转 Markdown 为结构推断，复杂版面可能有损，仅供参考"`
- 其他转换：`warning = null`

**触发时机**：任务创建时（`ConvertService.convert`）根据 `target_format` 写入 `Task.warning`，`/api/task/{id}` 查询时返回。

### Task 表新增字段

```sql
ALTER TABLE task ADD COLUMN warning TEXT;
```

`Task` 实体加 `warning` 字段，`create()` 时按目标格式设置。

### 前端弹窗（Plan 3 依赖，Plan 2 不实现）

- API 返回的 `warning` 非空时，前端在发起转换前/下载前弹窗提示
- Plan 2 仅保证 API 字段就绪，前端展示逻辑记为 Plan 3 的接入点

### MCP 工具（Plan 4 依赖）

`extract_ofd_markdown` MCP 工具直接返回 Markdown 内容（供 Agent 消费，无需下载文件）--Plan 1 spec 已设计，Plan 2 只需保证 `Ofd2Markdown` 转换器可用，MCP 端点在 Plan 4 实现。

## 6. 测试与验收

### 扩展 Fixtures（可控回归）

在现有 `Fixtures.java` 基础上新增生成器，构造含结构的 OFD：

- `Fixtures.ofdWithHeadings(tmp)`：含 3 个不同字号文字块（模拟 H1/H2/正文）
- `Fixtures.ofdWithTable(tmp)`：含网格对齐文字块（模拟 2×3 表格）
- `Fixtures.ofdWithList(tmp)`：含行首编号模式（模拟有序/无序列表）

用 ofdrw-layout 的 `OFDDoc.add()` + 不同字号 `Paragraph` 构造。

### 单元测试

- `OfdTextBlockExtractorTest`：提取夹具 OFD，验证 TextBlock 数量/字号/坐标正确
- `OfdStructureInferrerTest`（DOCX）：输入含标题夹具，验证 H1/H2/正文分级正确
- `MdStructureInferrerTest`（MD）：输入含表格夹具，验证表格列数正确
- `Ofd2DocxTest`：转换夹具 OFD -> DOCX，验证输出是有效 ZIP + 用 POI 读回检查标题/段落存在
- `Ofd2MarkdownTest`：转换夹具 OFD -> MD，验证输出含 `#`/表格 `|`/列表 `-` 语法

### 真实样本端到端测试

- 收集 5+ 真实 OFD 文件放入 `backend/src/test/resources/test-ofd/`
- `RealSampleConversionTest`：遍历真实样本，执行 OFD->DOCX 和 OFD->MD，验证：
  - 不崩溃（无异常）
  - DOCX 是有效 ZIP，POI 可读回
  - MD 非空，含至少一个标题或段落
- **降级验证**：结构推断失败的样本仍产出可用输出（纯文本段落）

### 验收标准

- OFD->DOCX：文字保留率 > 90%，标题层级基本正确
- OFD->MD：标题/表格/列表结构基本正确，供 Agent 消费可读
- 每种转换至少跑通 5 个真实 OFD 文件，零崩溃
- 有损 warning 字段正确返回
- 推断失败降级为纯文本，保证可用

### 已知限制

- 复合块内文字不提取
- 表格合并单元格不还原
- 渐变/旋转文字不处理
- 真实样本若含这些复杂特性，相应部分降级

## 7. 后续扩展（不在 Plan 2 范围）

- DOCX -> OFD（完整排版引擎，后续 plan）
- 复合块递归提取
- 表格合并单元格还原
- 渐变/旋转文字处理
- 模板层（页眉页脚）特殊处理
- 图片二进制提取（若 Agent 需多模态消费）
