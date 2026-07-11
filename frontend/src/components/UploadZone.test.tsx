import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UploadZone } from './UploadZone'

afterEach(() => vi.unstubAllGlobals())

describe('UploadZone', () => {
  it('uploads a file and calls onUploaded', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ file_id: 'f1', filename: 'a.ofd', size: 10, source_type: 'OFD' }),
    }))
    const onUploaded = vi.fn()
    render(<UploadZone onUploaded={onUploaded} />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await userEvent.upload(input, new File(['x'], 'a.ofd'))
    await waitFor(() => expect(onUploaded).toHaveBeenCalledWith(expect.objectContaining({ file_id: 'f1' })))
  })
})
