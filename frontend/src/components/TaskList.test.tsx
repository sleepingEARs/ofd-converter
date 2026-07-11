import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TaskList } from './TaskList'
import type { TaskItem } from '../types/api'

const mk = (over: Partial<TaskItem> = {}): TaskItem => ({
  task_id: 't1', source_filename: 'a.ofd', target_format: 'pdf',
  status: 'done', download_url: '/api/download/t1', error: null, warning: null, ...over,
})

describe('TaskList', () => {
  it('shows download link for done task', () => {
    render(<TaskList tasks={[mk({ status: 'done' })]} onDownload={() => {}} />)
    expect(screen.getByRole('button', { name: /下载/ })).toBeInTheDocument()
  })

  it('shows error for failed task', () => {
    render(<TaskList tasks={[mk({ status: 'failed', error: '文件损坏' })]} onDownload={() => {}} />)
    expect(screen.getByText('文件损坏')).toBeInTheDocument()
  })

  it('shows lossy tag for task with warning', () => {
    render(<TaskList tasks={[mk({ status: 'done', warning: 'OFD 转 Markdown 为结构推断' })]} onDownload={() => {}} />)
    // Tooltip title only renders on hover; assert the visible "有损" tag instead.
    expect(screen.getByText('有损')).toBeInTheDocument()
  })

  it('calls onDownload', async () => {
    const onDownload = vi.fn()
    render(<TaskList tasks={[mk({ status: 'done' })]} onDownload={onDownload} />)
    await userEvent.click(screen.getByRole('button', { name: /下载/ }))
    expect(onDownload).toHaveBeenCalledWith('t1')
  })
})
