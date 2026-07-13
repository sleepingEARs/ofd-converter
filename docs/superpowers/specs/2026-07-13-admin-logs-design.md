# Admin Logs Page Design

**Date**: 2026-07-13
**Status**: Approved

## Context

OFD Converter 当前没有管理页面，无法查看历史转换记录。需要新增一个受密码保护的管理页面，支持查询 `operation_log` 表的分页记录和筛选。

## Requirements

1. 后端：新增 `/api/admin/logs` 分页查询 API（查 `operation_log` 表）
2. 前端：新增 `/admin` 路由页面（日志列表 + 筛选）
3. 认证：管理页面需密码保护（环境变量 `ADMIN_PASSWORD` + token 头）

## Design

### Backend

#### 1. AdminController (`controller/AdminController.java`)

- `GET /api/admin/logs` — 分页 + 筛选查询
- 查询参数：
  - `page` (int, default 1)
  - `size` (int, default 20)
  - `operation_type` (String, optional) — UPLOAD / CONVERT / DOWNLOAD / MCP_CALL
  - `status` (String, optional) — SUCCESS / FAILED / TIMEOUT / PENDING
  - `start_date` (Long, optional) — 起始时间戳
  - `end_date` (Long, optional) — 结束时间戳
  - `search` (String, optional) — 模糊匹配 client_ip / file_id / task_id
- 认证：读取 `ADMIN_PASSWORD` 环境变量，校验请求头 `X-Admin-Token`，不匹配返回 401
- 响应：`{ logs: [...], total: long, page: int, size: int }`

#### 2. OperationLogRepository 扩展

新增自定义查询方法，使用 `@Query` 注解实现分页和筛选。由于 Spring Data JDBC 不支持动态查询，采用 `JdbcTemplate` 在 Service 层构建动态 SQL。

#### 3. LogService 扩展

新增 `queryLogs(...)` 方法，使用 `JdbcTemplate` 构建动态 SQL：
- 基础 `SELECT` + 动态 `WHERE` 子句（根据非空筛选参数拼接）
- `COUNT(*)` 查询获取总数
- `LIMIT ? OFFSET ?` 分页

#### 4. DTO

- `AdminLogsResponse` (record): `List<OperationLog> logs`, `long total`, `int page`, `int size`

### Frontend

#### 1. 路由

- 添加 `react-router-dom` 依赖
- `BrowserRouter` 包裹应用，两个路由：
  - `/` → 现有转换工具页面
  - `/admin` → 管理页面

#### 2. AdminPage 组件 (`components/AdminPage.tsx`)

两种状态：

**未认证状态**：
- 密码输入框 + 提交按钮
- 提交时调用 `/api/admin/logs?page=1&size=1` 带上 `X-Admin-Token`
- 200 → 存储 token 到 `sessionStorage`，切换到日志视图
- 401 → 显示错误提示

**已认证状态**：
- 顶部筛选栏：操作类型 Select、状态 Select、日期 RangePicker、搜索 Input
- Ant Design `Table` 组件，服务端分页
- 列：操作类型、状态、客户端IP、文件名/任务ID、目标格式、耗时、错误信息、时间
- 查询按钮触发 API 调用

#### 3. API 客户端扩展 (`api/client.ts`)

新增 `adminLogs(params)` 方法：
- 拼接查询参数
- 添加 `X-Admin-Token` 请求头（从 `sessionStorage` 读取）
- 401 时清除 token 并跳回登录状态

#### 4. App.tsx 修改

- 引入 `BrowserRouter`、`Routes`、`Route`
- `/` 路由渲染现有转换工具 UI
- `/admin` 路由渲染 `AdminPage`
- Header 中添加导航链接

### Auth Flow

```
用户访问 /admin → 显示密码表单 → 输入密码
→ GET /api/admin/logs?page=1&size=1 (Header: X-Admin-Token)
→ 后端比对 ADMIN_PASSWORD 环境变量
→ 200: 前端存储 token 到 sessionStorage，显示日志列表
→ 401: 前端显示"密码错误"
```

### Environment Variable

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `ADMIN_PASSWORD` | （无默认值，未设置时管理功能不可用） | 管理页面密码 |

### Files to Create/Modify

**Backend (new):**
- `backend/src/main/java/com/ofd/converter/controller/AdminController.java`
- `backend/src/main/java/com/ofd/converter/model/dto/AdminLogsResponse.java`

**Backend (modify):**
- `backend/src/main/java/com/ofd/converter/repository/OperationLogRepository.java` — 添加分页查询方法
- `backend/src/main/java/com/ofd/converter/service/LogService.java` — 添加 queryLogs 方法

**Frontend (new):**
- `frontend/src/components/AdminPage.tsx`

**Frontend (modify):**
- `frontend/src/App.tsx` — 添加路由
- `frontend/src/api/client.ts` — 添加 adminLogs 方法
- `frontend/src/types/api.ts` — 添加 AdminLog 类型
- `frontend/package.json` — 添加 react-router-dom 依赖

## Verification

1. 启动后端，设置 `ADMIN_PASSWORD=test123`
2. 不带 token 访问 `/api/admin/logs` → 401
3. 带正确 token 访问 → 200，返回分页数据
4. 测试筛选参数：`operation_type=CONVERT`、`status=SUCCESS`、`start_date`/`end_date`、`search`
5. 前端：访问 `/admin` → 密码表单 → 输入错误密码 → 提示错误
6. 前端：输入正确密码 → 显示日志列表 → 测试筛选和分页
7. 前端：刷新页面 → token 仍在 sessionStorage → 无需重新登录