# OFD 文件格式转换工具 — 设计文档

> 创建日期：2026-07-05
> 状态：设计阶段（v7 — 修复反向转换 API 等 review 问题）

## 1. 项目概述

基于 Web 的 OFD（Open Fixed-layout Document，GB/T 33190-2016）文件格式转换工具。支持在线转换和 AI Agent 调用两种使用方式。

### 目标用户

- 普通用户：通过网页上传文件、选择格式、下载转换结果
- AI Agent：通过 REST API 或 MCP 协议调用转换能力

### 核心功能

- OFD → PDF / 图片（PNG/JPG）/ DOCX / 文本 / Markdown
- PDF / 图片 / DOCX → OFD
- OFD 文件在线预览
- 单文件 / 批量转换
- 操作日志记录（基于 IP，用于审计与排错）
- 多用户匿名使用（认证后期扩展）

## 2. 技术栈

| 层 | 技术 | 说明 |
|---|---|---|
| 前端 | React 19 + TypeScript 6 + Vite 8 | SPA，静态资源（版本需在 PoC 阶段验证兼容性） |
| OFD 预览 | ofd.js（332 ⭐） | 纯前端 SVG/Canvas 渲染，需验证与 React 19 集成 |
| 后端 | Java Spring Boot 3 | REST API + MCP 端点 |
| OFD 引擎 | ofdrw 2.x（1.8k ⭐） | 最成熟的 OFD 开源库 |
| DOCX 处理 | Apache POI | DOCX 读写 |
| PDF 处理 | ofdrw-converter + PDFBox | PDF 解析与生成 |
| 数据库 | SQLite（WAL 模式） | 任务记录、文件元数据、操作日志 |
| 文件存储 | 本地磁盘 | `/data/uploads/` + `/data/outputs/` |
| 部署 | Docker Compose | nginx + Spring Boot |
| 认证 | 无（后期扩展） | 当前匿名使用，以 IP 标识用户 |

## 3. 项目结构

