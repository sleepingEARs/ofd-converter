# Admin Logs Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 OFD Converter 新增受密码保护的管理页面，支持分页查询 operation_log 表记录并筛选。

**Architecture:** 后端新增 AdminController + LogService.queryLogs 方法，使用 JdbcTemplate 构建动态 SQL。前端添加 react-router-dom 实现 /admin 路由，AdminPage 组件包含登录表单和日志表格。

**Tech Stack:** Java 17, Spring Boot 3.3.5, Spring Data JDBC, SQLite, React 19, TypeScript, Ant Design 5, react-router-dom

## Global Constraints

- 后端遵循现有 Controller → Service → Repository 分层
- DTO 使用 Java `record` 类型
- 前端使用 Ant Design 组件，中文 locale
- 密码通过环境变量 `ADMIN_PASSWORD` 配置，无默认值时管理功能不可用
- 前端 token 存储在 sessionStorage，刷新页面免重新登录

---

### Task 1: 后端 — 新增 ErrorCode 和 DTO

**Files:**
- Modify: `backend/src/main/java/com/ofd/converter/model/ErrorCode.java`
- Create: `backend/src/main/java/com/ofd/converter/model/dto/AdminLogsResponse.java`

**Interfaces:**
- Produces: `ErrorCode.UNAUTHORIZED` (HttpStatus.UNAUTHORIZED), `AdminLogsResponse(List<OperationLog> logs, long total, int page, int size)`

- [ ] **Step 1: 在 ErrorCode 中添加 UNAUTHORIZED**

```java
// 在 INTERNAL_ERROR 前添加一行
UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
```

- [ ] **Step 2: 创建 AdminLogsResponse DTO**

```java
package com.ofd.converter.model.dto;

import com.ofd.converter.model.OperationLog;
import java.util.List;

public record AdminLogsResponse(List<OperationLog> logs, long total, int page, int size) {}
```

- [ ] **Step 3: 编译验证**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git add backend/src/main/java/com/ofd/converter/model/ErrorCode.java backend/src/main/java/com/ofd/converter/model/dto/AdminLogsResponse.java
git commit -m "feat: add UNAUTHORIZED error code and AdminLogsResponse DTO"
```

---

### Task 2: 后端 — LogService 新增 queryLogs 方法

**Files:**
- Modify: `backend/src/main/java/com/ofd/converter/service/LogService.java`

**Interfaces:**
- Consumes: `AdminLogsResponse` from Task 1
- Produces: `LogService.queryLogs(int page, int size, String operationType, String status, Long startDate, Long endDate, String search)` → `AdminLogsResponse`

- [ ] **Step 1: 修改 LogService.java，添加 JdbcTemplate 和 queryLogs 方法**

在构造函数中注入 `JdbcTemplate`，添加 `queryLogs` 方法：

```java
package com.ofd.converter.service;

import com.ofd.converter.model.*;
import com.ofd.converter.model.dto.AdminLogsResponse;
import com.ofd.converter.repository.OperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
public class LogService {
    private static final Logger log = LoggerFactory.getLogger(LogService.class);
    private final OperationLogRepository repo;
    private final JdbcTemplate jdbc;
    private final ExecutorService logExecutor;

    private static final RowMapper<OperationLog> ROW_MAPPER = (rs, rowNum) -> {
        OperationLog entry = new OperationLog();
        entry.setId(rs.getString("id"));
        entry.setOperationType(rs.getString("operation_type"));
        entry.setClientIp(rs.getString("client_ip"));
        entry.setFileId(rs.getString("file_id"));
        entry.setTaskId(rs.getString("task_id"));
        entry.setTargetFormat(rs.getString("target_format"));
        entry.setStatus(rs.getString("status"));
        entry.setDurationMs(rs.getLong("duration_ms"));
        entry.setErrorMessage(rs.getString("error_message"));
        entry.setUserAgent(rs.getString("user_agent"));
        entry.setCreatedAt(rs.getLong("created_at"));
        entry.markNotNew();
        return entry;
    };

