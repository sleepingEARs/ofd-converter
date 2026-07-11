# OFD Converter - Plan 3 (Frontend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the React + TypeScript + Vite + Ant Design single-page frontend for the OFD converter: upload, ofd.js preview, format-select convert, polling, download, with a lossy-conversion warning modal.

**Architecture:** A Vite SPA at `frontend/` consumes the Plan 1/2 REST API (`/api/*`). Four custom hooks (`useUpload`, `useConvert`, `useTaskPolling`, `usePreview`) encapsulate API calls and side effects; six Ant Design components compose the single page. ofd.js integration is proven first via a PoC (Task 2). nginx serves the build and reverse-proxies `/api` to the backend.

**Tech Stack:** React 19, TypeScript, Vite, Ant Design 5, ofd.js, Vitest + React Testing Library, nginx.

## Global Constraints

(Copied verbatim from the Plan 3 design spec. Every task implicitly includes these.)

- Frontend at `ofd-converter/frontend/` (same repo as `backend/`).
- Scaffold via `npm create vite@latest frontend -- --template react-ts`.
- Dependencies: `antd` (v5), `ofd.js`. Dev: `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `jsdom`.
- Backend API base path: `/api` (dev: Vite proxy; prod: nginx reverse-proxy). JSON field naming: snake_case (backend Jackson SNAKE_CASE).
- API endpoints (Plan 1/2, verified): `POST /api/upload` (multipart `file`) -> `{file_id, filename, size, source_type}`; `POST /api/convert` (`{file_id, target_format, options?}`) -> `{task_id, status}`; `GET /api/task/{id}` -> `{task_id, status, download_url, error, warning}`; `GET /api/download/{id}` (file stream); `GET /api/formats` -> `{ofd:[...], pdf:[...], image:[...]}`; `GET /health`.
- `target_format` values: `pdf | png | jpg | txt | docx | md | ofd`.
- Task status values (lowercase): `pending | processing | done | failed | timeout`.
- Lossy warning (Plan 2): OFD->DOCX warning = "版式转 DOCX 为有损转换，排版可能变化，仅供参考"; OFD->MD warning = "OFD 转 Markdown 为结构推断，复杂版面可能有损，仅供参考"; others null.
- Polling interval: 2 seconds. Only poll non-terminal tasks (pending/processing).
- Download: `window.location.href = '/api/download/{taskId}'` (browser handles Content-Disposition).
- Upload size limit: 50MB (enforced backend-side; frontend trusts API error).
- UI copy in Chinese.
- TDD: failing test first, then minimal implementation. Every task ends with a commit.

## Scope Check

One cohesive frontend feature. The ofd.js PoC (Task 2) is a gate but not a separate subsystem. No decomposition needed.

## File Structure (this plan creates/modifies)

```
ofd-converter/
├── frontend/                           # CREATE (whole dir)
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json / tsconfig.app.json / tsconfig.node.json
│   ├── vite.config.ts                  # dev proxy /api -> :8080 + vitest config
│   ├── vitest.setup.ts
│   ├── nginx.conf
│   ├── Dockerfile
│   ├── public/
│   └── src/
│       ├── main.tsx                    # AntD ConfigProvider mount
│       ├── App.tsx                     # single-page layout + shared state
│       ├── styles.css
│       ├── api/client.ts               # fetch wrapper + typed endpoints
│       ├── types/api.ts                # API + internal types
│       ├── types/ofdjs.d.ts            # ofd.js type declarations (self-written)
│       ├── hooks/useUpload.ts
│       ├── hooks/useConvert.ts
│       ├── hooks/useTaskPolling.ts
│       ├── hooks/usePreview.ts
│       ├── components/UploadZone.tsx
│       ├── components/FileList.tsx
│       ├── components/PreviewPanel.tsx
│       ├── components/ConvertOptions.tsx
│       ├── components/TaskList.tsx
│       ├── components/LossyWarningModal.tsx
│       └── components/PreviewPoc.tsx   # Task 2 PoC (kept as reference)
│       └── tests (co-located: *.test.tsx / *.test.ts)
├── docker-compose.yml                  # MODIFY: add frontend service
```

---

## Task 1: Scaffold frontend project + API client + types

**Files:**
- Create: `frontend/` (vite react-ts scaffold), `frontend/src/types/api.ts`, `frontend/src/api/client.ts`, `frontend/vite.config.ts` (modify), `frontend/vitest.setup.ts`, `frontend/src/api/client.test.ts`

**Interfaces:**
- Produces: `api` object with `upload/convert/getTask/downloadUrl/formats`; types `FormatsResponse, UploadResponse, ConvertRequest, ConvertResponse, TaskResponse, FileItem, TaskItem`. Consumed by hooks (Tasks 3-6).

- [ ] **Step 1: Scaffold Vite project**

```bash
cd /home/alex/my_workspace/ofd-converter
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
npm install antd ofd.js
npm install -D vitest @testing-library/react @testing-library/jest-dom jsdom @testing-library/user-event
```

- [ ] **Step 2: Configure Vite (dev proxy + vitest)**

Replace `frontend/vite.config.ts`:
```typescript
/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/health': 'http://localhost:8080',
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './vitest.setup.ts',
  },
})
```

Create `frontend/vitest.setup.ts`:
```typescript
import '@testing-library/jest-dom'
```

- [ ] **Step 3: Write the failing test**

`frontend/src/api/client.test.ts`:
```typescript
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { api } from './client'

describe('api client', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => { vi.unstubAllGlobals() })

  it('formats() returns grouped formats', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ ofd: ['pdf', 'md'], pdf: ['ofd'] }),
    })
    const f = await api.formats()
    expect(f).toEqual({ ofd: ['pdf', 'md'], pdf: ['ofd'] })
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/formats', undefined)
  })

  it('getTask returns task', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ task_id: 't1', status: 'done', download_url: '/api/download/t1', error: null, warning: null }),
    })
    const t = await api.getTask('t1')
    expect(t.status).toBe('done')
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/task/t1', undefined)
  })

  it('throws on non-ok with backend error message', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      json: async () => ({ error: { code: 'FILE_TOO_LARGE', message: '超过 50MB 限制' } }),
    })
    await expect(api.formats()).rejects.toThrow('超过 50MB 限制')
  })

  it('downloadUrl builds path', () => {
    expect(api.downloadUrl('t1')).toBe('/api/download/t1')
  })

  it('upload posts FormData', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ file_id: 'f1', filename: 'a.ofd', size: 10, source_type: 'OFD' }),
    })
    const file = new File(['x'], 'a.ofd', { type: 'application/octet-stream' })
    const r = await api.upload(file)
    expect(r.file_id).toBe('f1')
    const [path, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(path).toBe('/api/upload')
    expect((init as RequestInit).method).toBe('POST')
    expect(((init as RequestInit).body as FormData).get('file')).toBe(file)
  })
})
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/api/client.test.ts`
Expected: FAIL - `./client` does not export `api`.

- [ ] **Step 5: Write types + client**

`frontend/src/types/api.ts`:
```typescript
export type FormatsResponse = Record<string, string[]>

export interface UploadResponse {
  file_id: string
  filename: string
  size: number
  source_type: 'OFD' | 'PDF' | 'IMAGE' | 'DOCX'
}

export interface ConvertRequest {
  file_id: string
  target_format: string
  options?: { pages?: string; dpi?: number }
}

