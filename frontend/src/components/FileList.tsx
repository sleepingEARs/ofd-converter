import { List, Tag, Button } from 'antd'
import type { FileItem } from '../types/api'

interface Props {
  files: FileItem[]
  selectedFileId: string | null
  onSelect: (fileId: string) => void
  onDelete: (fileId: string) => void
}

const typeColor: Record<string, string> = { OFD: 'blue', PDF: 'red', IMAGE: 'green', DOCX: 'orange' }

export function FileList({ files, selectedFileId, onSelect, onDelete }: Props) {
  return (
    <List
      bordered
      dataSource={files}
      locale={{ emptyText: '暂无文件' }}
      renderItem={(f) => (
        <List.Item
          style={{ background: f.file_id === selectedFileId ? '#e6f4ff' : undefined, cursor: 'pointer' }}
          onClick={() => onSelect(f.file_id)}
          actions={[
            <Button key="del" type="link" danger size="small" onClick={(e) => { e.stopPropagation(); onDelete(f.file_id) }}>删除</Button>,
          ]}
        >
          <List.Item.Meta
            title={f.filename}
            description={<><Tag color={typeColor[f.source_type] ?? 'default'}>{f.source_type}</Tag>{(f.size / 1024).toFixed(1)} KB</>}
          />
        </List.Item>
      )}
    />
  )
}
