import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useUpload } from './useUpload'

afterEach(() => vi.unstubAllGlobals())

describe('useUpload', () => {
  it('calls onSuccess on success and toggles uploading', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ file_id: 'f1', filename: 'a.ofd', size: 10, source_type: 'OFD' }),
    }))
    const onSuccess = vi.fn()
    const { result } = renderHook(() => useUpload(onSuccess))
    expect(result.current.uploading).toBe(false)
    let p: Promise<void>
    act(() => { p = result.current.upload(new File(['x'], 'a.ofd')) })
    expect(result.current.uploading).toBe(true)
    await act(async () => { await p! })
    expect(result.current.uploading).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith(expect.objectContaining({ file_id: 'f1', source_type: 'OFD' }))
  })

  it('does not call onSuccess on failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ error: { code: 'X', message: '失败' } }),
    }))
    const onSuccess = vi.fn()
    const { result } = renderHook(() => useUpload(onSuccess))
    await act(async () => { await result.current.upload(new File(['x'], 'a.ofd')) })
    expect(onSuccess).not.toHaveBeenCalled()
  })
})
