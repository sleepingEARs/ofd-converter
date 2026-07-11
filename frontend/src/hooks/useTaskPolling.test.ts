import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useTaskPolling } from './useTaskPolling'
import type { TaskItem } from '../types/api'

afterEach(() => vi.unstubAllGlobals())

const mkTask = (over: Partial<TaskItem> = {}): TaskItem => ({
  task_id: 't1', source_filename: 'a.ofd', target_format: 'pdf',
  status: 'pending', download_url: null, error: null, warning: null, ...over,
})

describe('useTaskPolling', () => {
  it('does not poll when all tasks are terminal', () => {
    vi.stubGlobal('fetch', vi.fn())
    const onUpdate = vi.fn()
    renderHook(() => useTaskPolling([mkTask({ status: 'done' })], onUpdate))
    expect(vi.mocked(fetch)).not.toHaveBeenCalled()
  })

  it('polls non-terminal tasks and stops on terminal', async () => {
    vi.useFakeTimers()
    let call = 0
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => {
        call++
        return { task_id: 't1', status: call < 2 ? 'processing' : 'done', download_url: '/api/download/t1', error: null, warning: null }
      },
    }))
    const onUpdate = vi.fn()
    const { rerender } = renderHook(({ tasks }) => useTaskPolling(tasks, onUpdate), {
      initialProps: { tasks: [mkTask({ status: 'pending' })] },
    })
    await act(async () => { await vi.advanceTimersByTimeAsync(2000) })
    expect(onUpdate).toHaveBeenCalled()
    rerender({ tasks: [mkTask({ status: 'done' })] })
    const callsAfterDone = vi.mocked(fetch).mock.calls.length
    await act(async () => { await vi.advanceTimersByTimeAsync(4000) })
    expect(vi.mocked(fetch).mock.calls.length).toBe(callsAfterDone)
    vi.useRealTimers()
  })
})