    public LogService(OperationLogRepository repo, JdbcTemplate jdbc,
                      @Qualifier("logExecutor") ExecutorService logExecutor) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.logExecutor = logExecutor;
    }

    // ... record 方法保持不变 ...

    public AdminLogsResponse queryLogs(int page, int size, String operationType,
                                        String status, Long startDate, Long endDate,
                                        String search) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (operationType != null && !operationType.isBlank()) {
            where.append(" AND operation_type = ?");
            params.add(operationType.toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status.toUpperCase());
        }
        if (startDate != null) {
            where.append(" AND created_at >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            where.append(" AND created_at <= ?");
            params.add(endDate);
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND (client_ip LIKE ? OR file_id LIKE ? OR task_id LIKE ?)");
            String like = "%" + search + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }

        String whereClause = where.toString();
        long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM operation_log WHERE 1=1" + whereClause,
            Long.class, params.toArray());

        int offset = (page - 1) * size;
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(size);
        listParams.add(offset);
        List<OperationLog> logs = jdbc.query(
            "SELECT * FROM operation_log WHERE 1=1" + whereClause + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
            ROW_MAPPER, listParams.toArray());

        return new AdminLogsResponse(logs, total, page, size);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/ofd/converter/service/LogService.java
git commit -m "feat: add queryLogs method to LogService with dynamic filtering"
```

---

### Task 3: 后端 — 创建 AdminController

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/controller/AdminController.java`

**Interfaces:**
- Consumes: `LogService.queryLogs` from Task 2, `ErrorCode.UNAUTHORIZED` from Task 1
- Produces: `GET /api/admin/logs` endpoint

- [ ] **Step 1: 创建 AdminController.java**

```java
package com.ofd.converter.controller;

import com.ofd.converter.model.ErrorCode;
import com.ofd.converter.model.dto.AdminLogsResponse;
import com.ofd.converter.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminController {

    private final LogService logService;

    public AdminController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping("/api/admin/logs")
    public AdminLogsResponse logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operation_type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long start_date,
            @RequestParam(required = false) Long end_date,
            @RequestParam(required = false) String search,
            HttpServletRequest req) {
        checkAuth(req);
        return logService.queryLogs(page, size, operation_type, status,
            start_date, end_date, search);
    }

    private void checkAuth(HttpServletRequest req) {
        String password = System.getenv("ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                "管理功能未配置（ADMIN_PASSWORD 环境变量未设置）", 503);
        }
        String token = req.getHeader("X-Admin-Token");
        if (!password.equals(token)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "密码错误", 401);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/ofd/converter/controller/AdminController.java
git commit -m "feat: add AdminController with auth-protected /api/admin/logs"
```

---

### Task 4: 后端 — 手动验证 API

**Files:** None (manual test)

- [ ] **Step 1: 启动后端（无密码时）**

Run: `cd backend && unset ADMIN_PASSWORD && ./mvnw spring-boot:run &`
Wait for startup, then:
```bash
curl -s http://localhost:8080/api/admin/logs | python3 -m json.tool
```
Expected: 503，错误信息包含 "ADMIN_PASSWORD 环境变量未设置"

Stop backend: `kill %1`

- [ ] **Step 2: 启动后端（有密码时）**

Run: `cd backend && ADMIN_PASSWORD=test123 ./mvnw spring-boot:run &`
Wait for startup.

- [ ] **Step 3: 无 token 请求 → 401**

```bash
curl -s http://localhost:8080/api/admin/logs | python3 -m json.tool
```
Expected: 401，错误信息包含 "密码错误"

- [ ] **Step 4: 正确 token → 200**

```bash
curl -s -H "X-Admin-Token: test123" http://localhost:8080/api/admin/logs | python3 -m json.tool
```
Expected: 200，返回 `{ "logs": [...], "total": N, "page": 1, "size": 20 }`

- [ ] **Step 5: 测试筛选参数**

```bash
curl -s -H "X-Admin-Token: test123" "http://localhost:8080/api/admin/logs?operation_type=CONVERT&status=SUCCESS&page=1&size=5" | python3 -m json.tool
```
Expected: 200，返回筛选后的结果

- [ ] **Step 6: 测试搜索**

```bash
curl -s -H "X-Admin-Token: test123" "http://localhost:8080/api/admin/logs?search=127.0.0.1" | python3 -m json.tool
```
Expected: 200，返回匹配 IP 的记录

Stop backend: `kill %1`

---

