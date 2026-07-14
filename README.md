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

仅需一台安装好 Docker 的服务器，即可一键部署。

### 前提条件

- 安装 Docker 20.10+ 和 Docker Compose（v2 插件或 standalone）
- 开放一个对外访问的端口（默认 `80`）

### 部署步骤

```bash
# 1. 克隆仓库
git clone https://github.com/sleepingEARs/ofd-converter.git
cd ofd-converter

# 2. 执行一键部署脚本
./deploy.sh
```

脚本会交互式询问以下配置（均有默认值，可直接回车）：

- **HTTP 端口**：默认 `80`
- **数据目录**：默认 `./data`
- **管理员口令**：默认随机生成 16 位字符

配置会保存到 `.env` 文件。首次构建会下载 Maven 和 npm 依赖，可能需要几分钟。

### 访问应用

部署成功后，根据你设置的端口访问：

```text
前端页面:     http://<服务器IP>:<端口>
管理后台:     http://<服务器IP>:<端口>/admin
API 接口:     http://<服务器IP>:<端口>/api/
健康检查:     http://<服务器IP>:<端口>/health
```

例如使用默认端口 `80`：

```text
http://<服务器IP>
http://<服务器IP>/admin
```

管理员口令在 `.env` 文件中查看：

```bash
cat .env
```

### 防火墙放行

如果服务器启用了防火墙，需要放行对应端口（以 Ubuntu UFW 为例）：

```bash
# 默认 80 端口
sudo ufw allow 80/tcp

# 如果部署时使用了自定义端口，例如 8080
sudo ufw allow 8080/tcp
```

### 更新部署

拉取最新代码后，重新执行：

```bash
git pull
cd ofd-converter
./deploy.sh
```

脚本会自动重新构建镜像并重启容器，`data/` 目录中的数据会保留。

### 手动修改配置

直接编辑 `.env` 文件，然后运行：

```bash
docker compose down
docker compose up -d
```

### 查看日志

```bash
# 后端日志
docker compose logs -f backend

# 前端日志
docker compose logs -f frontend
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
