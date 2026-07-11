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
    // Depend on file_id (primitive) not the file object reference: App computes
    // selectedFile via files.find() each render, producing a new reference, which
    // would otherwise re-trigger ofd.js parsing on every poll-driven re-render.
  }, [file?.file_id, preview])

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