```
ofd-converter/
├── frontend/                    # React + TS + Vite
│   ├── src/
│   │   ├── components/
│   │   │   ├── UploadZone.tsx    # 拖拽/选择上传区域
│   │   │   ├── FileList.tsx      # 已上传文件列表
│   │   │   ├── PreviewPanel.tsx  # ofd.js 预览面板
│   │   │   ├── ConvertOptions.tsx# 格式选择 + 参数配置
│   │   │   ├── TaskList.tsx      # 转换任务状态列表
│   │   │   └── DownloadButton.tsx# 下载按钮
│   │   ├── hooks/
│   │   │   ├── useUpload.ts      # 上传逻辑
│   │   │   ├── useConvert.ts     # 转换请求逻辑
│   │   │   ├── usePreview.ts     # ofd.js 预览逻辑
│   │   │   └── useTaskPolling.ts # 任务状态轮询
│   │   ├── api/
│   │   │   └── client.ts         # REST API 封装
│   │   ├── types/
│   │   │   ├── ofdjs.d.ts        # ofd.js 类型声明（自写）
│   │   │   └── api.ts            # API 响应类型
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── public/
│   ├── index.html
│   ├── nginx.conf
│   ├── Dockerfile
│   └── package.json
├── backend/
│   ├── src/main/java/com/ofd/converter/
│   │   ├── OfdConverterApplication.java
│   │   ├── controller/
│   │   │   ├── ConvertController.java    # REST API 端点
│   │   │   ├── McpController.java        # MCP 协议端点
│   │   │   └── GlobalExceptionHandler.java # 统一异常处理
│   │   ├── service/
│   │   │   ├── FileService.java          # 文件存储管理
│   │   │   ├── ConvertService.java       # 转换任务编排
│   │   │   ├── TaskService.java          # 任务状态管理
│   │   │   ├── ValidationService.java    # 文件类型/大小校验
│   │   │   └── LogService.java           # 操作日志记录
│   │   ├── engine/
│   │   │   ├── OfdEngine.java            # ofdrw 封装
│   │   │   ├── converters/
│   │   │   │   ├── Ofd2Pdf.java
│   │   │   │   ├── Ofd2Image.java
│   │   │   │   ├── Ofd2Docx.java
│   │   │   │   ├── Ofd2Text.java
│   │   │   │   ├── Ofd2Markdown.java
│   │   │   │   ├── Pdf2Ofd.java
│   │   │   │   ├── Image2Ofd.java
│   │   │   │   └── Docx2Ofd.java
│   │   │   └── ConvertPipeline.java      # 转换流水线
│   │   ├── model/
│   │   │   ├── Task.java                 # 任务实体
│   │   │   ├── TaskStatus.java           # 任务状态枚举
│   │   │   ├── ConvertFormat.java        # 支持格式枚举
│   │   │   ├── OperationLog.java         # 操作日志实体
│   │   │   ├── OperationType.java        # 操作类型枚举
│   │   │   ├── ApiError.java             # 统一错误响应
│   │   │   └── dto/                      # 请求/响应 DTO
│   │   ├── repository/
│   │   │   ├── TaskRepository.java       # 任务 DAO
│   │   │   └── OperationLogRepository.java # 操作日志 DAO
│   │   ├── interceptor/
│   │   │   └── ClientIpInterceptor.java  # 提取客户端 IP
│   │   ├── scheduler/
│   │   │   ├── FileCleanupScheduler.java # 定时文件清理
│   │   │   └── LogCleanupScheduler.java  # 定时日志清理
│   │   └── config/
│   │       ├── ThreadPoolConfig.java     # 转换线程池配置
│   │       ├── WebConfig.java            # CORS、转发头处理
│   │       └── RetentionProperties.java  # 保留期配置（文件/日志）
│   ├── src/test/java/com/ofd/converter/  # 测试代码
│   │   ├── engine/                       # 转换器单元测试
│   │   ├── controller/                   # API 集成测试
│   │   └── resources/test-ofd/           # 测试用 OFD 文件
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── schema.sql                    # 数据库初始化
│   ├── Dockerfile
│   └── pom.xml
├── docker-compose.yml
└── README.md
```

## 4. 架构

### 部署拓扑

```
┌──────────────────────────────────────────────────┐
│                   Docker Compose                   │
│                                                    │
│  ┌──────────────┐    ┌───────────────────────────┐│
│  │   nginx      │    │   Spring Boot (backend)    ││
│  │   - 静态资源  │    │   - REST API :8080         ││
│  │   - 反向代理  │───▶│   - MCP 端点 /mcp          ││
│  │   - 端口 80   │    │   - ofdrw 转换引擎         ││
│  │   - 透传 XFF  │    │   - SQLite 持久化           ││
│  └──────────────┘    │   - 文件管理 + 日志记录      ││
│                      └───────────────────────────┘│
│  ┌──────────────────────────────────────────────┐ │
│  │  数据卷                                       │ │
│  │  - /data/uploads/    # 上传文件               │ │
│  │  - /data/outputs/    # 转换结果               │ │
│  │  - /data/converter.db # SQLite 数据库         │ │
│  └──────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
```

**nginx 职责：** 服务前端静态资源（React 构建产物），反向代理 `/api/*` 和 `/mcp` 到 Spring Boot，透传 `X-Forwarded-For` 头以便后端获取真实客户端 IP。

### 数据流

```
1. 用户上传 OFD → nginx（记录 XFF）→ Spring Boot → 文件校验 → /data/uploads/{uuid}/
   → LogService 记录 upload 操作（IP、file_id、文件名、状态）
2. 前端调用 /api/convert → 后端创建 Task → 返回 task_id
   → LogService 记录 convert 操作（IP、file_id、task_id、目标格式）
3. 线程池执行转换（受超时和内存限制）→ ofdrw 解析 → 目标格式渲染 → /data/outputs/{uuid}/
   → 更新 Task 状态，LogService 补充转换结果与耗时
4. 前端轮询 /api/task/{task_id} → 状态变为 done → 获取下载链接
5. 用户下载 → 后端标记已下载
   → LogService 记录 download 操作（IP、task_id）
6. 定时任务清理超过配置保留期的文件与日志（默认文件 24 小时 / 日志 90 天，可配置）
```

