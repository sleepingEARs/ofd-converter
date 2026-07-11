import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import type { FileItem } from '../types/api'

// vi.mock is hoisted above imports, so usePreview's static `import ... from 'ofd.js'`
// gets the mock (doMock would be too late - the real module loads at import time).
vi.mock('ofd.js', () => ({
  parseOfdDocument: (config: { success?: () => void }) => { config.success?.() },
  getOFDPageCount: () => 1,
  renderOfdByIndex: (_di: number, pi: number) => {
    const d = document.createElement('div'); d.textContent = `ofd-page-${pi}`; return d
  },
}))

const { PreviewPanel } = await import('./PreviewPanel')

const ofdFile: FileItem = {
  file_id: 'f1', filename: 'a.ofd', size: 1, source_type: 'OFD',
  file: new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], 'a.ofd'),
}

describe('PreviewPanel', () => {
  it('shows placeholder for non-OFD file', () => {
    render(<PreviewPanel file={{ file_id: 'f1', filename: 'a.pdf', size: 1, source_type: 'PDF', file: new File(['x'], 'a.pdf') }} />)
    expect(screen.getByText('不支持预览')).toBeInTheDocument()
  })

  it('shows placeholder when no file selected', () => {
    render(<PreviewPanel file={null} />)
    expect(screen.getByText(/请选择/)).toBeInTheDocument()
  })

  it('renders ofd.js pages for an OFD file', async () => {
    render(<PreviewPanel file={ofdFile} />)
    await waitFor(() => expect(screen.getByText('ofd-page-0')).toBeInTheDocument())
  })
})

