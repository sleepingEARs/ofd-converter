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
      // convert() is async on the backend: it returns a PENDING task_id immediately and the
      // conversion runs in the background. The returned task is added to the task list and
      // polled to completion by useTaskPolling. Do NOT fetch /api/task here - it would always
      // be pending and is a wasted request. The lossy-conversion warning (if any) is carried
      // in the convert response so it can be shown immediately.
      return {
        task_id: res.task_id,
        source_filename: sourceFilename,
        target_format: targetFormat,
        status: res.status,
        download_url: null,
        error: null,
        warning: res.warning,
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '转换失败')
      return null
    } finally {
      setConverting(false)
    }
  }

  return { convert, converting }
}
