import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FileList } from './FileList'
import type { FileItem } from '../types/api'

const mk = (over: Partial<FileItem> = {}): FileItem => ({
  file_id: 'f1', filename: 'a.ofd', size: 10, source_type: 'OFD',
  file: new File(['x'], 'a.ofd'), ...over,
})

const baseProps = {
  selectedFileId: null as string | null,
  onSelect: () => {},
  onDelete: () => {},
  checkedIds: new Set<string>(),
  onToggleCheck: () => {},
  onToggleAll: () => {},
  allChecked: false,
  onBatchDelete: () => {},
}

describe('FileList', () => {
  it('renders files and selects on click', async () => {
    const onSelect = vi.fn()
    render(<FileList {...baseProps} onSelect={onSelect} files={[mk({ file_id: 'f1', filename: 'a.ofd' }), mk({ file_id: 'f2', filename: 'b.ofd' })]} />)
    expect(screen.getByText('a.ofd')).toBeInTheDocument()
    expect(screen.getByText('b.ofd')).toBeInTheDocument()
    await userEvent.click(screen.getByText('a.ofd'))
    expect(onSelect).toHaveBeenCalledWith('f1')
  })

  it('calls onDelete on delete click', async () => {
    const onDelete = vi.fn()
    render(<FileList {...baseProps} onDelete={onDelete} files={[mk({ file_id: 'f1', filename: 'a.ofd' })]} />)
    await userEvent.click(screen.getByRole('button', { name: /删除/ }))
    expect(onDelete).toHaveBeenCalledWith('f1')
  })
})
