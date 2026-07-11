# OFD 转换工具 - Plan 4 设计文档（MCP 协议端点）

> 创建日期：2026-07-11
> 状态：设计阶段
> 依赖：Plan 1（后端 API/服务）+ Plan 2（Ofd2Markdown/Ofd2Text 转换器）已完成

## 1. 范围与组件

### 范围

- MCP 端点 `POST /mcp`（JSON-RPC 2.0 over HTTP，无状态单次请求/响应）
- 完整握手（`initialize` -> `notifications/initialized`）+ 会话管理（内存 + TTL 30 分钟）
- 严格会话校验：`tools/call` 必须有效 Session-Id + initialized
- 6 个工具：upload_file / convert_ofd / get_task_status / extract_ofd_text / extract_ofd_markdown / list_formats

### 新增组件

```
backend/src/main/java/com/ofd/converter/
├── controller/McpController.java          # POST /mcp，JSON-RPC 分发
├── mcp/
│   ├── McpSessionService.java             # 会话管理（内存 Map + TTL）
│   ├── McpSession.java                    # 会话实体
│   ├── McpToolRegistry.java               # 工具定义 + 分发
│   ├── McpTool.java                       # 工具接口
│   ├── McpErrors.java                     # JSON-RPC 错误码常量
│   └── tools/
│       ├── UploadFileTool.java
│       ├── ConvertOfdTool.java
│       ├── GetTaskStatusTool.java
│       ├── ExtractOfdTextTool.java
│       ├── ExtractOfdMarkdownTool.java
│       └── ListFormatsTool.java
```

### 复用 Plan 1/2

- `ConvertService`（upload/convert）、`TaskService`（get）、`FileService`（uploadFile/createOutputDir）
- `Ofd2Text`/`Ofd2Markdown` 转换器（extract 工具同步直接调用）
- `ValidationService`（file_id 校验）、`LogService`（MCP_CALL 日志）

## 2. MCP 协议处理

### JSON-RPC 2.0 请求/响应

请求格式：
```json
{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"upload_file","arguments":{...}}}
```

响应（成功）：
```json
{"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"..."}]}}
```

响应（错误）：
```json
{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"无效参数"}}
```

通知（无 id，无响应）：
```json
{"jsonrpc":"2.0","method":"notifications/initialized"}
```

### McpController 流程

```
POST /mcp（Content-Type: application/json）
1. 解析 JSON-RPC envelope（jsonrpc/id/method/params）
2. 提取 Session-Id（Mcp-Session-Id 头或 params._meta.sessionId）
3. 按 method 分发：
   - initialize:               创建会话，返回 {sessionId, protocolVersion, capabilities}
   - notifications/initialized: 标记会话 initialized=true（通知，无响应体）
   - tools/list:               返回工具定义列表（需有效会话）
   - tools/call:               校验会话 -> 按工具名分发 -> 返回结果
4. 错误：JSON-RPC 错误对象（含 code/message/data）
5. 日志：所有 tools/call 记 MCP_CALL 操作日志
```

### 关键决策

- **Session-Id 传递**：`initialize` 响应头返回 `Mcp-Session-Id`；后续请求带该头。也支持 `params._meta.sessionId`（兼容无自定义头能力的客户端）。
- **通知无响应**：`notifications/*`（如 `notifications/initialized`）按 JSON-RPC 规范无 id，返回 202 Accepted（无 body）。
- **协议版本**：`initialize` 返回 `"2025-06-18"`（MCP 当前版本）。
- **错误码**：`-32700`（解析错误）、`-32600`（无效请求）、`-32601`（方法不存在）、`-32602`（无效参数）、`-32603`（内部错误）。会话相关：自定义 `-32000`（无会话）、`-32001`（会话未初始化）。

## 3. 会话管理

### McpSession

```java
class McpSession {
    String sessionId;          // UUID
    boolean initialized;       // initialize 后置 true（notifications/initialized）
    long createdAt;            // 创建时间
    long lastActivity;         // 最后活动时间（每次请求更新）
}
```