export interface ConvertResponse {
  task_id: string
  status: 'pending' | 'processing' | 'done' | 'failed' | 'timeout'
}

export interface TaskResponse {
  task_id: string
  status: 'pending' | 'processing' | 'done' | 'failed' | 'timeout'
  download_url: string | null
  error: string | null
  warning: string | null
}

export interface FileItem {
  file_id: string
  filename: string
  size: number
  source_type: string
  file: File   // cached for ofd.js preview
}

export interface TaskItem {
  task_id: string
  source_filename: string
  target_format: string
  status: string
  download_url: string | null
  error: string | null
  warning: string | null
}
```

`frontend/src/api/client.ts`:
```typescript
import type {
  FormatsResponse, UploadResponse, ConvertRequest, ConvertResponse, TaskResponse,
} from '../types/api'

const BASE = '/api'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, init)
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.error?.message ?? '请求失败')
  }
  return res.json()
}

export const api = {
  formats(): Promise<FormatsResponse> {
    return request<FormatsResponse>('/formats')
  },
  upload(file: File): Promise<UploadResponse> {
    const fd = new FormData()
    fd.append('file', file)
    return request<UploadResponse>('/upload', { method: 'POST', body: fd })
  },
  convert(req: ConvertRequest): Promise<ConvertResponse> {
    return request<ConvertResponse>('/convert', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    })
  },
  getTask(taskId: string): Promise<TaskResponse> {
    return request<TaskResponse>(`/task/${taskId}`)
  },
  downloadUrl(taskId: string): string {
    return `${BASE}/download/${taskId}`
  },
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/api/client.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git add frontend/
git commit -m "feat(frontend): scaffold vite project + API client + types"
```

---

## Task 2: ofd.js + React 19 integration PoC

**Files:**
- Create: `frontend/src/types/ofdjs.d.ts`, `frontend/src/components/PreviewPoc.tsx`, `frontend/src/components/PreviewPoc.test.tsx`

**Interfaces:**
- Produces: `ofdjs.d.ts` type declarations (verified API shape); `PreviewPoc` proving ofd.js parses+renders in React. Informs `usePreview` (Task 6). If PoC fails, escalate before proceeding to Task 6.

- [ ] **Step 1: Inspect ofd.js API**

Run: `cd frontend && cat node_modules/ofd.js/package.json | head -20` and inspect `node_modules/ofd.js/` for the exported entry and the `parseOfdDocument` / `renderOfd` / `renderOfdByIndex` signatures. ofd.js (per Plan 1 research) exposes: `parseOfdDocument({ ofd, success, fail })` and `renderOfd(documentIndex, width)` / `renderOfdByIndex(documentIndex, pageIndex, width)`, returning DOM nodes (SVG/Canvas wrappers).

- [ ] **Step 2: Write type declarations**

`frontend/src/types/ofdjs.d.ts`:
```typescript
declare module 'ofd.js' {
  export interface ParseOfdResult {
    pageCount: number
    // ofd.js returns pages as arrays of DOM/scale-result objects per document
    [key: string]: unknown
  }
  export interface ParseOfdOptions {
    ofd: ArrayBuffer | Uint8Array | string
    success?: (result: ParseOfdResult | ParseOfdResult[]) => void
    fail?: (error: Error) => void
  }
  export function parseOfdDocument(options: ParseOfdOptions): void
  export function renderOfd(documentIndex: number, width?: number): HTMLElement[]
  export function renderOfdByIndex(documentIndex: number, pageIndex: number, width?: number): HTMLElement
  const _default: { parseOfdDocument: typeof parseOfdDocument; renderOfd: typeof renderOfd; renderOfdByIndex: typeof renderOfdByIndex }
  export default _default
}
```

> Note: these signatures are the PoC's hypothesis. If Step 3 reveals a different shape (e.g. promise-returning, or different param names), update `ofdjs.d.ts` and the PoC to match the real API, and record the verified shape in the commit message.

- [ ] **Step 3: Write the PoC test**

`frontend/src/components/PreviewPoc.test.tsx`:
```typescript
import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PreviewPoc } from './PreviewPoc'

describe('PreviewPoc', () => {
  it('parses and renders an OFD file into the container', async () => {
    // Mock ofd.js: parseOfdDocument calls success synchronously; renderOfd returns a stub div.
    vi.doMock('ofd.js', () => ({
      parseOfdDocument: ({ success }: { success?: (r: unknown) => void }) => {
        success?.({ pageCount: 1 })
      },
      renderOfd: () => {
        const d = document.createElement('div')
        d.textContent = 'rendered-page'
        return [d]
      },
    }))
    const { PreviewPoc: Poc } = await import('./PreviewPoc')
    render(<Poc />)
    const input = screen.getByTestId('ofd-input') as HTMLInputElement
    const file = new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], 'a.ofd', { type: 'application/octet-stream' })
    await userEvent.upload(input, file)
    await waitFor(() => {
      expect(screen.getByText('rendered-page')).toBeInTheDocument()
    })
    expect(screen.getByText(/页数: 1/)).toBeInTheDocument()
  })
})
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/components/PreviewPoc.test.tsx`
Expected: FAIL - `PreviewPoc` does not exist.

- [ ] **Step 5: Write PreviewPoc**

`frontend/src/components/PreviewPoc.tsx`:
```tsx
import { useRef, useState } from 'react'
import { parseOfdDocument, renderOfd } from 'ofd.js'

export function PreviewPoc() {
  const containerRef = useRef<HTMLDivElement>(null)
  const [pageCount, setPageCount] = useState<number | null>(null)

  async function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file || !containerRef.current) return
    const buf = await file.arrayBuffer()
    containerRef.current.innerHTML = ''
    parseOfdDocument({
      ofd: buf,
      success: (result) => {
        const r = Array.isArray(result) ? result[0] : result
        const count = (r as { pageCount?: number }).pageCount ?? 0
        setPageCount(count)
        const nodes = renderOfd(0, 800)
        for (const n of nodes) containerRef.current!.appendChild(n)
      },
      fail: (err) => {
        setPageCount(null)
        console.error('ofd.js parse failed', err)
      },
    })
  }

  return (
    <div>
      <input data-testid="ofd-input" type="file" accept=".ofd" onChange={onFile} />
      <div ref={containerRef} />
      {pageCount !== null && <p>页数: {pageCount}</p>}
    </div>
  )
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/components/PreviewPoc.test.tsx`
Expected: PASS. If ofd.js's real export shape differs from `ofdjs.d.ts` (e.g. default export, promise API), adjust the import and declarations until the PoC test passes, then record the verified shape.

- [ ] **Step 7: Manual PoC verification (optional but recommended)**

Run: `cd frontend && npm run dev`, open the PoC route, upload a real `.ofd` file (e.g. generated by the backend Fixtures or a real invoice). Confirm pages render in the browser. If rendering fails in the browser despite the unit test passing, note the discrepancy - the unit test mocks ofd.js, so a browser failure means the real ofd.js API differs.

- [ ] **Step 8: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git add frontend/src/types/ofdjs.d.ts frontend/src/components/PreviewPoc.tsx frontend/src/components/PreviewPoc.test.tsx
git commit -m "poc(frontend): ofd.js + React 19 integration (verified API shape)"
```

