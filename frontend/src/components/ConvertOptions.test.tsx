import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConvertOptions } from './ConvertOptions'
import type { FileItem } from '../types/api'

afterEach(() => vi.unstubAllGlobals())

const ofdFile: FileItem = { file_id: 'f1', filename: 'a.ofd', size: 1, source_type: 'OFD', file: new File(['x'], 'a.ofd') }

describe('ConvertOptions', () => {
  it('lists formats for selected OFD file', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ofd: ['pdf', 'md'] }) }))
    render(<ConvertOptions selectedFile={ofdFile} onConvert={() => {}} converting={false} />)
    await waitFor(() => expect(screen.getByText('pdf')).toBeInTheDocument())
    expect(screen.getByText('md')).toBeInTheDocument()
  })

  it('shows lossy warning for md and converts on click', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ofd: ['md'] }) }))
    const onConvert = vi.fn()
    render(<ConvertOptions selectedFile={ofdFile} onConvert={onConvert} converting={false} />)
    await waitFor(() => expect(screen.getByText('md')).toBeInTheDocument())
    await userEvent.click(screen.getByText('md'))
    // Selecting a lossy format shows an inline warning (no modal).
    expect(await screen.findByText(/有损转换/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /开始转换/ }))
    expect(onConvert).toHaveBeenCalledWith('md')
  })
})
