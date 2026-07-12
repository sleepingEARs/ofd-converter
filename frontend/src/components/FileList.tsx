import { List, Tag, Button, Checkbox } from 'antd'
import type { FileItem } from '../types/api'

interface Props {
  files: FileItem[]
  selectedFileId: string | null
  onSelect: (fileId: string) => void
  onDelete: (fileId: string) => void
  checkedIds: Set<string>
  onToggleCheck: (fileId: string) => void
  onToggleAll: () => void
  allChecked: boolean
}

const typeColor: Record<string, string> = { OFD: 'blue', PDF: 'red', IMAGE: 'green', DOCX: 'orange' }

function formatSize(bytes: number): string {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`
}

export function FileList({ files, selectedFileId, onSelect, onDelete, checkedIds, onToggleCheck, onToggleAll, allChecked }: Props) {
  return (
    <List
      bordered
      dataSource={files}
      locale={{ emptyText: '暂无文件' }}
      style={{ maxHeight: 500, overflow: 'auto' }}
      header={
        files.length > 0 ? (
          <Checkbox checked={allChecked} onChange={onToggleAll}>全选</Checkbox>
        ) : undefined
      }
      renderItem={(f) => (
        <List.Item
          style={{ background: f.file_id === selectedFileId ? '#e6f4ff' : undefined, cursor: 'pointer' }}
          onClick={() => onSelect(f.file_id)}
          actions={[
            <Button key="del" type="link" danger size="small" onClick={(e) => { e.stopPropagation(); onDelete(f.file_id) }}>删除</Button>,
          ]}
        >
          <Checkbox
            checked={checkedIds.has(f.file_id)}
            onChange={(e) => { e.stopPropagation(); onToggleCheck(f.file_id) }}
            style={{ marginRight: 8 }}
            onClick={(e) => e.stopPropagation()}
          />
          <List.Item.Meta
            title={f.filename}
            description={<><Tag color={typeColor[f.source_type] ?? 'default'}>{f.source_type}</Tag>{formatSize(f.size)}</>}
          />
        </List.Item>
      )}
    />
  )
}
