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
