# OFD 转换工具 - Plan 3 设计文档（前端）

> 创建日期：2026-07-06
> 状态：设计阶段
> 依赖：Plan 1（后端 REST API）+ Plan 2（warning 字段 + DOCX/MD 转换）已完成

## 1. 范围与项目结构

### 范围

- 单页应用（React 19 + TypeScript + Vite + Ant Design）
- 上传（拖拽+多文件）-> 在线预览（ofd.js）-> 选格式转换 -> 轮询状态 -> 下载
- 有损提示弹窗（OFD->DOCX/MD，对接 Plan 2 的 `warning` 字段）
- ofd.js + React 19 集成 PoC（前置必做）

### 项目结构

```
ofd-converter/frontend/
├── index.html
├── package.json
├── tsconfig.json / tsconfig.app.json / tsconfig.node.json
├── vite.config.ts                    # dev 代理 /api -> backend:8080
├── public/
└── src/
    ├── main.tsx                      # 入口，挂载 AntD ConfigProvider
    ├── App.tsx                       # 单页布局
    ├── api/
    │   └── client.ts                 # REST API 封装（fetch）
    ├── types/
    │   ├── api.ts                    # API 响应类型
    │   └── ofdjs.d.ts                # ofd.js 类型声明（自写）
    ├── hooks/
    │   ├── useUpload.ts
    │   ├── useConvert.ts
    │   ├── useTaskPolling.ts
    │   └── usePreview.ts             # ofd.js 预览逻辑
    ├── components/
    │   ├── UploadZone.tsx
    │   ├── FileList.tsx
    │   ├── PreviewPanel.tsx
    │   ├── ConvertOptions.tsx
    │   ├── TaskList.tsx
    │   └── LossyWarningModal.tsx     # 有损提示弹窗
    └── styles.css                    # 极少量补充样式（AntD 为主）
```

### 对接后端 API（Plan 1/2 已实现）

| 端点 | 用途 |
|------|------|
| `POST /api/upload` | 上传文件 |
| `POST /api/convert` | 发起转换 |
| `GET /api/task/{id}` | 轮询状态（含 `warning` 字段） |
| `GET /api/download/{id}` | 下载结果 |
| `GET /api/formats` | 获取可用格式（按源格式分组） |

### 与后端的运行关系

- 开发时：Vite dev server 代理 `/api` -> `localhost:8080`（vite.config.ts 配置 proxy）
- 部署时：nginx 服务前端静态资源 + 反向代理 `/api`（Plan 1 docker-compose 已为 nginx 预留，Plan 3 补充 nginx.conf + frontend Dockerfile）

## 2. ofd.js + React 19 集成 PoC（前置）

spec §11 要求前端第一步必须做 ofd.js 集成 PoC。这是 Plan 3 的前置任务，结果决定预览实现方式。

### PoC 目标

验证 ofd.js 能在 React 19 + Vite + TypeScript 环境中：
1. 成功 `import`（或作为全局脚本加载）
2. 解析一个真实 OFD 文件（`parseOfdDocument`）
3. 渲染页面到 DOM（`renderOfd`/`renderOfdByIndex`）
4. 在 React 组件 `useEffect` 中安全调用（不与 React 生命周期冲突）

### PoC 步骤

1. `npm create vite@latest frontend -- --template react-ts`
2. `npm i ofd.js antd`
3. 写最小 PoC 组件 `PreviewPoc.tsx`：
   - 文件输入接受 .ofd
   - `useRef` 持有渲染容器 div
   - `useEffect` 中调用 ofd.js 解析 + 渲染
   - 渲染结果 append 到 ref 容器
4. 用 Plan 1 的 `Fixtures.ofdWithHeadings` 生成的 OFD 文件测试（或任意真实 OFD）
5. 自写 `types/ofdjs.d.ts` 类型声明（ofd.js 无官方类型）

### PoC 验证点

- ofd.js 是否能 `npm i` 正常安装（Vue 依赖是否冲突）
- `parseOfdDocument`/`renderOfd` 的实际 API 形态（参数、回调、返回值）
- 渲染产物是 SVG/Canvas/DOM 节点？（决定如何挂载）
- 是否有内存泄漏（切换文件时清理上一次渲染）

### PoC 结果分支