### McpSessionService

- `initialize()`：创建会话，返回 sessionId。`initialized=false`（等 `notifications/initialized`）。
- `markInitialized(sessionId)`：标记 initialized=true。
- `get(sessionId)`：返回会话，更新 lastActivity；不存在返回 empty。
- `requireInitialized(sessionId)`：校验会话存在 + initialized=true，否则抛 MCP 错误。
- TTL 清理：定时任务（`@Scheduled` 每 5 分钟）清理 lastActivity > 30 分钟的会话。

### 关键决策

- **存储**：`ConcurrentHashMap<String, McpSession>`（线程安全，50 并发无压力）。
- **TTL 30 分钟**：从 lastActivity 起算，每次请求刷新。超时会话被清理，Agent 需重新握手。
- **initialized 时机**：MCP 规范是 `initialize` 后客户端发 `notifications/initialized` 才算完成。严格模式下，`initialize` 与 `notifications/initialized` 之间的 `tools/call` 应被拒绝（会话存在但 initialized=false）。
- **无认证**：会话仅是握手状态，不做用户身份绑定（与 REST 一致，认证后期扩展）。
- **日志**：会话本身不记日志（`tools/call` 记 MCP_CALL）。

### 边界情况

- 同一 Agent 重发 `initialize`：返回新 sessionId（旧会话自然 TTL 过期，不主动销毁）。
- 会话过期后带旧 Session-Id 调用：返回 `-32000` 无会话错误，Agent 重新握手。
- `notifications/initialized` 对不存在/已初始化的会话：幂等处理（不报错，无副作用）。

## 4. 工具定义与实现

### 工具接口

```java
interface McpTool {
    String name();
    String description();
    Object inputSchema();          // JSON Schema（参数定义）
    Object execute(Map<String,Object> args, McpSession session) throws Exception;
}
```

### 6 个工具

**upload_file**
- 参数：`filename: string`，`content: string`（base64 编码文件内容）
- 流程：解码 base64 -> `ValidationService`（大小/类型魔数）-> `FileService.storeUpload` -> 返回 `file_id`
- 响应：`{file_id, filename, size, source_type}`
- 日志：MCP_CALL（upload_file）
- 限制：大文件提示走 REST（< 10MB，spec §6）

**convert_ofd**
- 参数：`file_id: string`，`target_format: string`，`options?: object`
- 流程：`ValidationService.requireFileId` -> `ConvertService.convert`（复用异步机制）-> 返回 `task_id` + `status`
- 响应：`{task_id, status, warning?}`
- 注：异步，Agent 需 `get_task_status` 轮询

**get_task_status**
- 参数：`task_id: string`
- 流程：`TaskService.get` -> 返回状态 + download_url（done 时）+ warning
- 响应：`{task_id, status, download_url?, error?, warning?}`

**extract_ofd_text**（同步）
- 参数：`file_id: string`，`pages?: string`
- 流程：`requireFileId` -> `FileService.uploadFile` -> **同步**调用 `Ofd2Text.convert()` -> 读取输出文件内容
- 响应：`{text: string}`（直接返回文本，Agent 无需下载）
- 超时：30 秒

**extract_ofd_markdown**（同步）
- 参数：`file_id: string`，`pages?: string`
- 流程：同上，调用 `Ofd2Markdown.convert()` -> 读取 MD 内容
- 响应：`{markdown: string}`
- 超时：30 秒

**list_formats**
- 参数：无
- 流程：返回 `ConvertController.formats()` 同样的分组格式
- 响应：`{ofd: [...], pdf: [...], image: [...]}`

### 同步 extract 的实现细节

- 复用 `Converter.convert(source, outputDir, filename, opts)` 接口，在临时目录同步执行
- 不走 `ConvertService`（避免创建 task + 异步 + 轮询）
- 用 `FileService.createOutputDir` 创建临时输出目录，转换后读取内容，清理临时文件
- pages 选项：传给 `ConvertOptions`；若转换器不支持，extract 工具先不支持，后续扩展

