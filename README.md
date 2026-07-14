# OFD Converter

基于 Web 的 OFD（GB/T 33190-2016）文件格式转换工具。

## 范围

- OFD -> PDF / PNG / JPG / 文本 / DOCX / Markdown
- PDF / 图片 -> OFD
- OFD 文件在线预览（服务端渲染为 PNG）
- REST API：上传 / 转换 / 查询 / 下载 / 格式列表 / 健康检查
- 操作日志（基于 IP）、定时清理、文件保留期可配置、按 IP 限流
- 管理后台：转换日志查询
- Docker Compose 一键部署（前后端）

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

## 测试样本声明

`backend/src/test/resources/test-ofd/` 目录下的 OFD 文件来自 [OFDRW](https://github.com/ofdrw/ofdrw) 官方测试集，仅用于功能测试。这些文件为模拟样本，不包含真实个人或企业信息。

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).

See [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md) for third-party licenses.
