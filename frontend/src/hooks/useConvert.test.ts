import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useConvert } from './useConvert'

afterEach(() => vi.unstubAllGlobals())

describe('useConvert', () => {
  it('returns a TaskItem on success', async () => {
    // convert() response carries the warning directly; no second /api/task fetch.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        task_id: 't1',
        status: 'pending',
        warning: 'OFD 转 Markdown 为结构推断，复杂版面可能有损，仅供参考',
      }),
    }))
    const { result } = renderHook(() => useConvert())
    let task: Awaited<ReturnType<typeof result.current.convert>>
    await act(async () => { task = await result.current.convert('f1', 'a.ofd', 'md') })
    expect(task!.task_id).toBe('t1')
    expect(task!.warning).toContain('Markdown')
    expect(task!.status).toBe('pending')
  })

  it('returns null on failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ error: { code: 'X', message: '失败' } }),
    }))
    const { result } = renderHook(() => useConvert())
    let task: Awaited<ReturnType<typeof result.current.convert>> | null = null
    await act(async () => { task = await result.current.convert('f1', 'a.ofd', 'pdf') })
    expect(task).toBeNull()
  })
})
