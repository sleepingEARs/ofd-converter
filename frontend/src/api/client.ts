import type {
  FormatsResponse, UploadResponse, ConvertRequest, ConvertResponse, TaskResponse,
  AdminLogsParams, AdminLogsResponse,
} from '../types/api'

const BASE = '/api'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, init)
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.error?.message ?? '请求失败')
  }
  if (res.status === 204) return undefined as unknown as T
  return res.json()
}

function getToken(): string {
  return sessionStorage.getItem('admin_token') || ''
}

export function clearAdminToken(): void {
  sessionStorage.removeItem('admin_token')
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