### Task 5: 前端 — 安装 react-router-dom，添加类型

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/types/api.ts`

**Interfaces:**
- Produces: `AdminLogsParams` type, `AdminLogsResponse` type, `AdminLogEntry` type in `api.ts`

- [ ] **Step 1: 安装 react-router-dom**

Run: `cd frontend && npm install react-router-dom`
Expected: 成功安装

- [ ] **Step 2: 在 api.ts 中添加 Admin 相关类型**

在 `frontend/src/types/api.ts` 末尾添加：

```typescript
export interface AdminLogsParams {
  page?: number
  size?: number
  operation_type?: string
  status?: string
  start_date?: number
  end_date?: number
  search?: string
}

export interface AdminLogEntry {
  id: string
  operation_type: string
  client_ip: string | null
  file_id: string | null
  task_id: string | null
  target_format: string | null
  status: string
  duration_ms: number | null
  error_message: string | null
  user_agent: string | null
  created_at: number
}

export interface AdminLogsResponse {
  logs: AdminLogEntry[]
  total: number
  page: number
  size: number
}
```

- [ ] **Step 3: 编译验证**

Run: `cd frontend && npx tsc -b --noEmit`
Expected: 无错误

- [ ] **Step 4: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/types/api.ts
git commit -m "feat: add react-router-dom dependency and admin log types"
```

---

### Task 6: 前端 — 扩展 API client

**Files:**
- Modify: `frontend/src/api/client.ts`

**Interfaces:**
- Consumes: `AdminLogsParams`, `AdminLogsResponse` from Task 5
- Produces: `api.adminLogs(params)` → `Promise<AdminLogsResponse>`

- [ ] **Step 1: 添加 adminLogs 方法和 getToken 辅助函数**

在 `client.ts` 的 `api` 对象中添加：

```typescript
import type {
  FormatsResponse, UploadResponse, ConvertRequest, ConvertResponse, TaskResponse,
  AdminLogsParams, AdminLogsResponse,
} from '../types/api'

// ... 现有代码保持不变 ...

function getToken(): string {
  return sessionStorage.getItem('admin_token') || ''
}

export function clearAdminToken(): void {
  sessionStorage.removeItem('admin_token')
}

export const api = {
  // ... 现有方法保持不变 ...

  adminLogs(params: AdminLogsParams = {}): Promise<AdminLogsResponse> {
    const sp = new URLSearchParams()
    if (params.page) sp.set('page', String(params.page))
    if (params.size) sp.set('size', String(params.size))
    if (params.operation_type) sp.set('operation_type', params.operation_type)
    if (params.status) sp.set('status', params.status)
    if (params.start_date) sp.set('start_date', String(params.start_date))
    if (params.end_date) sp.set('end_date', String(params.end_date))
    if (params.search) sp.set('search', params.search)
    return request<AdminLogsResponse>('/admin/logs?' + sp.toString(), {
      headers: { 'X-Admin-Token': getToken() },
    })
  },
}
```

- [ ] **Step 2: 编译验证**

Run: `cd frontend && npx tsc -b --noEmit`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/client.ts
git commit -m "feat: add adminLogs API method with token header"
```

---

### Task 7: 前端 — 创建 AdminPage 组件

**Files:**
- Create: `frontend/src/components/AdminPage.tsx`

**Interfaces:**
- Consumes: `api.adminLogs` from Task 6, `AdminLogEntry`, `AdminLogsResponse` from Task 5
- Produces: `AdminPage` React component

- [ ] **Step 1: 创建 AdminPage.tsx**

```tsx
import { useState, useCallback, useEffect } from 'react'
import {
  Layout, Table, Select, Input, Button, DatePicker, Space, Typography, Form, message,
} from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { api, clearAdminToken } from '../api/client'
import type { AdminLogEntry, AdminLogsResponse } from '../types/api'
import type { ColumnsType } from 'antd/es/table'

const { Title } = Typography
const { RangePicker } = DatePicker

const OPERATION_TYPES = [
  { value: 'UPLOAD', label: '上传' },
  { value: 'CONVERT', label: '转换' },
  { value: 'DOWNLOAD', label: '下载' },
  { value: 'MCP_CALL', label: 'MCP调用' },
]

const STATUS_TYPES = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'TIMEOUT', label: '超时' },
  { value: 'PENDING', label: '处理中' },
]