## 5. 转换引擎

### ⚠️ 实施前置：PoC 验证

ofdrw 的能力边界需在正式开发前通过 PoC 验证。**PoC 必须先于其他实施工作完成。** 详见第 14 节阶段 0。

### 转换能力矩阵（按置信度分级）

| 转换方向 | 核心依赖 | 置信度 | 说明 |
|---------|---------|--------|------|
| OFD → PDF | ofdrw-converter（PDFExporterPDFBox） | 🟢 高 | 原生支持；⚠️ 官方标注"不推荐/LTS不再更新"，但仍可用 |
| OFD → PNG/JPG | ofdrw-converter（ImageExporter） | 🟢 高 | 原生支持，可配置 PPM（每毫米像素数） |
| OFD → 文本 | ofdrw-converter（TextExporter） | 🟡 中 | 原生支持，但有条件：字形索引/路径图元/纯图片页面无法导出 |
| OFD → Markdown | 待 PoC 定路径 | 🟡 中 | 无原生支持；PoC 验证三条路径（见下方"OFD → Markdown 实现路径"），择优实现 |
| OFD → DOCX | ofdrw-reader + Apache POI | 🟡 中 | 无原生支持；固定版式 → 流式排版，本质有损，需自研映射 |
| PDF → OFD | ofdrw-converter（PDFConverter） | 🟢 高 | 原生支持（PDFBox + graphics2d 桥接）；转换效果可能丢失部分特性 |
| 图片 → OFD | ofdrw-converter（ImageConverter） | 🟢 高 | 原生支持，PNG/JPG/BMP，每图独立成页 |
| DOCX → OFD | Apache POI + ofdrw-layout | 🔴 低 | 无原生支持；流式 → 固定版式，需自研排版引擎，工作量大 |

**备注（ofdrw 原生但未列入 v1 用户功能）：**
- OFD → SVG（SVGExporter）、OFD → HTML（HTMLExporter，基于 SVG）也为原生支持，可作为中间格式或预览备用方案
- 若 ofd.js 前端集成 PoC 失败，可降级用 OFD → HTML 服务端渲染替代预览
- 文本 → OFD（TextConverter）原生支持，但 v1 不单独暴露（DOCX→OFD 若实现会内部复用）

### 根本性挑战说明

**OFD ↔ DOCX 互转是有损的难题：**
- OFD 是**固定版式**（每个元素有绝对坐标），DOCX 是**流式排版**（内容随页面重排）
- OFD → DOCX：需推断文字段落、表格结构，复杂版面（多栏、叠加）难以准确还原
- OFD → Markdown：同样需推断结构，但 Markdown 容错性高，表格/标题推断失败时可降级为纯文本
- DOCX → OFD：需自行实现排版引擎，将流式内容渲染为固定坐标，相当于重写一个简易 Word 排版器
- 这与业界"PDF ↔ Word"难题等同，**无法做到 100% 还原**，需在 UI 上明确提示用户

### PoC 验证项

正式开发前必须验证以下三项，每项至少跑通 3 个真实 OFD 文件：

1. **OFD → DOCX**：验证 ofdrw-reader 文本/表格/图片提取 + POI 重建效果，确定有损程度
2. **OFD → Markdown**：双路径验证（见下方"OFD → Markdown 实现路径"），择优实现
3. **DOCX → OFD**：验证 POI 提取 + ofdrw-layout 构造的可行性与排版效果

> PDF → OFD、图片 → OFD 已由 ofdrw-converter 原生支持（PDFConverter/ImageConverter），无需 PoC。

### OFD → Markdown 实现路径（PoC 双路径验证 + 兜底）

无原生 OFD→Markdown 库，需基于 ofdrw 已有能力组合。PoC 阶段验证三条路径，择优实现：