| PoC 结果 | 决策 |
|---------|------|
| ofd.js 可正常集成 + 渲染 | 采用 ofd.js 纯前端预览（第 4 节预览面板） |
| ofd.js 安装/调用失败，但可通过 `<script>` 全局加载 | 改用全局加载方式（index.html 引入 ofd.js） |
| 完全无法集成 | 降级到 OFD->HTML 服务端渲染（需后端补 HTML 预览端点，回归 spec §5 备注方案） |

### PoC 产出

- `PreviewPoc.tsx`（保留为预览组件的基础）
- `types/ofdjs.d.ts`（类型声明）
- PoC 发现代码注释（实际 API 形态、渲染产物类型）

## 3. API 客户端与类型定义

### API 类型（`types/api.ts`）

```typescript
type FormatsResponse = Record<string, string[]>;
// 例: { ofd: ["pdf","png","jpg","txt","docx","md"], pdf: ["ofd"], image: ["ofd"] }

interface UploadResponse {
  file_id: string;
  filename: string;
  size: number;
  source_type: 'OFD' | 'PDF' | 'IMAGE' | 'DOCX';
}

interface ConvertRequest {
  file_id: string;
  target_format: string;
  options?: { pages?: string; dpi?: number };
}

interface ConvertResponse {
  task_id: string;
  status: 'pending' | 'processing' | 'done' | 'failed' | 'timeout';
}

interface TaskResponse {
  task_id: string;
  status: 'pending' | 'processing' | 'done' | 'failed' | 'timeout';
  download_url: string | null;
  error: string | null;
  warning: string | null;
}

interface FileItem {
  file_id: string;
  filename: string;
  size: number;
  source_type: string;
}

interface TaskItem {
  task_id: string;
  source_filename: string;
  target_format: string;
  status: string;
  download_url: string | null;
  error: string | null;
  warning: string | null;
}
```

### API 客户端（`api/client.ts`）

基于 `fetch`，统一错误处理（对接 Plan 1 的 `{ error: { code, message } }` 格式）：

```typescript
const BASE = '/api';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, init);
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.error?.message ?? '请求失败');
  }
  return res.json();
}

export const api = {
  upload(file: File): Promise<UploadResponse> { /* FormData */ },
  convert(req: ConvertRequest): Promise<ConvertResponse> { /* JSON */ },
  getTask(taskId: string): Promise<TaskResponse> { /* GET */ },
  downloadUrl(taskId: string): string { return `${BASE}/download/${taskId}`; },
  formats(): Promise<FormatsResponse> { /* GET /formats */ },
};
```

### 关键决策

- **错误处理**：所有 4xx/5xx 抛 `Error(message)`，由调用方 hooks 用 AntD `message.error` 展示
- **download**：不通过 fetch，直接用 `window.location.href = api.downloadUrl(taskId)` 触发浏览器下载（后端返回 `Content-Disposition: attachment`）
- **无 axios**：fetch 足够，YAGNI

## 4. 组件设计

### 页面布局

```
┌─────────────────────────────────────────────┐
│  OFD 转换工具（AntD PageHeader）            │
├─────────────────────────────────────────────┤
│  UploadZone（AntD Dragger，拖拽+选择）      │
├──────────────────┬──────────────────────────┤
│  FileList        │  PreviewPanel            │
│  (左侧)          │  (右侧，ofd.js 渲染)     │
│  - file1.ofd ●   │  选中文件的页面预览      │
│  - file2.ofd     │  （多页可翻页）          │
├──────────────────┼──────────────────────────┤
│  ConvertOptions（格式选择 + 开始转换按钮）  │
├─────────────────────────────────────────────┤
│  TaskList（转换任务状态 + 下载按钮）        │
└─────────────────────────────────────────────┘
```

### 组件职责

**UploadZone**（AntD `Dragger`）
- 拖拽 + 点击选择，多文件
- 上传调用 `useUpload`，成功后加入 FileList
- 按 `source_type` 校验（调用 `/api/formats` 判断是否支持）

**FileList**（AntD `List`）
- 展示已上传文件（文件名、大小、源类型标签）
- 单选高亮，选中项传给 PreviewPanel + ConvertOptions
- 删除按钮（仅前端移除，后端文件靠保留期清理）

