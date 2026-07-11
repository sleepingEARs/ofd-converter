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