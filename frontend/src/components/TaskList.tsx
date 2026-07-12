import { List, Tag, Button, Tooltip, Space, Pagination } from 'antd'
import { useState } from 'react'
import type { TaskItem } from '../types/api'

interface Props {
  tasks: TaskItem[]
  onDownload: (taskId: string) => void
}

const PAGE_SIZE = 10

const statusMap: Record<string, { color: string; text: string }> = {
  pending: { color: 'default', text: '等待中' },
  processing: { color: 'processing', text: '转换中' },
  done: { color: 'success', text: '完成' },
  failed: { color: 'error', text: '失败' },
  timeout: { color: 'warning', text: '超时' },
}

export function TaskList({ tasks, onDownload }: Props) {
  const [page, setPage] = useState(1)
  const totalPages = Math.ceil(tasks.length / PAGE_SIZE)
  const currentPage = Math.min(page, Math.max(1, totalPages))
  const start = (currentPage - 1) * PAGE_SIZE
  const pageTasks = tasks.slice(start, start + PAGE_SIZE)

  return (
    <div>
      <List
        bordered
        header={<strong>转换任务 ({tasks.length})</strong>}
        locale={{ emptyText: '暂无任务' }}
        dataSource={pageTasks}
        renderItem={(t) => {
          const s = statusMap[t.status] ?? { color: 'default', text: t.status }
          return (
            <List.Item
              actions={t.status === 'done' ? [<Button key="dl" type="link" onClick={() => onDownload(t.task_id)}>下载</Button>] : []}
            >
              <Space>
                <span>{t.source_filename} -&gt; {t.target_format}</span>
                <Tag color={s.color}>{s.text}</Tag>
                {t.warning && <Tooltip title={t.warning}><Tag color="orange">有损</Tag></Tooltip>}
                {t.error && <span style={{ color: 'red' }}>{t.error}</span>}
              </Space>
            </List.Item>
          )
        }}
      />
      {totalPages > 1 && (
        <Pagination
          current={currentPage}
          total={tasks.length}
          pageSize={PAGE_SIZE}
          onChange={setPage}
          style={{ marginTop: 8, textAlign: 'center' }}
        />
      )}
    </div>
  )
}