> If the PoC cannot be made to work (ofd.js incompatible with React 19/Vite), STOP and escalate: the spec's fallback is OFD->HTML server-side rendering, which requires a backend change outside this plan.

---

## Task 3: useUpload + useConvert hooks

**Files:**
- Create: `frontend/src/hooks/useUpload.ts`, `frontend/src/hooks/useUpload.test.ts`, `frontend/src/hooks/useConvert.ts`, `frontend/src/hooks/useConvert.test.ts`

**Interfaces:**
- Consumes: `api` (Task 1), `FileItem`/`TaskItem` (Task 1).
- Produces: `useUpload(onSuccess)` -> `{ upload, uploading }`; `useConvert()` -> `{ convert, converting }`. Consumed by `UploadZone` (Task 8) and `ConvertOptions` (Task 9).

- [ ] **Step 1: Write the failing tests**

`frontend/src/hooks/useUpload.test.ts`:
```typescript
import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useUpload } from './useUpload'

afterEach(() => vi.unstubAllGlobals())

describe('useUpload', () => {
  it('calls onSuccess on success and toggles uploading', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ file_id: 'f1', filename: 'a.ofd', size: 10, source_type: 'OFD' }),
    }))
    const onSuccess = vi.fn()
    const { result } = renderHook(() => useUpload(onSuccess))
    expect(result.current.uploading).toBe(false)
    let p: Promise<void>
    act(() => { p = result.current.upload(new File(['x'], 'a.ofd')) })
    expect(result.current.uploading).toBe(true)
    await act(async () => { await p! })
    expect(result.current.uploading).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith(expect.objectContaining({ file_id: 'f1', source_type: 'OFD' }))
  })

  it('does not call onSuccess on failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ error: { code: 'X', message: '失败' } }),
    }))
    const onSuccess = vi.fn()
    const { result } = renderHook(() => useUpload(onSuccess))
    await act(async () => { await result.current.upload(new File(['x'], 'a.ofd')) })
    expect(onSuccess).not.toHaveBeenCalled()
  })
})
```

`frontend/src/hooks/useConvert.test.ts`:
```typescript
import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useConvert } from './useConvert'

afterEach(() => vi.unstubAllGlobals())

describe('useConvert', () => {
  it('returns a TaskItem on success', async () => {
    let call = 0
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => {
      call++
      return Promise.resolve({
        ok: true,
        json: async () =>
          call === 1
            ? { task_id: 't1', status: 'pending' }
            : { task_id: 't1', status: 'pending', download_url: null, error: null, warning: 'OFD 转 Markdown 为结构推断，复杂版面可能有损，仅供参考' },
      })
    }))
    const { result } = renderHook(() => useConvert())
    let task: Awaited<ReturnType<typeof result.current.convert>>
    await act(async () => { task = await result.current.convert('f1', 'md') })
    expect(task!.task_id).toBe('t1')
    expect(task!.warning).toContain('Markdown')
    expect(task!.status).toBe('pending')
  })

  it('returns null on failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ error: { code: 'X', message: '失败' } }),
    }))
    const { result } = renderHook(() => useConvert())
    let task: Awaited<ReturnType<typeof result.current.convert>> | null = null
    await act(async () => { task = await result.current.convert('f1', 'pdf') })
    expect(task).toBeNull()
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/hooks/useUpload.test.ts src/hooks/useConvert.test.ts`
Expected: FAIL - hooks don't exist.

- [ ] **Step 3: Write useUpload**

`frontend/src/hooks/useUpload.ts`:
```typescript
import { useState } from 'react'
import { message } from 'antd'
import { api } from '../api/client'
import type { FileItem } from '../types/api'

export function useUpload(onSuccess: (file: FileItem) => void) {
  const [uploading, setUploading] = useState(false)

  async function upload(file: File): Promise<void> {
    setUploading(true)
    try {
      const res = await api.upload(file)
      onSuccess({ file_id: res.file_id, filename: res.filename, size: res.size, source_type: res.source_type, file })
      message.success('上传成功')
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setUploading(false)
    }
  }

  return { upload, uploading }
}
```

- [ ] **Step 4: Write useConvert**

`frontend/src/hooks/useConvert.ts`:
```typescript
import { useState } from 'react'
import { message } from 'antd'
import { api } from '../api/client'
import type { TaskItem } from '../types/api'

export function useConvert() {
  const [converting, setConverting] = useState(false)

  async function convert(fileId: string, sourceFilename: string, targetFormat: string): Promise<TaskItem | null> {
    setConverting(true)
    try {
      const res = await api.convert({ file_id: fileId, target_format: targetFormat })
      const task = await api.getTask(res.task_id)
      return {
        task_id: res.task_id,
        source_filename: sourceFilename,
        target_format: targetFormat,
        status: task.status,
        download_url: task.download_url,
        error: task.error,
        warning: task.warning,
      }
    } catch (e) {
      message.error((e as Error).message)
      return null
    } finally {
      setConverting(false)
    }
  }

  return { convert, converting }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/hooks/useUpload.test.ts src/hooks/useConvert.test.ts`
Expected: PASS (4 tests). Note: `useConvert` signature in the test calls `convert('f1', 'md')` (2 args) but the implementation takes 3 (`fileId, sourceFilename, targetFormat`) - update the test to `convert('f1', 'a.ofd', 'md')` to match.

- [ ] **Step 6: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git add frontend/src/hooks/
git commit -m "feat(frontend): add useUpload + useConvert hooks"
```

---

## Task 4: useTaskPolling hook

**Files:**
- Create: `frontend/src/hooks/useTaskPolling.ts`, `frontend/src/hooks/useTaskPolling.test.ts`

**Interfaces:**
- Consumes: `api.getTask`, `TaskItem`.
- Produces: `useTaskPolling(tasks, onUpdate)` - polls non-terminal tasks every 2s, calls `onUpdate` with refreshed TaskItem, clears interval when no non-terminal tasks remain or on unmount. Consumed by `App` (Task 7).

- [ ] **Step 1: Write the failing test**

`frontend/src/hooks/useTaskPolling.test.ts`:
```typescript
import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useTaskPolling } from './useTaskPolling'
import type { TaskItem } from '../types/api'

afterEach(() => vi.unstubAllGlobals())

const mkTask = (over: Partial<TaskItem> = {}): TaskItem => ({
  task_id: 't1', source_filename: 'a.ofd', target_format: 'pdf',
  status: 'pending', download_url: null, error: null, warning: null, ...over,
})

