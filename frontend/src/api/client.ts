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