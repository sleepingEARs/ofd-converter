import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import type { FileItem } from '../types/api'

const setCurrentPage = vi.fn()

vi.mock('../hooks/usePreview', () => ({
  usePreview: () => ({
    preview: vi.fn(),
    loading: false,
    pages: ['/api/preview/f1?page=0', '/api/preview/f1?page=1'],
    currentPage: 0,
    setCurrentPage,
    error: null,
  }),
}))

const { PreviewPanel } = await import('./PreviewPanel')

const ofdFile: FileItem = {
  file_id: 'f1',
  filename: 'a.ofd',
  size: 1,
  source_type: 'OFD',
  file: new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], 'a.ofd'),
}

const pdfFile: FileItem = {
  file_id: 'f2',
  filename: 'a.pdf',
  size: 1,
  source_type: 'PDF',
  file: new File(['x'], 'a.pdf'),
}

describe('PreviewPanel', () => {
  beforeEach(() => {
    setCurrentPage.mockClear()
  })

  it('shows placeholder when no file selected', () => {
    render(<PreviewPanel file={null} />)
    expect(screen.getByText(/请选择/)).toBeInTheDocument()
  })

  it('shows placeholder for non-OFD file', () => {
    render(<PreviewPanel file={pdfFile} />)
    expect(screen.getByText('不支持预览')).toBeInTheDocument()
  })

  it('renders preview image for an OFD file', () => {
    render(<PreviewPanel file={ofdFile} />)
    expect(screen.getByAltText('第 1 页')).toHaveAttribute('src', '/api/preview/f1?page=0')
  })

  it('opens a modal when preview image is clicked', async () => {
    render(<PreviewPanel file={ofdFile} />)
    fireEvent.click(screen.getByAltText('第 1 页'))
    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })
  })

  it('syncs page index when paginating inside modal', async () => {
    render(<PreviewPanel file={ofdFile} />)
    fireEvent.click(screen.getByAltText('第 1 页'))
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument())

    const nextBtn = screen.getAllByLabelText('right').pop()
    expect(nextBtn).toBeDefined()
    fireEvent.click(nextBtn!)

    expect(setCurrentPage).toHaveBeenCalledWith(1)
  })
})