describe('useTaskPolling', () => {
  it('does not poll when all tasks are terminal', () => {
    vi.stubGlobal('fetch', vi.fn())
    const onUpdate = vi.fn()
    renderHook(() => useTaskPolling([mkTask({ status: 'done' })], onUpdate))
    expect(vi.mocked(fetch)).not.toHaveBeenCalled()
  })

  it('polls non-terminal tasks and stops on terminal', async () => {
    vi.useFakeTimers()
    let call = 0
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => {
        call++
        return { task_id: 't1', status: call < 2 ? 'processing' : 'done', download_url: '/api/download/t1', error: null, warning: null }
      },
    }))
    const onUpdate = vi.fn()
    const { rerender } = renderHook(({ tasks }) => useTaskPolling(tasks, onUpdate), {
      initialProps: { tasks: [mkTask({ status: 'pending' })] },
    })
    await act(async () => { await vi.advanceTimersByTimeAsync(2000) })
    expect(onUpdate).toHaveBeenCalled()
    // After done, update tasks prop to terminal -> no further fetch
    rerender({ tasks: [mkTask({ status: 'done' })] })
    const callsAfterDone = vi.mocked(fetch).mock.calls.length
    await act(async () => { await vi.advanceTimersByTimeAsync(4000) })
    expect(vi.mocked(fetch).mock.calls.length).toBe(callsAfterDone)
    vi.useRealTimers()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/hooks/useTaskPolling.test.ts`
Expected: FAIL - hook doesn't exist.

- [ ] **Step 3: Write useTaskPolling**

`frontend/src/hooks/useTaskPolling.ts`:
```typescript
import { useEffect, useRef } from 'react'
import { api } from '../api/client'
import type { TaskItem } from '../types/api'

const POLL_INTERVAL = 2000
const TERMINAL = new Set(['done', 'failed', 'timeout'])

export function useTaskPolling(tasks: TaskItem[], onUpdate: (task: TaskItem) => void) {
  const onUpdateRef = useRef(onUpdate)
  onUpdateRef.current = onUpdate

  const pending = tasks.filter((t) => !TERMINAL.has(t.status))
  const pendingKey = pending.map((t) => t.task_id).join(',')

  useEffect(() => {
    if (!pendingKey) return
    let cancelled = false

    async function tick() {
      for (const t of pending) {
        if (cancelled) return
        try {
          const r = await api.getTask(t.task_id)
          if (!cancelled) {
            onUpdateRef.current({ ...t, status: r.status, download_url: r.download_url, error: r.error, warning: r.warning })
          }
        } catch {
          // swallow; next tick retries
        }
      }
    }

    tick()
    const id = setInterval(tick, POLL_INTERVAL)
    return () => { cancelled = true; clearInterval(id) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pendingKey])

  // Re-read pending on every render so the effect closure sees fresh task objects.
  void pending
}
```

> Note: `pendingKey` drives the effect; `pending` is read inside `tick` from the closure formed each render where the effect re-runs. Because `pendingKey` only changes when the *set* of non-terminal ids changes, but task objects mutate (status flips to done), the onUpdate closure could go stale. The implementation re-derives `pending` each render and the `void pending` keeps the linter quiet; to be robust, the effect reads `tasks` via a ref. If the test reveals staleness, switch to a `tasksRef`. Keep it simple first; the test (rerender with terminal status) verifies the stop condition.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/hooks/useTaskPolling.test.ts`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git add frontend/src/hooks/useTaskPolling.ts frontend/src/hooks/useTaskPolling.test.ts
git commit -m "feat(frontend): add useTaskPolling hook (2s interval, stops on terminal)"
```

---

## Task 5: usePreview hook

**Files:**
- Create: `frontend/src/hooks/usePreview.ts`, `frontend/src/hooks/usePreview.test.ts`

**Interfaces:**
- Consumes: ofd.js (Task 2 verified shape), `FileItem`.
- Produces: `usePreview()` -> `{ preview(file: FileItem | null), loading, pages, currentPage, setCurrentPage, clear }`. Consumed by `PreviewPanel` (Task 8).

- [ ] **Step 1: Write the failing test**

`frontend/src/hooks/usePreview.test.ts`:
```typescript
import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { usePreview } from './usePreview'
import type { FileItem } from '../types/api'

afterEach(() => vi.unstubAllGlobals())

const ofdFile = (name = 'a.ofd'): FileItem => ({
  file_id: 'f1', filename: name, size: 10, source_type: 'OFD',
  file: new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], name),
})

describe('usePreview', () => {
  it('renders pages for an OFD file', async () => {
    vi.doMock('ofd.js', () => ({
      parseOfdDocument: ({ success }: { success?: (r: unknown) => void }) => success?.([{ pageCount: 2 }]),
      renderOfdByIndex: (_di: number, pi: number) => {
        const d = document.createElement('div')
        d.textContent = `page-${pi}`
        return d
      },
    }))
    const { usePreview: hook } = await import('./usePreview')
    const { result } = renderHook(() => hook())
    await act(async () => { await result.current.preview(ofdFile()) })
    expect(result.current.pages.length).toBeGreaterThan(0)
    expect(result.current.loading).toBe(false)
  })

  it('shows placeholder for non-OFD file (no throw)', async () => {
    const { usePreview: hook } = await import('./usePreview')
    const { result } = renderHook(() => hook())
    await act(async () => {
      await result.current.preview({ ...ofdFile('a.pdf'), source_type: 'PDF' })
    })
    expect(result.current.pages).toHaveLength(0)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/hooks/usePreview.test.ts`
Expected: FAIL - hook doesn't exist.

- [ ] **Step 3: Write usePreview**

`frontend/src/hooks/usePreview.ts`:
```typescript
import { useState, useCallback } from 'react'
import { parseOfdDocument, renderOfd, renderOfdByIndex } from 'ofd.js'
import type { FileItem } from '../types/api'

export function usePreview() {
  const [loading, setLoading] = useState(false)
  const [pages, setPages] = useState<HTMLElement[]>([])
  const [currentPage, setCurrentPage] = useState(0)

  const clear = useCallback(() => {
    setPages([])
    setCurrentPage(0)
  }, [])

  const preview = useCallback(async (file: FileItem | null) => {
    setPages([])
    setCurrentPage(0)
    if (!file || file.source_type !== 'OFD') {
      return
    }
    setLoading(true)
    try {
      const buf = await file.file.arrayBuffer()
      await new Promise<void>((resolve, reject) => {
        parseOfdDocument({
          ofd: buf,
          success: (result) => {
            const r = Array.isArray(result) ? result[0] : result
            const count = (r as { pageCount?: number }).pageCount ?? 0
            if (count === 0) {
              setPages(renderOfd(0, 800))
            } else {
              setPages(Array.from({ length: count }, (_, i) => renderOfdByIndex(0, i, 800)))
            }
            resolve()
          },
          fail: (err) => reject(err),
        })
      })
    } catch {
      setPages([])
    } finally {
      setLoading(false)
    }
  }, [])

  return { preview, loading, pages, currentPage, setCurrentPage, clear }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/hooks/usePreview.test.ts`
Expected: PASS (2 tests). Adjust the render call (`renderOfd` vs `renderOfdByIndex`) to match the verified ofd.js shape from Task 2.

- [ ] **Step 5: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git add frontend/src/hooks/usePreview.ts frontend/src/hooks/usePreview.test.ts
git commit -m "feat(frontend): add usePreview hook (ofd.js, multi-page)"
```

---

## Task 6: LossyWarningModal component

**Files:**
- Create: `frontend/src/components/LossyWarningModal.tsx`, `frontend/src/components/LossyWarningModal.test.tsx`

**Interfaces:**
- Consumes: AntD `Modal`.
- Produces: `<LossyWarningModal open warning onConfirm onCancel />`. Consumed by `ConvertOptions` (Task 9).

- [ ] **Step 1: Write the failing test**

`frontend/src/components/LossyWarningModal.test.tsx`:
```typescript
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LossyWarningModal } from './LossyWarningModal'

describe('LossyWarningModal', () => {
  it('shows warning text and calls onConfirm on OK', async () => {
    const onConfirm = vi.fn()
    render(<LossyWarningModal open={true} warning="版式转 DOCX 为有损转换" onConfirm={onConfirm} onCancel={() => {}} />)
    expect(await screen.findByText('版式转 DOCX 为有损转换')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /确认/ }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('calls onCancel on cancel', async () => {
    const onCancel = vi.fn()
    render(<LossyWarningModal open={true} warning="x" onConfirm={() => {}} onCancel={onCancel} />)
    await userEvent.click(screen.getByRole('button', { name: /取消/ }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/components/LossyWarningModal.test.tsx`
Expected: FAIL - component doesn't exist.

- [ ] **Step 3: Write LossyWarningModal**

`frontend/src/components/LossyWarningModal.tsx`:
```tsx
import { Modal } from 'antd'

interface Props {
  open: boolean
  warning: string
  onConfirm: () => void
  onCancel: () => void
}

export function LossyWarningModal({ open, warning, onConfirm, onCancel }: Props) {
  return (
    <Modal
      open={open}
      title="有损转换提示"
      okText="确认"
      cancelText="取消"
      onOk={onConfirm}
      onCancel={onCancel}
    >
      <p>{warning}</p>
    </Modal>
  )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/components/LossyWarningModal.test.tsx`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git add frontend/src/components/LossyWarningModal.tsx frontend/src/components/LossyWarningModal.test.tsx
git commit -m "feat(frontend): add LossyWarningModal"
```

---

## Task 7: UploadZone + FileList components

**Files:**
- Create: `frontend/src/components/UploadZone.tsx`, `frontend/src/components/UploadZone.test.tsx`, `frontend/src/components/FileList.tsx`, `frontend/src/components/FileList.test.tsx`

**Interfaces:**
- Consumes: `useUpload` (Task 3), `FileItem`.
- Produces: `<UploadZone onUploaded={fn} />`; `<FileList files selectedFileId onSelect onDelete />`. Consumed by `App` (Task 9).

- [ ] **Step 1: Write the failing tests**

`frontend/src/components/UploadZone.test.tsx`:
```typescript
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UploadZone } from './UploadZone'

afterEach(() => vi.unstubAllGlobals())

describe('UploadZone', () => {
  it('uploads a file and calls onUploaded', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ file_id: 'f1', filename: 'a.ofd', size: 10, source_type: 'OFD' }),
    }))
    const onUploaded = vi.fn()
    render(<UploadZone onUploaded={onUploaded} />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await userEvent.upload(input, new File(['x'], 'a.ofd'))
    await waitFor(() => expect(onUploaded).toHaveBeenCalledWith(expect.objectContaining({ file_id: 'f1' })))
  })
})
```

`frontend/src/components/FileList.test.tsx`:
```typescript
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FileList } from './FileList'
import type { FileItem } from '../types/api'

