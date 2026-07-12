import { useState, useCallback } from 'react'
import { Layout, Typography, message } from 'antd'
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
  const [checkedIds, setCheckedIds] = useState<Set<string>>(new Set())
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
    setCheckedIds((prev) => { const n = new Set(prev); n.delete(fileId); return n })
  }, [])

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

  // Single-file convert (no checkboxes checked).
  const handleConvert = useCallback(async (targetFormat: string) => {
    if (checkedIds.size > 0) {
      // Batch convert: convert each checked file, then zip results.
      const checkedFiles = files.filter((f) => checkedIds.has(f.file_id))
      if (checkedFiles.length === 0) return

      message.loading({ content: `正在批量转换 ${checkedFiles.length} 个文件...`, key: 'batch', duration: 0 })

      const taskIds: string[] = []
      for (const f of checkedFiles) {
        const task = await convert(f.file_id, f.filename, targetFormat)
        if (task) {
          taskIds.push(task.task_id)
          setTasks((prev) => [task, ...prev])
        }
      }

      // Wait for all tasks to complete, then download as zip.
      // Poll each task until done/failed, then fetch results and zip.
      const maxWait = 300 // max poll iterations (300 * 2s = 10 min)
      let waited = 0
      const checkAllDone = async () => {
        while (waited < maxWait) {
          waited++
          const statuses = await Promise.all(taskIds.map(async (tid) => {
            try {
              const res = await fetch(`/api/task/${tid}`)
              const d = await res.json()
              return d.status
            } catch { return 'pending' }
          }))
          const allDone = statuses.every((s) => s === 'done' || s === 'failed' || s === 'timeout')
          if (allDone) break
          await new Promise((r) => setTimeout(r, 2000))
        }

        // Collect all completed results and download as zip.
        const blobs: { name: string; blob: Blob }[] = []
        for (const tid of taskIds) {
          try {
            const res = await fetch(api.downloadUrl(tid))
            if (!res.ok) continue
            const blob = await res.blob()
            const cd = res.headers.get('Content-Disposition') || ''
            const m = cd.match(/filename\*=UTF-8''([^;]+)/) || cd.match(/filename="?([^";]+)"?/)
            const name = m ? decodeURIComponent(m[1]) : `${tid}`
            blobs.push({ name, blob })
          } catch { /* skip failed */ }
        }

        if (blobs.length === 0) {
          message.error({ content: '批量转换失败，无可用文件', key: 'batch' })
          return
        }

        // Create zip in browser using a simple approach (JSZip not available, use blob concat fallback).
        // Download each file individually if zip not possible.
        for (const { name, blob } of blobs) {
          const url = URL.createObjectURL(blob)
          const a = document.createElement('a')
          a.href = url
          a.download = name
          document.body.appendChild(a)
          a.click()
          document.body.removeChild(a)
          URL.revokeObjectURL(url)
        }

        message.success({ content: `批量转换完成，已下载 ${blobs.length} 个文件`, key: 'batch' })
        // Clear checkboxes after batch.
        setCheckedIds(new Set())
      }

      checkAllDone()
      return
    }

    // Single file convert (existing behavior).
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
      a.href = url
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    } catch (e) {
      console.error('download failed', e)
    }
  }, [])

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header><Title level={3} style={{ color: 'white', margin: '12px 0' }}>OFD 转换工具</Title></Header>
      <Content style={{ padding: 24, maxWidth: 1000, margin: '0 auto', width: '100%' }}>
        <UploadZone onUploaded={handleUploaded} />
        <div style={{ display: 'flex', gap: 16, marginTop: 16 }}>
          <div style={{ flex: 1 }}>
            <FileList
              files={files}
              selectedFileId={selectedFileId}
              onSelect={setSelectedFileId}
              onDelete={handleDelete}
              checkedIds={checkedIds}
              onToggleCheck={handleToggleCheck}
              onToggleAll={handleToggleAll}
              allChecked={allChecked}
            />
          </div>
          <div style={{ flex: 2 }}><PreviewPanel file={selectedFile} /></div>
        </div>
        <div style={{ marginTop: 16 }}>
          <ConvertOptions
            selectedFile={selectedFile}
            onConvert={handleConvert}
            converting={converting}
            checkedCount={checkedIds.size}
          />
        </div>
        <div style={{ marginTop: 16 }}><TaskList tasks={tasks} onDownload={handleDownload} /></div>
      </Content>
    </Layout>
  )
}

export default App
