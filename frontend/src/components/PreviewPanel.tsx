import { useEffect } from 'react'
import { Card, Pagination, Spin, Empty, Alert } from 'antd'
import { usePreview } from '../hooks/usePreview'
import type { FileItem } from '../types/api'

interface Props {
  file: FileItem | null
}

export function PreviewPanel({ file }: Props) {
  const { preview, loading, pages, currentPage, setCurrentPage, error } = usePreview()

  useEffect(() => {
    void preview(file)
  }, [file?.file_id, preview])

  if (!file) {
    return <Card title="预览" style={{ height: 842 }}><Empty description="请选择文件预览" /></Card>
  }
  if (file.source_type !== 'OFD') {
    return <Card title="预览" style={{ height: 842 }}><Empty description="不支持预览" /></Card>
  }

  return (
    <Card title="预览" style={{ height: 842, display: 'flex', flexDirection: 'column' }}
      styles={{ body: { flex: 1, overflow: 'hidden', padding: 8, display: 'flex', flexDirection: 'column' } }}
    >
      <div style={{ flex: 1, overflow: 'auto', display: 'flex', justifyContent: 'center', alignItems: 'flex-start' }}>
        <Spin spinning={loading}>
          {error ? (
            <Alert
              type="warning"
              message="预览失败"
              description={`${error}\n\n可能原因：该文件包含全图片内容、加密或特殊格式。建议转 PDF/PNG 查看原始版式。`}
              showIcon
            />
          ) : pages.length === 0 && !loading ? (
            <Alert
              type="info"
              message="无预览内容"
              description="该文件可能为纯图片内容（无文本图层），预览不可用。建议转 PDF/PNG 查看原始版式。"
              showIcon
            />
          ) : (
            pages[currentPage] && (
              <img
                src={pages[currentPage]}
                alt={`第 ${currentPage + 1} 页`}
                style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain', border: '1px solid #e0e0e0' }}
              />
            )
          )}
        </Spin>
      </div>
      {pages.length > 1 && (
        <Pagination
          current={currentPage + 1}
          total={pages.length}
          pageSize={1}
          onChange={(p) => setCurrentPage(p - 1)}
          style={{ marginTop: 4, textAlign: 'center', flexShrink: 0, paddingBottom: 4 }}
        />
      )}
    </Card>
  )
}