| 路径 | 实现 | 结构质量 | 预评估 |
|------|------|---------|--------|
| **B. OFD→HTML→MD** | ofdrw HTMLExporter + flexmark-java HTML→Markdown | 差 | HTMLExporter 基于 SVG，输出非语义化 HTML，flexmark 大概率失效；PoC 需验证实际输出是否含语义标签 |
| **A. ofdrw-reader 自研** | ofdrw-reader 取文字图元（字号/坐标）→ 启发式推断结构 → 生成 MD | 好 | 复用 TextExporter 同款解析基础，额外做结构推断；约几百行代码 |
| **C. OFD→Text→MD（兜底）** | ofdrw TextExporter + 按行分段套 .md | 几乎无 | 能跑但无结构价值，仅作基线兜底 |

**PoC 验证步骤：**
1. 取 3 个真实 OFD 文件，用 HTMLExporter 导出，检查 HTML 是否含 `<h1>/<p>/<table>` 等语义标签
2. 若路径 B 输出语义化 → 直接采用（最省事）
3. 若路径 B 输出 SVG 矢量 → 验证路径 A 的字号/坐标结构推断效果
4. 若 A/B 均不理想 → 临时采用路径 C 兜底，后续迭代增强

**降级策略：** 结构推断失败的元素（如复杂表格）降级为纯文本段落，保证 Markdown 可用。

**PoC 失败的应对：** 若某方向验证失败，将该方向降级为"实验性功能"或移出 v1 范围，spec 同步更新。

### 批量转换

- 每个文件创建独立 Task
- 线程池：最多 4 个并行转换任务
- 任务状态机：`pending → processing → done | failed | timeout`
- 单任务超时：5 分钟（超时后标记 `timeout`，释放线程）

### 已知限制

- ofdrw 的 OFD → DOCX 非原生，转换质量有限
- 复杂 OFD 文档（渐变、特殊字体）可能存在渲染差异
- 转换质量依赖 ofdrw 版本，需跟随上游更新

## 6. API 设计

### REST API

| 端点 | 方法 | 请求 | 成功响应 | 失败响应 |
|------|------|------|---------|---------|
| `/api/upload` | POST | multipart/form-data（文件） | `{ "file_id": "uuid", "filename": "...", "size": N }` | 见错误响应格式 |
| `/api/convert` | POST | `{ "file_id": "uuid", "target_format": "...", "options": {...} }` | `{ "task_id": "uuid", "status": "pending" }` | 见错误响应格式 |
| `/api/task/{task_id}` | GET | — | `{ "task_id": "uuid", "status": "...", "download_url"?: "...", "error"?: "..." }` | 404 若不存在 |
| `/api/download/{task_id}` | GET | — | 文件流（Content-Disposition: attachment） | 410 若已清理/过期 |
| `/api/formats` | GET | — | 按源格式分组的可用目标格式（见下方示例） | — |
| `/health` | GET | — | `{ "status": "ok" }`（供 Docker healthcheck） | — |

**`target_format` 取值：** `pdf` | `png` | `jpg` | `docx` | `txt` | `md` | `ofd`

> **反向转换：** `target_format: "ofd"` 触发 PDF/图片/DOCX → OFD。系统按 file_id 对应源文件魔数判定源格式，依 (源格式, 目标格式) 矩阵选择 converter。

**`/api/formats` 响应示例（按源格式分组）：**

```json
{
  "ofd": ["pdf", "png", "jpg", "docx", "txt", "md"],
  "pdf": ["ofd", "png", "jpg"],
  "image": ["ofd"],
  "docx": ["ofd"]
}
```

**`options` 可选字段：**
- `pages`: string — 页码范围，如 `"1-5"`、`"1,3,5"`
- `dpi`: number — 图片导出分辨率，默认 150

### 统一错误响应格式

所有 4xx/5xx 响应统一格式：

```json
{
  "error": {
    "code": "INVALID_FILE_TYPE",
    "message": "不支持的文件类型: .exe",
    "details": {}
  }
}
```

**错误码规范：**

