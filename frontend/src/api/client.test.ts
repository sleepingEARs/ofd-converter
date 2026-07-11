import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { api } from './client'

describe('api client', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => { vi.unstubAllGlobals() })

  it('formats() returns grouped formats', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ ofd: ['pdf', 'md'], pdf: ['ofd'] }),
    })
    const f = await api.formats()
    expect(f).toEqual({ ofd: ['pdf', 'md'], pdf: ['ofd'] })
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/formats', undefined)
  })

  it('getTask returns task', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ task_id: 't1', status: 'done', download_url: '/api/download/t1', error: null, warning: null }),
    })
    const t = await api.getTask('t1')
    expect(t.status).toBe('done')
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/task/t1', undefined)
  })

  it('throws on non-ok with backend error message', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      json: async () => ({ error: { code: 'FILE_TOO_LARGE', message: '超过 50MB 限制' } }),
    })
    await expect(api.formats()).rejects.toThrow('超过 50MB 限制')
  })

  it('downloadUrl builds path', () => {
    expect(api.downloadUrl('t1')).toBe('/api/download/t1')
  })

  it('upload posts FormData', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ file_id: 'f1', filename: 'a.ofd', size: 10, source_type: 'OFD' }),
    })
    const file = new File(['x'], 'a.ofd', { type: 'application/octet-stream' })
    const r = await api.upload(file)
    expect(r.file_id).toBe('f1')
    const [path, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(path).toBe('/api/upload')
    expect((init as RequestInit).method).toBe('POST')
    expect(((init as RequestInit).body as FormData).get('file')).toBe(file)
  })
})