const COLUMNS: ColumnsType<AdminLogEntry> = [
  { title: '操作类型', dataIndex: 'operation_type', key: 'operation_type', width: 100,
    render: (v: string) => {
      const m: Record<string, string> = { UPLOAD: '上传', CONVERT: '转换', DOWNLOAD: '下载', MCP_CALL: 'MCP' }
      return m[v] || v
    } },
  { title: '状态', dataIndex: 'status', key: 'status', width: 80,
    render: (v: string) => {
      const colors: Record<string, string> = { SUCCESS: '#52c41a', FAILED: '#ff4d4f', TIMEOUT: '#faad14', PENDING: '#1890ff' }
      return <span style={{ color: colors[v] || '#999' }}>{v}</span>
    } },
  { title: '客户端IP', dataIndex: 'client_ip', key: 'client_ip', width: 130 },
  { title: '文件ID', dataIndex: 'file_id', key: 'file_id', width: 120, ellipsis: true },
  { title: '任务ID', dataIndex: 'task_id', key: 'task_id', width: 120, ellipsis: true },
  { title: '目标格式', dataIndex: 'target_format', key: 'target_format', width: 80 },
  { title: '耗时(ms)', dataIndex: 'duration_ms', key: 'duration_ms', width: 90 },
  { title: '错误信息', dataIndex: 'error_message', key: 'error_message', ellipsis: true },
  { title: '时间', dataIndex: 'created_at', key: 'created_at', width: 170,
    render: (v: number) => v ? new Date(v).toLocaleString('zh-CN') : '-' },
]