const mk = (over: Partial<FileItem> = {}): FileItem => ({
  file_id: 'f1', filename: 'a.ofd', size: 10, source_type: 'OFD',
  file: new File(['x'], 'a.ofd'), ...over,
})

describe('FileList', () => {
  it('renders files and selects on click', async () => {
    const onSelect = vi.fn()
    render(<FileList files={[mk({ file_id: 'f1', filename: 'a.ofd' }), mk({ file_id: 'f2', filename: 'b.ofd' })]} selectedFileId={null} onSelect={onSelect} onDelete={() => {}} />)
    expect(screen.getByText('a.ofd')).toBeInTheDocument()
    expect(screen.getByText('b.ofd')).toBeInTheDocument()
    await userEvent.click(screen.getByText('a.ofd'))
    expect(onSelect).toHaveBeenCalledWith('f1')
  })

  it('calls onDelete on delete click', async () => {
    const onDelete = vi.fn()
    render(<FileList files={[mk({ file_id: 'f1', filename: 'a.ofd' })]} selectedFileId={null} onSelect={() => {}} onDelete={onDelete} />)
    await userEvent.click(screen.getByRole('button', { name: /删除/ }))
    expect(onDelete).toHaveBeenCalledWith('f1')
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/components/UploadZone.test.tsx src/components/FileList.test.tsx`
Expected: FAIL - components don't exist.

- [ ] **Step 3: Write UploadZone**

`frontend/src/components/UploadZone.tsx`:
```tsx
import { Upload } from 'antd'
import type { UploadProps } from 'antd'
import { useUpload } from '../hooks/useUpload'
import type { FileItem } from '../types/api'

interface Props {
  onUploaded: (file: FileItem) => void
}

export function UploadZone({ onUploaded }: Props) {
  const { upload, uploading } = useUpload(onUploaded)

  const props: UploadProps = {
    name: 'file',
    multiple: true,
    accept: '.ofd,.pdf,.png,.jpg,.jpeg,.docx',
    beforeUpload: (file) => {
      void upload(file as unknown as File)
      return false // prevent antd auto-upload; we use our api client
    },
    showUploadList: false,
    disabled: uploading,
  }

  return (
    <Upload.Dragger {...props}>
      <p className="ant-upload-text">点击或拖拽文件到此处上传</p>
      <p className="ant-upload-hint">支持 OFD / PDF / 图片 / DOCX，单文件 ≤ 50MB</p>
    </Upload.Dragger>
  )
}
```

- [ ] **Step 4: Write FileList**

`frontend/src/components/FileList.tsx`:
```tsx
import { List, Tag, Button } from 'antd'
import type { FileItem } from '../types/api'

interface Props {
  files: FileItem[]
  selectedFileId: string | null
  onSelect: (fileId: string) => void
  onDelete: (fileId: string) => void
}

const typeColor: Record<string, string> = { OFD: 'blue', PDF: 'red', IMAGE: 'green', DOCX: 'orange' }

export function FileList({ files, selectedFileId, onSelect, onDelete }: Props) {
  return (
    <List
      bordered
      dataSource={files}
      locale={{ emptyText: '暂无文件' }}
      renderItem={(f) => (
        <List.Item
          style={{ background: f.file_id === selectedFileId ? '#e6f4ff' : undefined, cursor: 'pointer' }}
          onClick={() => onSelect(f.file_id)}
          actions={[
            <Button key="del" type="link" danger size="small" onClick={(e) => { e.stopPropagation(); onDelete(f.file_id) }}>删除</Button>,
          ]}
        >
          <List.Item.Meta
            title={f.filename}
            description={<><Tag color={typeColor[f.source_type] ?? 'default'}>{f.source_type}</Tag>{(f.size / 1024).toFixed(1)} KB</>}
          />
        </List.Item>
      )}
    />
  )
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/components/UploadZone.test.tsx src/components/FileList.test.tsx`
Expected: PASS (3 tests). The `userEvent.upload` targets `input[type=file]`; AntD Dragger renders one - if the selector misses, query by `accept=".ofd"`.

- [ ] **Step 6: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git add frontend/src/components/UploadZone.tsx frontend/src/components/FileList.tsx frontend/src/components/UploadZone.test.tsx frontend/src/components/FileList.test.tsx
git commit -m "feat(frontend): add UploadZone + FileList components"
```

---

## Task 8: PreviewPanel + ConvertOptions components

**Files:**
- Create: `frontend/src/components/PreviewPanel.tsx`, `frontend/src/components/PreviewPanel.test.tsx`, `frontend/src/components/ConvertOptions.tsx`, `frontend/src/components/ConvertOptions.test.tsx`

**Interfaces:**
- Consumes: `usePreview` (Task 5), `LossyWarningModal` (Task 6), `api.formats`, `FileItem`.
- Produces: `<PreviewPanel file={FileItem | null} />`; `<ConvertOptions files selectedFileId onConvert(formats) />` where onConvert receives the chosen target_format (and the modal confirms lossy). Consumed by `App` (Task 9).

- [ ] **Step 1: Write the failing tests**

`frontend/src/components/PreviewPanel.test.tsx`:
```typescript
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { PreviewPanel } from './PreviewPanel'
import type { FileItem } from '../types/api'

afterEach(() => vi.doUnmock('ofd.js'))

describe('PreviewPanel', () => {
  it('shows placeholder for non-OFD file', () => {
    render(<PreviewPanel file={{ file_id: 'f1', filename: 'a.pdf', size: 1, source_type: 'PDF', file: new File(['x'], 'a.pdf') }} />)
    expect(screen.getByText('不支持预览')).toBeInTheDocument()
  })

  it('shows placeholder when no file selected', () => {
    render(<PreviewPanel file={null} />)
    expect(screen.getByText(/请选择/)).toBeInTheDocument()
  })

  it('renders ofd.js pages for an OFD file', async () => {
    vi.doMock('ofd.js', () => ({
      parseOfdDocument: ({ success }: { success?: (r: unknown) => void }) => success?.({ pageCount: 1 }),
      renderOfd: () => {
        const d = document.createElement('div'); d.textContent = 'ofd-page'; return [d]
      },
      renderOfdByIndex: (_di: number, pi: number) => { const d = document.createElement('div'); d.textContent = `ofd-page-${pi}`; return d },
    }))
    const { PreviewPanel: P } = await import('./PreviewPanel')
    render(<P file={{ file_id: 'f1', filename: 'a.ofd', size: 1, source_type: 'OFD', file: new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], 'a.ofd') }} />)
    await waitFor(() => expect(screen.getByText('ofd-page-0')).toBeInTheDocument())
  })
})
```

`frontend/src/components/ConvertOptions.test.tsx`:
```typescript
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConvertOptions } from './ConvertOptions'
import type { FileItem } from '../types/api'

afterEach(() => vi.unstubAllGlobals())

const ofdFile: FileItem = { file_id: 'f1', filename: 'a.ofd', size: 1, source_type: 'OFD', file: new File(['x'], 'a.ofd') }

describe('ConvertOptions', () => {
  it('lists formats for selected OFD file', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ofd: ['pdf', 'md'] }) }))
    render(<ConvertOptions selectedFile={ofdFile} onConvert={() => {}} converting={false} />)
    await waitFor(() => expect(screen.getByText('pdf')).toBeInTheDocument())
    expect(screen.getByText('md')).toBeInTheDocument()
  })

  it('opens lossy modal for md then confirms convert', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ofd: ['md'] }) }))
    // second fetch: getTask returns warning
    const onConvert = vi.fn()
    render(<ConvertOptions selectedFile={ofdFile} onConvert={onConvert} converting={false} />)
    await waitFor(() => expect(screen.getByText('md')).toBeInTheDocument())
    await userEvent.click(screen.getByText('md'))
    await userEvent.click(screen.getByRole('button', { name: /开始转换/ }))
    expect(await screen.findByText('OFD 转 Markdown 为结构推断，复杂版面可能有损，仅供参考')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /确认/ }))
    expect(onConvert).toHaveBeenCalledWith('md')
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/components/PreviewPanel.test.tsx src/components/ConvertOptions.test.tsx`
Expected: FAIL - components don't exist.

- [ ] **Step 3: Write PreviewPanel**

`frontend/src/components/PreviewPanel.tsx`:
```tsx
import { useEffect, useRef } from 'react'
import { Card, Pagination, Spin, Empty } from 'antd'
import { usePreview } from '../hooks/usePreview'
import type { FileItem } from '../types/api'

interface Props {
  file: FileItem | null
}

export function PreviewPanel({ file }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const { preview, loading, pages, currentPage, setCurrentPage } = usePreview()

  useEffect(() => {
    void preview(file)
  }, [file, preview])

  useEffect(() => {
    if (containerRef.current) {
      containerRef.current.innerHTML = ''
      const node = pages[currentPage]
      if (node) containerRef.current.appendChild(node)
    }
  }, [pages, currentPage])

  if (!file) {
    return <Card title="预览"><Empty description="请选择文件预览" /></Card>
  }
  if (file.source_type !== 'OFD') {
    return <Card title="预览"><Empty description="不支持预览" /></Card>
  }

  return (
    <Card title="预览" style={{ height: '100%', overflow: 'auto' }}>
      <Spin spinning={loading}>
        <div ref={containerRef} />
      </Spin>
      {pages.length > 1 && (
        <Pagination
          current={currentPage + 1}
          total={pages.length}
          pageSize={1}
          onChange={(p) => setCurrentPage(p - 1)}
          style={{ marginTop: 12, textAlign: 'center' }}
        />
      )}
    </Card>
  )
}
```

- [ ] **Step 4: Write ConvertOptions**

`frontend/src/components/ConvertOptions.tsx`:
```tsx
import { useEffect, useState } from 'react'
import { Card, Radio, Button, Space } from 'antd'
import { api } from '../api/client'
import { LossyWarningModal } from './LossyWarningModal'
import type { FileItem, FormatsResponse } from '../types/api'

const LOSSY = new Set(['docx', 'md'])
const LOSSY_WARNING: Record<string, string> = {
  docx: '版式转 DOCX 为有损转换，排版可能变化，仅供参考',
  md: 'OFD 转 Markdown 为结构推断，复杂版面可能有损，仅供参考',
}

interface Props {
  selectedFile: FileItem | null
  onConvert: (targetFormat: string) => void
  converting: boolean
}

export function ConvertOptions({ selectedFile, onConvert, converting }: Props) {
  const [formats, setFormats] = useState<FormatsResponse>({})
  const [target, setTarget] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)

  useEffect(() => {
    api.formats().then(setFormats).catch(() => {})
  }, [])

  const available = selectedFile ? formats[selectedFile.source_type.toLowerCase()] ?? [] : []

  function startConvert() {
    if (!target) return
    if (LOSSY.has(target)) {
      setModalOpen(true)
    } else {
      onConvert(target)
    }
  }

  return (
    <Card title="转换">
      <Space direction="vertical" style={{ width: '100%' }}>
        <Radio.Group value={target} onChange={(e) => setTarget(e.target.value)}>
          <Space wrap>
            {available.map((fmt) => <Radio key={fmt} value={fmt}>{fmt}</Radio>)}
          </Space>
        </Radio.Group>
        <Button type="primary" disabled={!target || converting} loading={converting} onClick={startConvert}>
          开始转换
        </Button>
      </Space>
      <LossyWarningModal
        open={modalOpen}
        warning={target ? LOSSY_WARNING[target] ?? '' : ''}
        onConfirm={() => { setModalOpen(false); if (target) onConvert(target) }}
        onCancel={() => setModalOpen(false)}
      />
    </Card>
  )
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/components/PreviewPanel.test.tsx src/components/ConvertOptions.test.tsx`
Expected: PASS (5 tests). Note `formats[selectedFile.source_type.toLowerCase()]` - source_type is `OFD` (uppercase), formats key is `ofd` (lowercase) - the `.toLowerCase()` aligns them.

- [ ] **Step 6: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git add frontend/src/components/PreviewPanel.tsx frontend/src/components/ConvertOptions.tsx frontend/src/components/PreviewPanel.test.tsx frontend/src/components/ConvertOptions.test.tsx
git commit -m "feat(frontend): add PreviewPanel + ConvertOptions components"
```

---

## Task 9: TaskList component + App assembly

**Files:**
- Create: `frontend/src/components/TaskList.tsx`, `frontend/src/components/TaskList.test.tsx`, `frontend/src/App.tsx` (replace), `frontend/src/main.tsx` (modify for AntD), `frontend/src/styles.css` (replace), `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: all hooks + components (Tasks 3-8), `useTaskPolling` (Task 4).
- Produces: the assembled single-page app.

- [ ] **Step 1: Write the failing tests**

`frontend/src/components/TaskList.test.tsx`:
```typescript
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TaskList } from './TaskList'
import type { TaskItem } from '../types/api'

const mk = (over: Partial<TaskItem> = {}): TaskItem => ({
  task_id: 't1', source_filename: 'a.ofd', target_format: 'pdf',
  status: 'done', download_url: '/api/download/t1', error: null, warning: null, ...over,
})

describe('TaskList', () => {
  it('shows download link for done task', () => {
    const onDownload = vi.fn()
    render(<TaskList tasks={[mk({ status: 'done' })]} onDownload={onDownload} />)
    expect(screen.getByRole('button', { name: /下载/ })).toBeInTheDocument()
  })

  it('shows error for failed task', () => {
    render(<TaskList tasks={[mk({ status: 'failed', error: '文件损坏' })]} onDownload={() => {}} />)
    expect(screen.getByText('文件损坏')).toBeInTheDocument()
  })

  it('shows warning tooltip text for lossy task', () => {
    render(<TaskList tasks={[mk({ status: 'done', warning: 'OFD 转 Markdown 为结构推断' })]} onDownload={() => {}} />)
    expect(screen.getByText('OFD 转 Markdown 为结构推断')).toBeInTheDocument()
  })

  it('calls onDownload', async () => {
    const onDownload = vi.fn()
    render(<TaskList tasks={[mk({ status: 'done' })]} onDownload={onDownload} />)
    await userEvent.click(screen.getByRole('button', { name: /下载/ }))
    expect(onDownload).toHaveBeenCalledWith('t1')
  })
})
```

`frontend/src/App.test.tsx`:
```typescript
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from './App'

afterEach(() => vi.unstubAllGlobals())

describe('App', () => {
  it('uploads, converts, polls to done, shows download', async () => {
    let uploadCall = 0
    vi.stubGlobal('fetch', vi.fn().mockImplementation((path: string) => {
      if (path.startsWith('/api/upload')) {
        return Promise.resolve({ ok: true, json: async () => ({ file_id: 'f1', filename: 'a.ofd', size: 1, source_type: 'OFD' }) })
      }
      if (path === '/api/formats') {
        return Promise.resolve({ ok: true, json: async () => ({ ofd: ['pdf'] }) })
      }
      if (path === '/api/convert') {
        return Promise.resolve({ ok: true, json: async () => ({ task_id: 't1', status: 'pending' }) })
      }
      if (path === '/api/task/t1') {
        uploadCall++
        return Promise.resolve({ ok: true, json: async () => ({ task_id: 't1', status: uploadCall < 2 ? 'pending' : 'done', download_url: '/api/download/t1', error: null, warning: null }) })
      }
      return Promise.resolve({ ok: true, json: async () => ({}) })
    }))

    render(<App />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await userEvent.upload(input, new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], 'a.ofd'))
    await waitFor(() => expect(screen.getByText('a.ofd')).toBeInTheDocument())
    await userEvent.click(screen.getByText('a.ofd'))
    await waitFor(() => expect(screen.getByText('pdf')).toBeInTheDocument())
    await userEvent.click(screen.getByText('pdf'))
    await userEvent.click(screen.getByRole('button', { name: /开始转换/ }))
    await waitFor(() => expect(screen.getByRole('button', { name: /下载/ })).toBeInTheDocument(), { timeout: 5000 })
  }, 10000)
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/components/TaskList.test.tsx src/App.test.tsx`
Expected: FAIL - TaskList/App don't exist.

- [ ] **Step 3: Write TaskList**

`frontend/src/components/TaskList.tsx`:
```tsx
import { List, Tag, Button, Tooltip, Space } from 'antd'

interface Props {
  tasks: import('../types/api').TaskItem[]
  onDownload: (taskId: string) => void
}

const statusMap: Record<string, { color: string; text: string }> = {
  pending: { color: 'default', text: '等待中' },
  processing: { color: 'processing', text: '转换中' },
  done: { color: 'success', text: '完成' },
  failed: { color: 'error', text: '失败' },
  timeout: { color: 'warning', text: '超时' },
}

export function TaskList({ tasks, onDownload }: Props) {
  return (
    <List
      bordered
      header={<strong>转换任务</strong>}
      locale={{ emptyText: '暂无任务' }}
      dataSource={tasks}
      renderItem={(t) => {
        const s = statusMap[t.status] ?? { color: 'default', text: t.status }
        return (
          <List.Item
            actions={t.status === 'done' ? [<Button key="dl" type="link" onClick={() => onDownload(t.task_id)}>下载</Button>] : []}
          >
            <Space>
              <span>{t.source_filename} → {t.target_format}</span>
              <Tag color={s.color}>{s.text}</Tag>
              {t.warning && <Tooltip title={t.warning}><Tag color="orange">有损</Tag></Tooltip>}
              {t.error && <span style={{ color: 'red' }}>{t.error}</span>}
            </Space>
          </List.Item>
        )
      }}
    />
  )
}
```

- [ ] **Step 4: Write App**

`frontend/src/App.tsx`:
```tsx
import { useState, useCallback } from 'react'
import { Layout, Typography } from 'antd'
import { UploadZone } from './components/UploadZone'
import { FileList } from './components/FileList'
import { PreviewPanel } from './components/PreviewPanel'
import { ConvertOptions } from './components/ConvertOptions'
import { TaskList } from './components/TaskList'
import { useConvert } from './hooks/useConvert'
import { useTaskPolling } from './hooks/useTaskPolling'
import { api } from './api/client'
import type { FileItem, TaskItem } from './types/api'

const { Header, Content } = Layout
const { Title } = Typography

export function App() {
  const [files, setFiles] = useState<FileItem[]>([])
  const [selectedFileId, setSelectedFileId] = useState<string | null>(null)
  const [tasks, setTasks] = useState<TaskItem[]>([])
  const { convert, converting } = useConvert()

  const selectedFile = files.find((f) => f.file_id === selectedFileId) ?? null

  const onUpdateTask = useCallback((t: TaskItem) => {
    setTasks((prev) => prev.map((x) => (x.task_id === t.task_id ? t : x)))
  }, [])
  useTaskPolling(tasks, onUpdateTask)

  const handleUploaded = useCallback((f: FileItem) => {
    setFiles((prev) => [...prev, f])
  }, [])

  const handleDelete = useCallback((fileId: string) => {
    setFiles((prev) => prev.filter((f) => f.file_id !== fileId))
    setSelectedFileId((prev) => (prev === fileId ? null : prev))
  }, [])

  const handleConvert = useCallback(async (targetFormat: string) => {
    if (!selectedFile) return
    const task = await convert(selectedFile.file_id, selectedFile.filename, targetFormat)
    if (task) setTasks((prev) => [task, ...prev])
  }, [selectedFile, convert])

  const handleDownload = useCallback((taskId: string) => {
    window.location.href = api.downloadUrl(taskId)
  }, [])

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header><Title level={3} style={{ color: 'white', margin: '12px 0' }}>OFD 转换工具</Title></Header>
      <Content style={{ padding: 24, maxWidth: 1000, margin: '0 auto', width: '100%' }}>
        <UploadZone onUploaded={handleUploaded} />
        <div style={{ display: 'flex', gap: 16, marginTop: 16 }}>
          <div style={{ flex: 1 }}><FileList files={files} selectedFileId={selectedFileId} onSelect={setSelectedFileId} onDelete={handleDelete} /></div>
          <div style={{ flex: 2 }}><PreviewPanel file={selectedFile} /></div>
        </div>
        <div style={{ marginTop: 16 }}><ConvertOptions selectedFile={selectedFile} onConvert={handleConvert} converting={converting} /></div>
        <div style={{ marginTop: 16 }}><TaskList tasks={tasks} onDownload={handleDownload} /></div>
      </Content>
    </Layout>
  )
}

export default App
```

- [ ] **Step 5: Wire AntD in main.tsx + styles**

Replace `frontend/src/main.tsx`:
```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import App from './App'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider locale={zhCN}>
      <App />
    </ConfigProvider>
  </StrictMode>,
)
```

Replace `frontend/src/styles.css`:
```css
body { margin: 0; }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/components/TaskList.test.tsx src/App.test.tsx`
Expected: PASS (5 tests).

- [ ] **Step 7: Run full test suite + commit**

Run: `cd frontend && npx vitest run`
Expected: all tests pass.
```bash
cd /home/alex/my_workspace/ofd-converter
git add frontend/src/
git commit -m "feat(frontend): assemble App with TaskList + all components"
```

---

## Task 10: Dockerfile + nginx.conf + docker-compose frontend service

**Files:**
- Create: `frontend/Dockerfile`, `frontend/nginx.conf`
- Modify: `docker-compose.yml` (add frontend service)

**Interfaces:**
- Produces: a buildable frontend Docker image serving dist/ via nginx, reverse-proxying /api to backend.

- [ ] **Step 1: Write Dockerfile**

`frontend/Dockerfile`:
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

- [ ] **Step 2: Write nginx.conf**

`frontend/nginx.conf`:
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

- [ ] **Step 3: Add frontend service to docker-compose.yml**

Read the existing `docker-compose.yml` (Plan 1). Add a `frontend` service alongside `backend`. Final structure:
```yaml
services:
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    volumes:
      - ofd-data:/data
    environment:
      - OFD_DATA_DIR=/data
      - OFD_DB_PATH=/data/converter.db
      - FILE_RETENTION_HOURS=24
      - LOG_RETENTION_DAYS=90
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:8080/health || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 30s
    restart: unless-stopped
  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      backend:
        condition: service_healthy
    restart: unless-stopped
volumes:
  ofd-data:
```

- [ ] **Step 4: Verify build (if Docker available)**

Run: `cd /home/alex/my_workspace/ofd-converter && docker compose build frontend 2>&1 | tail -5`
Expected: build succeeds (or note Docker unavailable - same caveat as Plan 1 Task 25).

- [ ] **Step 5: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git add frontend/Dockerfile frontend/nginx.conf docker-compose.yml
git commit -m "feat(frontend): add Dockerfile + nginx config + compose service"
```

---

## Task 11: README update

**Files:**
- Modify: `README.md` (root)

- [ ] **Step 1: Update README**

Add a frontend section to the existing `README.md` (Plan 1). After the backend dev section, add:
```markdown
## 前端开发

\`\`\`bash
cd frontend
npm install
npm run dev      # Vite dev server (:5173)，代理 /api -> localhost:8080
npm test         # Vitest
npm run build    # 构建到 dist/
\`\`\`

开发时需同时启动后端（`cd backend && mvn spring-boot:run`）。

## 部署（前后端）

\`\`\`bash
docker compose up --build -d
curl http://localhost/health          # 经 nginx 代理
# 前端: http://localhost
# 后端 API: http://localhost/api/...
\`\`\`
```

- [ ] **Step 2: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git add README.md
git commit -m "docs: add frontend dev/deploy section to README"
```

---

## Self-Review

**1. Spec coverage:**

| Spec section | Covered by | Status |
|---|---|---|
| §1 范围 (单页/上传/预览/转换/下载/有损提示) | Tasks 1,7,8,9 | ✅ |
| §1 项目结构 | Tasks 1-9 (file structure) | ✅ |
| §1 对接后端 API | Task 1 (api client) | ✅ |
| §1 运行关系 (dev proxy / prod nginx) | Task 1 (vite proxy) + Task 10 (nginx) | ✅ |
| §2 ofd.js PoC | Task 2 | ✅ (gate) |
| §3 API 客户端 + 类型 | Task 1 | ✅ |
| §4 组件设计 (6 组件) | Tasks 6,7,8,9 | ✅ |
| §4 状态流 | Task 9 (App) | ✅ |
| §5 hooks (4 个) | Tasks 3,4,5 | ✅ |
| §6 Docker + nginx + compose | Task 10 | ✅ |
| §6 XFF 透传 / 50MB / depends_on | Task 10 | ✅ |
| §7 测试 (hooks + 组件 + 集成) | Tasks 1-9 tests + App.test | ✅ |
| §7 验收标准 | Task 9 (App test = upload/convert/poll/download) | ✅ |
| §7 不测试项 (ofd.js 内部/AntD/nginx) | excluded | ✅ |

**2. Placeholder scan:** Searched for TBD/TODO/"implement later"/"add appropriate" - none in task deliverables. Task 2's ofd.js signature is explicitly a PoC hypothesis with a verified-by-running step; if it differs, the task says to adjust and record - not a placeholder. The `useConvert` test/impl arg-count mismatch is flagged inline (Task 3 Step 5) with the fix.

**3. Type consistency:** Checked across tasks:
- `FileItem { file_id, filename, size, source_type, file }` - consistent Tasks 1,3,5,7,8,9. `file: File` added in Task 1 for preview caching.
- `TaskItem { task_id, source_filename, target_format, status, download_url, error, warning }` - consistent Tasks 1,3,4,9.
- `api.upload/convert/getTask/downloadUrl/formats` - consistent Tasks 1,3,4,5,8,9.
- `useUpload(upload, uploading)`, `useConvert(convert(fileId, sourceFilename, targetFormat), converting)`, `useTaskPolling(tasks, onUpdate)`, `usePreview(preview, loading, pages, currentPage, setCurrentPage, clear)` - consistent across hook defs + consumers.
- `source_type.toLowerCase()` alignment (OFD->ofd) flagged in Task 8 Step 5.

**Risk:** ofd.js real API shape (Task 2) is the main unknown; the PoC is a gate with an explicit fallback path (escalate -> server-side HTML rendering).

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-06-ofd-converter-plan-3.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
