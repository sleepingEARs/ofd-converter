import { describe, it, expect, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import type { FileItem } from '../types/api'

// vi.mock is hoisted above imports so usePreview's static ofd.js import gets the mock.
vi.mock('ofd.js', () => ({
  parseOfdDocument: (config: { success?: () => void }) => { config.success?.() },
  getOFDPageCount: () => 2,
  renderOfdByIndex: (_di: number, pi: number) => {
    const d = document.createElement('div'); d.textContent = `page-${pi}`; return d
  },
}))

const { usePreview } = await import('./usePreview')

const ofdFile = (name = 'a.ofd'): FileItem => ({
  file_id: 'f1', filename: name, size: 10, source_type: 'OFD',
  file: new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], name),
})

describe('usePreview', () => {
  it('renders pages for an OFD file', async () => {
    const { result } = renderHook(() => usePreview())
    await act(async () => { await result.current.preview(ofdFile()) })
    expect(result.current.pages.length).toBe(2)
    expect(result.current.loading).toBe(false)
  })

  it('shows placeholder for non-OFD file (no throw, empty pages)', async () => {
    const { result } = renderHook(() => usePreview())
    await act(async () => {
      await result.current.preview({ ...ofdFile('a.pdf'), source_type: 'PDF' })
    })
    expect(result.current.pages).toHaveLength(0)
  })
})