**PreviewPanel**（AntD `Card` + ofd.js）
- 选中 OFD 文件时用 ofd.js 渲染（usePreview hook）
- 多页文档显示分页控件（AntD `Pagination`）
- 非 OFD 文件显示「不支持预览」占位
- 切换文件时清理上一次渲染（避免内存泄漏）

**ConvertOptions**（AntD `Radio.Group` + `Button`）
- 根据选中文件的 `source_type`，从 `/api/formats` 取可用目标格式
- 单选目标格式
- 选择 OFD->DOCX/MD 时，点「开始转换」前弹 `LossyWarningModal`
- 批量：支持对多个已选文件用同一格式转换

**TaskList**（AntD `Table` 或 `List`）
- 展示转换任务（源文件 -> 目标格式、状态、下载）
- 状态轮询：`useTaskPolling` 每 2 秒查 `/api/task/{id}`，done 显示下载按钮
- 状态图标：pending/processing（Loading）、done（✓+下载）、failed（✗+错误）、timeout（⏱）
- 有 warning 的任务显示警告图标（tooltip 展示 warning 文本）

**LossyWarningModal**（AntD `Modal`）
- 选择 OFD->DOCX/MD 转换时弹出
- 文案取自后端 `warning` 字段（Plan 2）：「版式转 DOCX 为有损转换…」
- 确认后继续转换，取消则中止

### 状态流

```
App 持有：
  files: FileItem[]           // 已上传文件
  selectedFileId: string|null // 当前选中
  tasks: TaskItem[]           // 转换任务

useUpload -> 添加 file
useConvert -> 创建 task，加入 tasks
useTaskPolling(tasks) -> 更新 task.status/download_url
```

## 5. hooks 设计

4 个自定义 hooks 封装 API 调用与副作用，组件只消费 hooks 返回的状态。

### useUpload

```typescript
function useUpload(onSuccess: (file: FileItem) => void) {
  const [uploading, setUploading] = useState(false);
  async function upload(file: File): Promise<void> {
    setUploading(true);
    try {
      const res = await api.upload(file);
      onSuccess({ file_id: res.file_id, filename: res.filename, size: res.size, source_type: res.source_type });
      message.success('上传成功');
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setUploading(false);
    }
  }
  return { upload, uploading };
}
```

### useConvert

```typescript
function useConvert() {
  const [converting, setConverting] = useState(false);
  async function convert(fileId: string, targetFormat: string): Promise<TaskItem | null> {
    setConverting(true);
    try {
      const res = await api.convert({ file_id: fileId, target_format: targetFormat });
      const task = await api.getTask(res.task_id);
      return { task_id: res.task_id, source_filename: '', target_format, ...task };
    } catch (e) {
      message.error((e as Error).message);
      return null;
    } finally {
      setConverting(false);
    }
  }
  return { convert, converting };
}
```

### useTaskPolling（核心）

```typescript
function useTaskPolling(tasks: TaskItem[], onUpdate: (task: TaskItem) => void) {
  // 过滤出未结束的任务（pending/processing）
  // 每 2 秒轮询这些任务的 /api/task/{id}
  // 收到 done/failed/timeout 停止该任务轮询，调用 onUpdate
  // 所有任务结束时停止定时器（组件卸载也清理）
}
```

关键点：
- 只轮询未结束任务（避免已完成任务持续请求）
- 用单个 `setInterval` 统一轮询（非每任务一个定时器）
- `useEffect` 依赖 `tasks` 中未结束任务列表，列表空时清 interval
- 组件卸载清 interval（防内存泄漏）

### usePreview（ofd.js，依赖 PoC 结果）

```typescript
function usePreview(containerRef: RefObject<HTMLDivElement>) {
  const [loading, setLoading] = useState(false);
  const [pages, setPages] = useState<HTMLElement[]>([]);
  const [currentPage, setCurrentPage] = useState(0);

  async function preview(fileId: string | null) {
    // 1. 清理上一次渲染（containerRef.current.innerHTML = ''）
    // 2. 若 fileId 非 OFD 源，显示占位
    // 3. 用前端缓存的 File 对象（免后端往返）
    // 4. ofd.js parseOfdDocument + renderOfdByIndex
    // 5. 渲染产物 append 到 containerRef
  }
  return { preview, loading, pages, currentPage, setCurrentPage };
}
```

