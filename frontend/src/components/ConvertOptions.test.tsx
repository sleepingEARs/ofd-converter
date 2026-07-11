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

  it('opens lossy modal for md then confirms convert', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ofd: ['md'] }) }))
    const onConvert = vi.fn()
    render(<ConvertOptions selectedFile={ofdFile} onConvert={onConvert} converting={false} />)
    await waitFor(() => expect(screen.getByText('md')).toBeInTheDocument())
    await userEvent.click(screen.getByText('md'))
    await userEvent.click(screen.getByRole('button', { name: /开始转换/ }))
    expect(await screen.findByText('OFD 转 Markdown 为结构推断，复杂版面可能有损，仅供参考')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /确\s?认/ }))
    expect(onConvert).toHaveBeenCalledWith('md')
  })
})