| HTTP | code | 场景 |
|------|------|------|
| 400 | `INVALID_FILE_TYPE` | 文件类型不支持（魔数校验失败） |
| 400 | `FILE_TOO_LARGE` | 超过 50MB 限制 |
| 400 | `INVALID_REQUEST` | 请求参数错误 |
| 404 | `TASK_NOT_FOUND` | task_id 不存在 |
| 410 | `FILE_EXPIRED` | 文件已清理或过期 |
| 409 | `TASK_FAILED` | 转换失败（error 字段含原因） |
| 408 | `TASK_TIMEOUT` | 转换超时 |
| 500 | `INTERNAL_ERROR` | 服务内部错误 |
| 503 | `STORAGE_FULL` | 磁盘空间不足，拒绝上传 |

### MCP 协议端点

- 端点：`POST /mcp`
- 协议：JSON-RPC 2.0 over HTTP
- 传输：HTTP（非 stdio，因为是远程服务）

**文件输入方式：上传优先模式**（与 REST API 一致）

MCP 工具调用流程：
1. Agent 先调用 `upload_file` 工具上传文件，获得 `file_id`
2. 再调用 `convert_ofd` 工具，传入 `file_id`

**暴露工具：**

| 工具名 | 功能 | 参数 |
|--------|------|------|
| `upload_file` | 上传文件 | `content`: base64 编码文件内容，`filename`: string → 返回 `file_id` |
| `convert_ofd` | 格式转换 | `file_id`: string，`target_format`: string，`options?`: object → 返回 `task_id` |
| `get_task_status` | 查询任务状态 | `task_id`: string → 返回状态和下载信息 |
| `extract_ofd_text` | 文本提取 | `file_id`: string，`pages?`: string → 返回文本内容 |
| `extract_ofd_markdown` | 结构化 Markdown 提取 | `file_id`: string，`pages?`: string → 返回 Markdown 内容（供 Agent 直接消费，无需下载文件） |
| `list_formats` | 格式列表 | — → 返回支持的转换格式 |

**示例调用：**

```json
// 1. 上传
{"jsonrpc":"2.0","id":1,"method":"tools/call",
 "params":{"name":"upload_file",
 "arguments":{"filename":"report.ofd","content":"<base64>"}}}
→ {"result":{"file_id":"uuid-xxx"}}

// 2. 转换
{"jsonrpc":"2.0","id":2,"method":"tools/call",
 "params":{"name":"convert_ofd",
 "arguments":{"file_id":"uuid-xxx","target_format":"pdf"}}}
→ {"result":{"task_id":"uuid-yyy","status":"pending"}}
```

> **大文件提示：** MCP `upload_file` 使用 base64 编码，适用于中小文档（建议 < 10MB）。大文件请走 REST `/api/upload`（multipart 更高效）。

## 7. 文件管理

### 目录结构

- **上传目录**：`/data/uploads/{file_id}/` 保存原始文件（UUID 命名，避免路径遍历）
- **输出目录**：`/data/outputs/{task_id}/` 保存转换结果
- **文件大小限制**：单文件最大 50MB

### 输出文件命名

- **单文件输出**：保留原文件名（净化后）+ 目标扩展名
  - 例：`report.ofd` → `report.pdf`、`report.ofd` → `report.md`、`report.ofd` → `report.txt`
- **多文件输出**（OFD→图片，每页一个文件）：目录内按 `{index}.png` 命名，下载时自动打包 ZIP
  - 例：`report.ofd` → `report_images.zip`（内含 `0.png`、`1.png`...）
- **文件名净化**：剥离路径分隔符、空字节等危险字符，仅保留文件名主体

### 下载机制

- 下载端点：`GET /api/download/{task_id}`
- 响应头：`Content-Disposition: attachment; filename="{output_filename}"`
- **保留期内可重复下载**（非一次性），首次下载时间记入日志
- 多文件输出返回 ZIP，单文件输出直接返回文件流
- 文件过期或已清理时返回 410 `FILE_EXPIRED`

