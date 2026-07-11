import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

describe('PreviewPoc', () => {
  it('parses and renders an OFD file into the container', async () => {
    // Mock ofd.js to the verified named-export shape: parseOfdDocument calls success
    // synchronously; getOFDPageCount returns 1; renderOfdByIndex returns a stub div.
    vi.doMock('ofd.js', () => ({
      parseOfdDocument: (config: { success?: () => void }) => { config.success?.() },
      getOFDPageCount: () => 1,
      renderOfdByIndex: (_di: number, pi: number) => {
        const d = document.createElement('div')
        d.textContent = `rendered-page-${pi}`
        return d
      },
    }))
    const { PreviewPoc } = await import('./PreviewPoc')
    render(<PreviewPoc />)
    const input = screen.getByTestId('ofd-input') as HTMLInputElement
    const file = new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], 'a.ofd', { type: 'application/octet-stream' })
    await userEvent.upload(input, file)
    await waitFor(() => {
      expect(screen.getByText('rendered-page-0')).toBeInTheDocument()
    })
    expect(screen.getByText(/页数: 1/)).toBeInTheDocument()
  })
})
