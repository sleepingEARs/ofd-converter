# OFD Converter

基于 Web 的 OFD（GB/T 33190-2016）文件格式转换工具。

## 范围

- OFD -> PDF / PNG / JPG / 文本 / DOCX / Markdown
- PDF / 图片 -> OFD
- OFD 文件在线预览（ofd.js 纯前端渲染）
- REST API：上传 / 转换 / 查询 / 下载 / 格式列表 / 健康检查
- 操作日志（基于 IP）、定时清理、文件保留期可配置、按 IP 限流
- Docker Compose 一键部署（前后端）

后续计划：
- Plan 4：MCP 协议端点（AI Agent 调用）

## 本地开发

### 后端

```bash
cd backend
mvn test            # 运行所有测试
mvn spring-boot:run # 启动后端（:8080）
```

### 前端

```bash
cd frontend
npm install
npm run dev      # Vite dev server（:5173），代理 /api -> localhost:8080
npm test         # Vitest
npm run build    # 构建到 dist/
```

开发时需同时启动后端（`cd backend && mvn spring-boot:run`）。

## 一键部署（推荐）

仅依赖 Docker，执行：

```bash
./deploy.sh
```

脚本会交互式询问以下配置（均有默认值，可直接回车）：

- **HTTP 端口**：默认 `80`
- **数据目录**：默认 `./data`
- **管理员口令**：默认随机生成 16 位字符

配置会保存到 `.env` 文件。后续再次运行 `./deploy.sh` 会沿用已有配置，也可重新输入。

部署成功后输出访问地址，例如：

```
Frontend:     http://localhost
Admin page:   http://localhost/admin
API base:     http://localhost/api/
Health check: http://localhost/health
```

### 更新部署

拉取最新代码后，重新执行：

```bash
./deploy.sh
```

脚本会自动重新构建镜像并重启容器，数据目录中的数据会保留。

### 手动修改配置

直接编辑 `.env` 文件，然后运行：

```bash
docker compose down
docker compose up -d
```

## 配置（环境变量）

| 变量 | 默认 | 说明 |
|---|---|---|
| `OFD_DATA_DIR` | `/data` | 数据目录（上传/输出/数据库） |
| `OFD_DB_PATH` | `/data/converter.db` | SQLite 路径 |
| `FILE_RETENTION_HOURS` | `24` | 文件保留小时数 |
| `LOG_RETENTION_DAYS` | `90` | 日志保留天数 |
| `JAVA_OPTS` | `-Xmx768m` | JVM 堆参数 |

## API

| 端点 | 方法 | 说明 |
|---|---|---|
| `/api/upload` | POST (multipart) | 上传文件 -> `{file_id, filename, size, source_type}` |
| `/api/convert` | POST | `{file_id, target_format, options?}` -> `{task_id, status}` |
| `/api/task/{task_id}` | GET | 查询任务状态 -> `{task_id, status, download_url?, error?, warning?}` |
| `/api/download/{task_id}` | GET | 下载转换结果（文件流） |
| `/api/formats` | GET | 按源格式分组的目标格式 |
| `/health` | GET | `{status: "ok"}`（Docker healthcheck） |

**`target_format` 取值：** `pdf` | `png` | `jpg` | `txt` | `docx` | `md` | `ofd`

反向转换：上传 PDF/图片后，`target_format: "ofd"` 触发 -> OFD。

JSON 字段使用 snake_case。状态值小写：`pending` / `processing` / `done` / `failed` / `timeout`。

`warning` 字段：OFD->DOCX/MD 等有损转换返回提示文案，其他转换为 null。

## 错误响应

```json
{ "error": { "code": "INVALID_FILE_TYPE", "message": "不支持的文件类型", "details": {} } }
```

错误码：`INVALID_FILE_TYPE` (400)、`FILE_TOO_LARGE` (400)、`INVALID_REQUEST` (400)、`TASK_NOT_FOUND` (404)、`FILE_EXPIRED` (410)、`TASK_FAILED` (409)、`TASK_TIMEOUT` (408)、`STORAGE_FULL` (503)、`TOO_MANY_REQUESTS` (429)、`INTERNAL_ERROR` (500)。

## 约束

- 单文件 ≤ 50MB；单任务转换超时 5 分钟；最多 4 个并行转换
- 上传限流：单 IP 每分钟 20 次
- 磁盘：使用率 ≥ 95% 拒绝新上传
- 文件类型按魔数校验（非扩展名）

## 设计文档

- 设计 spec：`docs/superpowers/specs/2026-07-05-ofd-converter-design.md`
- Plan 2 设计：`docs/superpowers/specs/2026-07-06-ofd-converter-plan-2-design.md`
- Plan 3 设计：`docs/superpowers/specs/2026-07-06-ofd-converter-plan-3-design.md`
- Plan 4 设计：`docs/superpowers/specs/2026-07-11-ofd-converter-plan-4-design.md`
- PoC 发现：`docs/superpowers/pocs/2026-07-05-ofd-converter-poc-findings.md`
- 实施计划：`docs/superpowers/plans/`（plan-1/2/3/4）

## MCP（AI Agent 调用）

端点 `POST /mcp`（JSON-RPC 2.0 over HTTP）。完整握手流程：
1. `initialize` -> 获得 `Mcp-Session-Id` 头
2. `notifications/initialized`（通知，无 id，返回 202）
3. `tools/list` / `tools/call`（需带 `Mcp-Session-Id` 头）

会话 30 分钟无活动过期。协议版本 `2025-06-18`。

**工具：**

| 工具 | 说明 | 返回 |
|------|------|------|
| `upload_file` | base64 上传文件（< 10MB，大文件走 REST） | `{file_id, filename, size, source_type}` |
| `convert_ofd` | 异步转换，返回 task_id | `{task_id, status, warning?}` |
| `get_task_status` | 查询任务状态 | `{task_id, status, download_url?, error?, warning?}` |
| `extract_ofd_text` | 同步提取 OFD 文本，直接返回内容 | `{text}` |
| `extract_ofd_markdown` | 同步提取 OFD 为 Markdown，直接返回（供 Agent 消费） | `{markdown}` |
| `list_formats` | 列出支持的转换格式 | `{ofd:[...], pdf:[...], image:[...]}` |

`extract_ofd_*` 同步调用，30 秒超时，Agent 一次调用拿结果（无需轮询）。`convert_ofd` 为异步，用 `get_task_status` 轮询。

**示例（提取 Markdown）：**
```
1. POST /mcp  {"method":"initialize"}           -> Mcp-Session-Id: <sid>
2. POST /mcp  {"method":"notifications/initialized"}  (header: Mcp-Session-Id: <sid>) -> 202
3. POST /mcp  {"method":"tools/call","params":{"name":"upload_file","arguments":{"filename":"a.ofd","content":"<base64>"}}}  -> file_id
4. POST /mcp  {"method":"tools/call","params":{"name":"extract_ofd_markdown","arguments":{"file_id":"<id>"}}}  -> {markdown: "..."}
```