### PoC 前的不确定点

- ofd.js 的 `parseOfdDocument`/`renderOfd` 的实际回调形态（同步/异步、回调函数）-- PoC 验证
- 预览输入源：上传的 `File` 对象可直接给 ofd.js，还是必须后端返回 OFD 原文件？倾向用前端缓存的 `File` 对象（免后端往返，但需在 FileItem 里缓存 File）

## 6. 部署与构建

### Docker 构建链

```
frontend/Dockerfile (多阶段)
├── node:20-alpine 阶段: npm ci && npm run build -> dist/
└── nginx:alpine 阶段: 复制 dist/ + nginx.conf
```

### nginx 配置（`frontend/nginx.conf`）

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 50m;
    }

    location /health {
        proxy_pass http://backend:8080;
    }
}
```

### docker-compose 更新（`ofd-converter/docker-compose.yml`）

Plan 1 的 compose 只有 backend 服务。Plan 3 补充 frontend 服务：

```yaml
services:
  backend:        # Plan 1 已有
    ...
  frontend:       # Plan 3 新增
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      backend:
        condition: service_healthy
    restart: unless-stopped
volumes:
  ofd-data:       # Plan 1 已有
```

### 关键决策

- **nginx 透传 XFF**：`X-Forwarded-For` 头透传给后端，后端 `ClientIpInterceptor`（Plan 1）据此获取真实客户端 IP（spec §9 日志记录依赖）
- **50MB 上传限制**：nginx `client_max_body_size 50m` 与后端一致（spec §8）
- **depends_on healthcheck**：frontend 等 backend 健康检查通过再启动（避免启动时 502）
- **无 https**：企业内网部署，http 即可（https 留后续）

## 7. 测试与验收

### 测试策略

前端测试比后端轻量（前端逻辑主要是 API 调用编排 + UI 渲染），重点测 hooks 的状态逻辑，组件用 mock API 集成测试。

### 单元测试（hooks，Vitest + React Testing Library）

- `useUpload`：成功/失败路径，`uploading` 状态切换
- `useConvert`：成功创建任务、失败处理
- `useTaskPolling`：
  - 未结束任务触发轮询
  - done/failed/timeout 停止轮询
  - 所有任务结束清 interval
  - 组件卸载清 interval（防泄漏）
- `usePreview`：非 OFD 文件显示占位、切换文件清理上一次渲染

### 组件测试（React Testing Library + mock API）

- `UploadZone`：拖拽/选择触发上传，错误显示 message
- `FileList`：列表渲染、选中高亮、删除
- `ConvertOptions`：根据 `source_type` 显示对应格式、选 OFD->DOCX 弹 warning modal
- `TaskList`：状态图标/文案、done 显示下载、failed 显示错误
- `LossyWarningModal`：确认/取消行为

### 集成测试（mock 全部 API）

完整流程：上传 -> 选格式 -> 弹有损提示 -> 转换 -> 轮询到 done -> 下载。验证组件协作 + 状态流。

### 不测试的

- ofd.js 内部渲染（第三方库，PoC 已验证可用）
- Ant Design 组件本身（第三方）
- nginx/部署配置（手动验证）

### 验收标准

- 上传多文件成功，FileList 正确展示
- 选中 OFD 文件，PreviewPanel 用 ofd.js 渲染出页面（PoC 验证的可视确认）
- 选 OFD->DOCX/MD 弹有损提示，确认后转换
- 转换任务列表实时更新（轮询 2 秒），done 显示下载按钮
- 下载触发浏览器下载，文件名正确
- 非 OFD 文件预览显示占位
- 开发环境 `npm run dev` + 后端，全流程跑通
- `npm run build` 成功，nginx 服务 dist/ 可访问

### 已知限制

- ofd.js 预览质量依赖其渲染能力（复杂 OFD 可能有渲染差异）
- 真实 OFD 样本预览效果需实际验证（PoC + 验收阶段）

## 8. 后续扩展（不在 Plan 3 范围）

- WebSocket 推送任务状态（替代轮询，spec §15）
- 用户认证（spec §15，接入后前端需登录页）
- 转换历史记录查询界面（spec §15）
- OFD 签章验证展示（spec §15）
- https 部署