export function AdminPage() {
  const [authed, setAuthed] = useState(false)
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<AdminLogsResponse | null>(null)
  const [filters, setFilters] = useState<Record<string, string | undefined>>({})
  const [pagination, setPagination] = useState({ page: 1, size: 20 })

  // 检查 sessionStorage 中是否已有 token
  useEffect(() => {
    if (sessionStorage.getItem('admin_token')) {
      setAuthed(true)
    }
  }, [])

  const fetchLogs = useCallback(async (page: number, size: number) => {
    setLoading(true)
    try {
      const res = await api.adminLogs({ page, size, ...filters })
      setData(res)
      setPagination({ page: res.page, size: res.size })
    } catch (e: any) {
      if (e.message?.includes('401') || e.message?.includes('密码')) {
        clearAdminToken()
        setAuthed(false)
        message.error('密码错误或已过期，请重新登录')
      } else {
        message.error('查询失败: ' + (e.message || '未知错误'))
      }
    } finally {
      setLoading(false)
    }
  }, [filters])

  // 登录后自动查询
  useEffect(() => {
    if (authed) {
      fetchLogs(1, 20)
    }
  }, [authed, fetchLogs])

  const handleLogin = async () => {
    if (!password) return
    // 先验证密码
    try {
      const res = await fetch('/api/admin/logs?page=1&size=1', {
        headers: { 'X-Admin-Token': password },
      })
      if (res.ok) {
        sessionStorage.setItem('admin_token', password)
        setAuthed(true)
        setPassword('')
      } else {
        message.error('密码错误')
      }
    } catch {
      message.error('网络错误')
    }
  }

  const handleSearch = () => {
    fetchLogs(1, pagination.size)
  }

  const handleTableChange = (pag: { current?: number; pageSize?: number }) => {
    fetchLogs(pag.current || 1, pag.pageSize || 20)
  }

  const handleLogout = () => {
    clearAdminToken()
    setAuthed(false)
    setData(null)
  }

  // 未认证 → 登录表单
  if (!authed) {
    return (
      <Layout style={{ minHeight: '100vh', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <div style={{ width: 320, textAlign: 'center' }}>
          <Title level={4}>管理页面登录</Title>
          <Input.Password
            placeholder="请输入管理密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onPressEnter={handleLogin}
            style={{ marginBottom: 16 }}
          />
          <Button type="primary" onClick={handleLogin} block>登录</Button>
        </div>
      </Layout>
    )
  }

  // 已认证 → 日志列表
  return (
    <Layout style={{ minHeight: '100vh', padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>转换日志</Title>
        <Space>
          <Button onClick={handleLogout}>退出</Button>
        </Space>
      </div>

      {/* 筛选栏 */}
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          placeholder="操作类型"
          allowClear
          style={{ width: 120 }}
          options={OPERATION_TYPES}
          value={filters.operation_type || undefined}
          onChange={(v) => setFilters((f) => ({ ...f, operation_type: v }))}
        />
        <Select
          placeholder="状态"
          allowClear
          style={{ width: 100 }}
          options={STATUS_TYPES}
          value={filters.status || undefined}
          onChange={(v) => setFilters((f) => ({ ...f, status: v }))}
        />
        <RangePicker
          showTime
          placeholder={['开始时间', '结束时间']}
          onChange={(dates) => {
            setFilters((f) => ({
              ...f,
              start_date: dates?.[0] ? String(dates[0].valueOf()) : undefined,
              end_date: dates?.[1] ? String(dates[1].valueOf()) : undefined,
            }))
          }}
        />
        <Input
          placeholder="搜索 IP / 文件ID / 任务ID"
          allowClear
          style={{ width: 240 }}
          prefix={<SearchOutlined />}
          value={filters.search || ''}
          onChange={(e) => setFilters((f) => ({ ...f, search: e.target.value || undefined }))}
          onPressEnter={handleSearch}
        />
        <Button type="primary" onClick={handleSearch} loading={loading}>查询</Button>
      </Space>

      <Table
        rowKey="id"
        columns={COLUMNS}
        dataSource={data?.logs || []}
        loading={loading}
        pagination={{
          current: pagination.page,
          pageSize: pagination.size,
          total: data?.total || 0,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
        onChange={handleTableChange}
        scroll={{ x: 1100 }}
        size="small"
      />
    </Layout>
  )
}

export default AdminPage
```

- [ ] **Step 2: 编译验证**

Run: `cd frontend && npx tsc -b --noEmit`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/AdminPage.tsx
git commit -m "feat: add AdminPage component with login form and log table"
```

---

### Task 8: 前端 — 修改 App.tsx 添加路由

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/main.tsx`

**Interfaces:**
- Consumes: `AdminPage` from Task 7

- [ ] **Step 1: 修改 main.tsx，添加 BrowserRouter**

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { App } from './App'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ConfigProvider locale={zhCN}>
        <App />
      </ConfigProvider>
    </BrowserRouter>
  </StrictMode>,
)
```

- [ ] **Step 2: 修改 App.tsx，使用 Routes 拆分页面**

将现有的转换工具 UI 提取为 `ConverterPage` 组件，`App` 组件改为路由容器：

```tsx
import { useState, useCallback } from 'react'
import { Routes, Route, Link } from 'react-router-dom'
import { Layout, Typography, message, Button } from 'antd'
import { SettingOutlined } from '@ant-design/icons'
import JSZip from 'jszip'
import { UploadZone } from './components/UploadZone'
import { FileList } from './components/FileList'
import { PreviewPanel } from './components/PreviewPanel'
import { ConvertOptions } from './components/ConvertOptions'
import { TaskList } from './components/TaskList'
import { AdminPage } from './components/AdminPage'
import { useConvert } from './hooks/useConvert'
import { useTaskPolling } from './hooks/useTaskPolling'
import { api } from './api/client'
import type { FileItem, TaskItem } from './types/api'

const { Header, Content } = Layout
const { Title } = Typography
const MAX_TOTAL_FILES = 50

function ConverterPage() {
  const [files, setFiles] = useState<FileItem[]>([])
  const [selectedFileId, setSelectedFileId] = useState<string | null>(null)
  const [tasks, setTasks] = useState<TaskItem[]>([])
  const [checkedIds, setCheckedIds] = useState<Set<string>>(new Set())
  const { convert, converting } = useConvert()

  const selectedFile = files.find((f) => f.file_id === selectedFileId) ?? null

  const onUpdateTask = useCallback((t: TaskItem) => {
    setTasks((prev) => prev.map((x) => (x.task_id === t.task_id ? t : x)))
  }, [])
  useTaskPolling(tasks, onUpdateTask)

  const handleUploaded = useCallback((f: FileItem) => {
    setFiles((prev) => {
      if (prev.length >= MAX_TOTAL_FILES) {
        message.warning({ content: `最多同时上传 ${MAX_TOTAL_FILES} 个文件，请先删除部分文件`, duration: 3 })
        return prev
      }
      return [...prev, f]
    })
  }, [])

  const handleDelete = useCallback((fileId: string) => {
    setFiles((prev) => prev.filter((f) => f.file_id !== fileId))
    setSelectedFileId((prev) => (prev === fileId ? null : prev))
    setCheckedIds((prev) => { const n = new Set(prev); n.delete(fileId); return n })
  }, [])

  const handleBatchDelete = useCallback(() => {
    setFiles((prev) => prev.filter((f) => !checkedIds.has(f.file_id)))
    setSelectedFileId((prev) => (prev && checkedIds.has(prev) ? null : prev))
    setCheckedIds(new Set())
  }, [checkedIds])

  const handleToggleCheck = useCallback((fileId: string) => {
    setCheckedIds((prev) => {
      const n = new Set(prev)
      if (n.has(fileId)) n.delete(fileId); else n.add(fileId)
      return n
    })
  }, [])

  const handleToggleAll = useCallback(() => {
    setCheckedIds((prev) => {
      if (prev.size === files.length) return new Set()
      return new Set(files.map((f) => f.file_id))
    })
  }, [files])

  const allChecked = files.length > 0 && checkedIds.size === files.length

  const handleConvert = useCallback(async (targetFormat: string) => {
    if (checkedIds.size > 0) {
      const checkedFiles = files.filter((f) => checkedIds.has(f.file_id))
      if (checkedFiles.length === 0) return
      const sourceTypes = new Set(checkedFiles.map((f) => f.source_type))
      if (sourceTypes.size > 1) {
        message.warning('所选文件包含不同源格式，批量转换可能产生不一致结果。建议按源格式分组转换。')
      }
      message.loading({ content: `正在批量转换 ${checkedFiles.length} 个文件...`, key: 'batch', duration: 0 })
      const taskIds: string[] = []
      for (const f of checkedFiles) {
        const task = await convert(f.file_id, f.filename, targetFormat)
        if (task) { taskIds.push(task.task_id); setTasks((prev) => [task, ...prev]) }
      }
      const maxWait = 300
      for (let i = 0; i < maxWait; i++) {
        const statuses = await Promise.all(taskIds.map(async (tid) => {
          try { const res = await fetch(`/api/task/${tid}`); const d = await res.json(); return d.status as string }
          catch { return 'pending' }
        }))
        if (statuses.every((s) => s === 'done' || s === 'failed' || s === 'timeout')) break
        await new Promise((r) => setTimeout(r, 2000))
      }
      const zip = new JSZip()
      let successCount = 0
      const usedNames = new Set<string>()
      for (const tid of taskIds) {
        try {
          const res = await fetch(api.downloadUrl(tid))
          if (!res.ok) continue
          const blob = await res.blob()
          const cd = res.headers.get('Content-Disposition') || ''
          const m = cd.match(/filename\*=UTF-8''([^;]+)/) || cd.match(/filename="?([^";]+)"?/)
          let name = m ? decodeURIComponent(m[1]) : `${tid}`
          if (usedNames.has(name)) {
            const dotIdx = name.lastIndexOf('.')
            const base = dotIdx > 0 ? name.substring(0, dotIdx) : name
            const ext = dotIdx > 0 ? name.substring(dotIdx) : ''
            name = `${base}_${tid.slice(0, 8)}${ext}`
          }
          usedNames.add(name)
          zip.file(name, blob)
          successCount++
        } catch { /* skip */ }
      }
      if (successCount === 0) { message.error({ content: '批量转换失败，无可用文件', key: 'batch' }); return }
      const zipBlob = await zip.generateAsync({ type: 'blob' })
      const ts = new Date()
      const tsStr = `${ts.getFullYear()}${String(ts.getMonth() + 1).padStart(2, '0')}${String(ts.getDate()).padStart(2, '0')}_${String(ts.getHours()).padStart(2, '0')}${String(ts.getMinutes()).padStart(2, '0')}${String(ts.getSeconds()).padStart(2, '0')}`
      const zipName = `batch_${targetFormat}_${tsStr}.zip`
      const url = URL.createObjectURL(zipBlob)
      const a = document.createElement('a')
      a.href = url; a.download = zipName; document.body.appendChild(a); a.click(); document.body.removeChild(a)
      URL.revokeObjectURL(url)
      message.success({ content: `批量转换完成，已下载 ${successCount} 个文件（${zipName}）`, key: 'batch' })
      setCheckedIds(new Set())
      return
    }
    if (!selectedFile) return
    const task = await convert(selectedFile.file_id, selectedFile.filename, targetFormat)
    if (task) setTasks((prev) => [task, ...prev])
  }, [selectedFile, convert, checkedIds, files])

  const handleDownload = useCallback(async (taskId: string) => {
    try {
      const res = await fetch(api.downloadUrl(taskId))
      if (!res.ok) throw new Error('下载失败')
      const blob = await res.blob()
      const cd = res.headers.get('Content-Disposition') || ''
      const m = cd.match(/filename\*=UTF-8''([^;]+)/) || cd.match(/filename="?([^";]+)"?/)
      const filename = m ? decodeURIComponent(m[1]) : taskId
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url; a.download = filename; document.body.appendChild(a); a.click(); document.body.removeChild(a)
      URL.revokeObjectURL(url)
    } catch (e) { console.error('download failed', e) }
  }, [])

  return (
    <>
      <UploadZone onUploaded={handleUploaded} />
      <div style={{ display: 'flex', gap: 16, marginTop: 16 }}>
        <div style={{ flex: 1 }}>
          <FileList files={files} selectedFileId={selectedFileId} onSelect={setSelectedFileId}
            onDelete={handleDelete} checkedIds={checkedIds} onToggleCheck={handleToggleCheck}
            onToggleAll={handleToggleAll} allChecked={allChecked} onBatchDelete={handleBatchDelete} />
        </div>
        <div style={{ flex: 2 }}><PreviewPanel file={selectedFile} /></div>
      </div>
      <div style={{ marginTop: 16 }}>
        <ConvertOptions selectedFile={selectedFile} onConvert={handleConvert} converting={converting} checkedCount={checkedIds.size} />
      </div>
      <div style={{ marginTop: 16 }}><TaskList tasks={tasks} onDownload={handleDownload} /></div>
    </>
  )
}

export function App() {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={3} style={{ color: 'white', margin: '12px 0' }}>OFD 转换工具</Title>
        <Link to="/admin">
          <Button type="text" icon={<SettingOutlined />} style={{ color: 'white' }}>管理</Button>
        </Link>
      </Header>
      <Content style={{ padding: 24, maxWidth: 1000, margin: '0 auto', width: '100%' }}>
        <Routes>
          <Route path="/" element={<ConverterPage />} />
          <Route path="/admin" element={<AdminPage />} />
        </Routes>
      </Content>
    </Layout>
  )
}

export default App
```

- [ ] **Step 2: 编译验证**

Run: `cd frontend && npx tsc -b --noEmit`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add frontend/src/App.tsx frontend/src/main.tsx
git commit -m "feat: add React Router with / and /admin routes"
```

---

### Task 9: 前端 — 端到端验证

**Files:** None (manual test)

- [ ] **Step 1: 启动后端和前端**

```bash
# Terminal 1
cd backend && ADMIN_PASSWORD=test123 ./mvnw spring-boot:run

# Terminal 2
cd frontend && npm run dev
```

- [ ] **Step 2: 访问主页**

打开 `http://localhost:5173/` → 确认转换工具页面正常显示，Header 中有"管理"按钮

- [ ] **Step 3: 访问管理页面**

点击"管理"按钮或直接访问 `http://localhost:5173/admin` → 确认显示密码登录表单

- [ ] **Step 4: 测试错误密码**

输入错误密码，点击登录 → 确认显示"密码错误"提示

- [ ] **Step 5: 测试正确密码**

输入正确密码 (`test123`)，点击登录 → 确认跳转到日志列表页面

- [ ] **Step 6: 测试筛选**

验证操作类型下拉、状态下拉、日期范围选择器、搜索框都能正常工作，点击查询按钮后数据更新

- [ ] **Step 7: 测试分页**

切换页码和每页条数 → 确认数据正确加载

- [ ] **Step 8: 测试刷新保持登录**

刷新页面 → 确认无需重新登录，直接显示日志列表

- [ ] **Step 9: 测试退出**

点击"退出"按钮 → 确认回到登录表单

- [ ] **Step 10: Commit (if any fixes)**

```bash
git add -A
git commit -m "fix: admin page verification fixes"
```