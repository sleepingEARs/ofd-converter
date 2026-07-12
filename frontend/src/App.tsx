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

  const handleDownload = useCallback(async (taskId: string) => {
    // Use fetch + blob + <a download> to force a download dialog (not inline display)
    // and avoid navigation/garbled-text issues with window.location.href.
    try {
      const res = await fetch(api.downloadUrl(taskId))
      if (!res.ok) throw new Error('下载失败')
      const blob = await res.blob()
      // Extract filename from Content-Disposition header (RFC 5987 filename*=UTF-8'').
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