### 数据库字段（Task 表输出相关）

| 字段 | 说明 |
|------|------|
| `output_path` | 输出目录相对路径 |
| `output_filename` | 下载时显示的文件名（如 `report.pdf` 或 `report_images.zip`） |
| `output_size` | 文件大小（字节） |
| `output_type` | `single` \| `archive`（ZIP） |
| `downloaded_at` | 首次下载时间戳（仅记录，不阻断再次下载） |

### 文件保留与清理

- **保留期可配置**，不写死：
  - 配置项 `file.retention-hours`，默认 24 小时
  - 部署时通过环境变量 `FILE_RETENTION_HOURS` 覆盖（Docker Compose 场景）
- `FileCleanupScheduler` 每小时扫描，清理超过保留期的上传文件、输出文件及对应数据库记录
- **失败清理**：转换失败/超时时，删除部分输出的临时文件，仅保留错误信息
- **磁盘保护**：`/data` 使用率超 95% 时拒绝新上传（见第 13 节）

> 日志保留期同理可配置：`log.retention-days`（默认 90 天，环境变量 `LOG_RETENTION_DAYS`）。
> 运行时通过管理 API 修改保留期的能力将在认证模块上线后提供（见第 15 节）。

## 8. 安全设计

虽无认证，仍需基础安全防护：

- **文件类型校验**：不只看扩展名，校验文件魔数
  - OFD：ZIP 格式（`PK\x03\x04`）
  - PDF：`%PDF`
  - DOCX：ZIP 格式
  - 图片：PNG/JPG 魔数
- **文件名净化**：存储用 UUID，原始文件名入库前剥离路径分隔符、空字节等危险字符
- **路径隔离**：所有文件操作限制在 `/data/` 下，拒绝包含 `..` 的路径
- **转换内存限制**：单任务 JVM 内存上限 512MB，超出则失败
- **转换超时**：单任务 5 分钟，超时强制中断释放线程
- **上传频率限制**：单 IP 每分钟最多 20 次上传（防滥用）
- **日志脱敏**：日志不记录文件内容，仅记录文件名、大小、task_id、IP

## 9. 操作日志记录

由于当前无用户认证，以**客户端 IP** 作为用户标识，记录所有转换操作，用于排错、用量统计和滥用检测。

### 日志记录范围

| 操作类型 | 触发时机 | 记录内容 |
|---------|---------|---------|
| `UPLOAD` | 文件上传完成 | IP、file_id、filename、size、user_agent、状态、耗时 |
| `CONVERT` | 转换任务创建 + 完成 | IP、file_id、task_id、target_format、options、状态、耗时、错误信息 |
| `DOWNLOAD` | 下载转换结果 | IP、task_id、状态 |
| `MCP_CALL` | MCP 工具调用 | IP、工具名、参数（不含文件内容）、状态、耗时 |

### 日志数据结构（SQLite `operation_log` 表）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | TEXT PK | 日志 ID（UUID） |
| `operation_type` | TEXT | 操作类型：UPLOAD/CONVERT/DOWNLOAD/MCP_CALL |
| `client_ip` | TEXT | 客户端 IP |
| `file_id` | TEXT | 关联的文件 ID（可空） |
| `task_id` | TEXT | 关联的任务 ID（可空） |
| `target_format` | TEXT | 目标格式（CONVERT 时） |
| `status` | TEXT | SUCCESS / FAILED / TIMEOUT |
| `duration_ms` | INTEGER | 操作耗时（毫秒） |
| `error_message` | TEXT | 错误信息（失败时） |
| `user_agent` | TEXT | 客户端 User-Agent |
| `created_at` | INTEGER | 时间戳（毫秒） |

### 客户端 IP 获取

- nginx 反向代理时通过 `X-Forwarded-For` 头透传真实 IP
- Spring Boot 配置 `server.forward-headers-strategy=native`，信任 nginx 代理
- `ClientIpInterceptor` 拦截器统一提取 IP：
  - 优先取 `X-Forwarded-For` 第一个值（最左侧为原始客户端）
  - 回退到 `X-Real-IP`
  - 最终回退到 `HttpServletRequest.getRemoteAddr()`
