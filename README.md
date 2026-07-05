# OFD Converter

基于 Web 的 OFD（GB/T 33190-2016）文件格式转换工具——后端 MVP。

## 范围（Plan 1）

- OFD → PDF / PNG / JPG / 文本
- PDF → OFD、图片 → OFD
- REST API：上传 / 转换 / 查询 / 下载 / 格式列表 / 健康检查
- 操作日志（基于 IP）、定时清理、文件保留期可配置、按 IP 限流
- Docker Compose 部署

后续计划（独立 plan）：
- Plan 2：OFD→DOCX、OFD→Markdown、DOCX→OFD 生产转换器（PoC 已完成，见 `docs/superpowers/pocs/2026-07-05-ofd-converter-poc-findings.md`）
- Plan 3：前端（React + ofd.js 预览）
- Plan 4：MCP 协议端点

## 本地开发

```bash
cd backend
mvn test            # 运行所有测试（36 个）
mvn spring-boot:run # 启动后端（:8080）
```

## Docker 部署

```bash
docker compose up --build -d
curl http://localhost:8080/health   # {"status":"ok"}
```

> Docker 构建未在开发环境验证（无 Docker）。首次构建会下载 Maven 依赖，较慢。

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
| `/api/upload` | POST (multipart) | 上传文件 → `{file_id, filename, size, source_type}` |
| `/api/convert` | POST | `{file_id, target_format, options?}` → `{task_id, status}` |
| `/api/task/{task_id}` | GET | 查询任务状态 → `{task_id, status, download_url?, error?}` |
| `/api/download/{task_id}` | GET | 下载转换结果（文件流） |
| `/api/formats` | GET | 按源格式分组的目标格式 |
| `/health` | GET | `{status: "ok"}`（Docker healthcheck） |

**`target_format` 取值：** `pdf` | `png` | `jpg` | `txt` | `ofd`（`docx`/`md` 见 Plan 2）

反向转换：上传 PDF/图片/DOCX 后，`target_format: "ofd"` 触发 → OFD。

JSON 字段使用 snake_case。状态值小写：`pending` / `processing` / `done` / `failed` / `timeout`。

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
- PoC 发现：`docs/superpowers/pocs/2026-07-05-ofd-converter-poc-findings.md`
- 实施计划：`docs/superpowers/plans/2026-07-05-ofd-converter-plan-1.md`
