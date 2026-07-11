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