- **防伪造**：仅信任来自 nginx（可信代理网段）的 `X-Forwarded-For`，避免客户端直接伪造

### 日志写入策略

- `LogService` 异步写入（不阻塞主请求流程），通过独立线程池落库
- 写入失败不影响业务操作（仅记录应用日志告警）
- 转换类操作在"创建"和"完成"两个时点分别记录，或合并为一条带最终状态（采用合并方案，减少写入量）

### 日志保留与清理

- **保留期**：90 天（比文件 24 小时长，便于排错和审计）
- **清理**：`LogCleanupScheduler` 每天清理超过 90 天的日志记录
- **存储**：与任务记录同库（SQLite），日志量可控（90 天 × 日均操作数）

### 隐私说明

- IP 地址属于个人信息，仅用于运维和防滥用，不向第三方共享
- 90 天后自动删除，不长期留存
- 日志不含文件内容，仅含文件元数据（名、大小、ID）

## 10. 错误处理策略

### 转换失败处理

- 转换器抛出异常 → 捕获 → Task 标记 `failed`，记录 error 信息
- 超时（5 分钟）→ Task 标记 `timeout`，中断线程
- OOM → Task 标记 `failed`，error 记录"内存不足"
- 部分输出文件 → 删除，避免残留无效文件
- **所有失败均通过 LogService 记录**，含错误信息和耗时

### 前端错误展示

- 上传失败：上传区显示错误提示
- 转换失败：任务列表显示失败原因（来自 error.message）
- 下载失败：提示文件已过期或已下载

### 后端错误兜底

- `GlobalExceptionHandler` 统一捕获未处理异常，返回 500 + `INTERNAL_ERROR`
- 转换线程池满 → 新任务排队等待（不拒绝，但提示预计等待时间）

## 11. 前端设计

### 页面布局

