import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { App } from './App'

// Mock ofd.js so PreviewPanel doesn't load the real (UMD) module in jsdom.
vi.mock('ofd.js', () => ({
  parseOfdDocument: (config: { success?: () => void }) => { config.success?.() },
  getOFDPageCount: () => 0,
  renderOfdByIndex: () => document.createElement('div'),
}))

afterEach(() => vi.unstubAllGlobals())

describe('App', () => {
  it('uploads, converts, polls to done, shows download', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let taskCall = 0
    vi.stubGlobal('fetch', vi.fn().mockImplementation((path: string) => {
      if (path.startsWith('/api/upload')) {
        return Promise.resolve({ ok: true, json: async () => ({ file_id: 'f1', filename: 'a.ofd', size: 1, source_type: 'OFD' }) })
      }
      if (path === '/api/formats') {
        return Promise.resolve({ ok: true, json: async () => ({ ofd: ['pdf'] }) })
      }
      if (path === '/api/convert') {
        return Promise.resolve({ ok: true, json: async () => ({ task_id: 't1', status: 'pending' }) })
      }
      if (path === '/api/task/t1') {
        taskCall++
        return Promise.resolve({ ok: true, json: async () => ({ task_id: 't1', status: taskCall < 2 ? 'pending' : 'done', download_url: '/api/download/t1', error: null, warning: null }) })
      }
      return Promise.resolve({ ok: true, json: async () => ({}) })
    }))

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    render(<MemoryRouter><App /></MemoryRouter>)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], 'a.ofd'))
    await waitFor(() => expect(screen.getByText('a.ofd')).toBeInTheDocument())
    await user.click(screen.getByText('a.ofd'))
    await waitFor(() => expect(screen.getByText('pdf')).toBeInTheDocument())
    await user.click(screen.getByText('pdf'))
    await user.click(screen.getByRole('button', { name: /开始转换/ }))
    await waitFor(() => expect(screen.getByRole('button', { name: /下载/ })).toBeInTheDocument(), { timeout: 5000 })
    vi.useRealTimers()
  }, 15000)
})