## 5. MCP 日志与 nginx 部署

### MCP 操作日志

spec §9 要求 MCP 工具调用记 `MCP_CALL` 日志（IP、工具名、参数不含文件内容、状态、耗时）。

- **触发时机**：每次 `tools/call` 执行后（成功/失败均记）
- **记录内容**：复用 `LogService.record(OperationType.MCP_CALL, ...)`，`target_format` 字段复用存工具名（如 `upload_file`/`extract_ofd_markdown`），`error_message` 存错误，`duration_ms` 存耗时。`file_id`/`task_id` 按工具填。
- **IP 获取**：`ClientIpInterceptor.extractIp`（与 REST 一致，nginx 透传 XFF）

### nginx 配置更新

现有 `frontend/nginx.conf` 已代理 `/api/` 到 backend。新增 `/mcp` 代理：

```nginx
location /mcp {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    # MCP 同步 extract 可能较慢，放宽超时
    proxy_read_timeout 60s;
}
```

### 关键决策

- **日志字段复用**：`operation_log.target_format` 存 MCP 工具名（不改表结构，避免迁移）。REST 的 CONVERT 日志 target_format 存格式名（pdf/docx），MCP 存工具名（upload_file）--两者都用该字段存「操作目标标识」，语义一致。
- **大文件 body**：MCP `upload_file` 的 base64 content 可能较大，nginx `client_max_body_size 50m` 需应用于 /mcp。
- **无独立 rate limit**：MCP 会话本身有握手门槛，且复用 REST 的 `UploadRateLimiter`（upload_file 工具调用走限流）。

## 6. 测试与验收

### 单元测试

- `McpSessionServiceTest`：initialize/markInitialized/requireInitialized/TTL 过期
- `McpToolRegistryTest`：tools/list 返回 6 个工具、按名分发、未知工具 -32601

### 工具单元测试

- `UploadFileToolTest`：base64 解码 + 上传 -> file_id；超限/类型错误抛 MCP 错误
- `ConvertOfdToolTest`：复用 ConvertService，返回 task_id
- `GetTaskStatusToolTest`：返回状态 + warning
- `ExtractOfdTextToolTest`：同步调用 Ofd2Text，返回文本；非 OFD 报错
- `ExtractOfdMarkdownToolTest`：同步调用 Ofd2Markdown，返回 MD
- `ListFormatsToolTest`：返回分组格式

### 集成测试

- `McpControllerTest`（`@SpringBootTest` + MockMvc）：
  - 完整握手：initialize -> notifications/initialized -> tools/list -> tools/call
  - 未握手调用 tools/call 返回 -32000/-32001
  - JSON-RPC 错误：解析错误、方法不存在、无效参数
  - 通知无响应（202）
  - 真实流程：initialize -> upload_file -> extract_ofd_markdown -> 验证 MD 内容

### 不测试的

- ofd.js / ofdrw 内部（第三方）
- nginx 代理（手动验证）

### 验收标准

- MCP 端点 `POST /mcp` 响应符合 JSON-RPC 2.0
- 完整握手 + 会话校验工作（严格模式）
- 6 个工具全部可用
- `extract_ofd_markdown` 同步返回 MD 内容（Agent 一次调用拿结果）
- MCP_CALL 日志正确记录（工具名、IP、状态、耗时）
- nginx /mcp 代理配置正确

### 已知限制

- `extract_ofd_*` 同步调用，大 OFD 文件可能阻塞请求（30 秒超时保护）
- 会话仅内存，服务重启丢失（Agent 重握手）
- base64 上传 < 10MB 建议，大文件走 REST
- `pages` 参数：若转换器不支持，extract 工具暂不支持（后续扩展）

## 7. 后续扩展（不在 Plan 4 范围）

- 用户认证（接入后会话绑定用户身份）
- Streamable HTTP 传输（MCP 2025 规范，SSE 流）
- 会话持久化（多实例共享）
- `pages` 参数支持（转换器扩展后）
- MCP 资源/prompts（当前仅 tools）