```
┌─────────────────────────────────────────────┐
│  OFD 转换工具                                │
├─────────────────────────────────────────────┤
│  ┌───────────────────────────────────────┐  │
│  │     拖拽文件到此处 / 点击选择            │  │
│  │     支持 OFD / PDF / DOCX / 图片       │  │
│  └───────────────────────────────────────┘  │
│                                             │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ │
│  │ 文件列表   │ │ 转换选项   │ │ 预览面板   │ │
│  │ file1.ofd │ │ ○ PDF     │ │ (ofd.js)  │ │
│  │ file2.ofd │ │ ○ PNG     │ │           │ │
│  │ ...       │ │ ○ JPG     │ │ 页面渲染   │ │
│  │           │ │ ○ DOCX    │ │           │ │
│  │           │ │ ○ TXT     │ │           │ │
│  │           │ │ ○ MD      │ │           │ │
│  │           │ │ [开始转换] │ │           │ │
│  └───────────┘ └───────────┘ └───────────┘ │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │ 转换任务                               │  │
│  │ file1.ofd → PDF  ✓ 完成 [下载]         │  │
│  │ file2.ofd → DOCX ⏳ 转换中...           │  │
│  │ file3.ofd → TXT  ✗ 失败: 文件损坏      │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

### 关键交互

- **上传**：拖拽 + 文件选择器，支持多文件
- **预览**：选中 OFD 文件后在右侧面板用 ofd.js 渲染
- **转换**：单选/批量选择格式，一键发起
- **状态**：轮询任务状态（2 秒间隔），实时更新
- **下载**：完成后显示下载按钮，点击即下载
- **有损提示**：选择 OFD↔DOCX 转换时，弹窗提示"版式转换可能有损，仅供参考"

### ofd.js 集成（PoC 优先）

- ofd.js 是 Vue 项目，核心渲染为原生 JS，需在 React 中通过 `useRef` + `useEffect` 调用
- 缺少类型定义，需自写 `types/ofdjs.d.ts`
- **前端开发第一步必须先做 ofd.js 集成 PoC**，验证与 React 19 兼容性

## 12. 测试策略

### 测试用例资产

- 准备标准 OFD 测试文件集（至少 10 个），覆盖：
  - 纯文本单页 / 多页
  - 含图片
  - 含表格
  - 含签章
  - 不同字体（含中文字体）
  - 大文件（接近 50MB）

### 后端测试

- **单元测试**：每个 converter 独立测试，输入标准 OFD，校验输出
- **转换质量校验**：
  - OFD → PDF：页数一致，文字可搜索
  - OFD → 图片：分辨率、页数正确
  - OFD → 文本：文字内容完整
  - OFD → DOCX：文字、表格结构基本保留（允许排版差异）
  - OFD → Markdown：标题层级、表格、列表结构基本正确
- **API 集成测试**：上传 → 转换 → 查询 → 下载全流程
- **异常测试**：损坏文件、超大文件、恶意文件名、超时场景
- **日志测试**：验证各操作类型均正确写入日志，IP 提取准确，异步写入不阻塞

### 前端测试

- **组件测试**：上传、预览、转换选项、任务列表
- **集成测试**：完整转换流程（mock API）
- **ofd.js 集成测试**：验证预览渲染正确

### 验收标准

- 每种转换至少跑通 5 个真实 OFD 文件
- 核心转换（OFD→PDF/图片/文本）零失败
- 有损转换（OFD↔DOCX）文字内容保留率 > 90%
- 所有操作均有日志记录，IP 提取正确率 100%

## 13. 非功能需求

- **并发**：支持 50 用户同时在线
- **并行转换**：最多 4 个转换任务同时执行
- **文件限制**：单文件 ≤ 50MB
- **转换超时**：单任务 ≤ 5 分钟
- **内存限制**：单任务 ≤ 512MB
- **文件保留**：可配置，默认 24 小时（`FILE_RETENTION_HOURS`）
- **日志保留**：可配置，默认 90 天（`LOG_RETENTION_DAYS`）
- **磁盘监控**：`/data` 使用率超过 80% 告警，超过 95% 拒绝新上传
- **部署**：Docker Compose 一键启动
- **存储**：所有数据在容器卷内，不依赖外部服务

## 14. 实施阶段划分

### 阶段 0：PoC 验证（前置必做）

1. ofdrw 转换能力 PoC（验证第 5 节待 PoC 验证项，🟡/🔴）
2. ofd.js + React 19 集成 PoC
3. 根据结果调整 spec 范围

### 阶段 1：核心转换（ofdrw 原生）

- OFD → PDF / 图片
- OFD → 文本（TextExporter，含限制场景兜底）
- PDF → OFD、图片 → OFD
- 后端框架 + REST API + 文件管理
- **操作日志记录（LogService + IP 提取）**
- 前端基础界面 + 上传 + 下载

### 阶段 2：补充转换（🟡 中置信度，依赖 PoC）

- OFD → DOCX
- OFD → Markdown（路径 A：ofdrw-reader 自研）
- 前端预览（ofd.js，失败则降级为 OFD→HTML 服务端渲染）

### 阶段 3：反向转换（🔴 低置信度，依赖 PoC）

- DOCX → OFD
- 若 PoC 失败则降级或移除

### 阶段 4：AI Agent 接入

- MCP 协议端点
- 工具暴露
- MCP 调用日志记录

### 阶段 5：部署与测试

- Docker Compose 配置
- 完整测试套件（含日志测试）
- 性能验证

## 15. 后续扩展（不在当前范围）

- 用户注册/登录/权限管理（接入后日志将记录真实用户而非 IP）
- 企业 SSO（LDAP/OIDC）
- 操作日志查询 API + 管理后台界面
- 运行时配置管理 API（管理员可调文件/日志保留期，需认证）
- 对象存储（MinIO / S3）替代本地磁盘
- PostgreSQL 替代 SQLite
- 转换历史记录查询
- WebSocket 推送任务状态（替代轮询）
- OFD 签章验证（ofdrw-sign / ofdrw-gm 模块原生支持）
