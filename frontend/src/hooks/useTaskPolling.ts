import { useEffect, useRef } from 'react'
import { api } from '../api/client'
import type { TaskItem } from '../types/api'

const POLL_INTERVAL = 2000
const TERMINAL = new Set(['done', 'failed', 'timeout'])

export function useTaskPolling(tasks: TaskItem[], onUpdate: (task: TaskItem) => void) {
  const onUpdateRef = useRef(onUpdate)
  onUpdateRef.current = onUpdate
  const tasksRef = useRef(tasks)
  tasksRef.current = tasks

  // Dependency key = sorted non-terminal task IDs. Drives effect restart when the
  // set of in-flight tasks changes; the effect body reads tasksRef.current for fresh data.
  const pendingKey = tasks
    .filter((t) => !TERMINAL.has(t.status))
    .map((t) => t.task_id)
    .sort()
    .join(',')

  useEffect(() => {
    if (!pendingKey) return
    let cancelled = false
    let timer: ReturnType<typeof setTimeout> | undefined

    async function tick() {
      if (cancelled) return
      const current = tasksRef.current
      for (const t of current) {
        if (cancelled) return
        if (TERMINAL.has(t.status)) continue
        try {
          const r = await api.getTask(t.task_id)
          if (!cancelled) {
            onUpdateRef.current({ ...t, status: r.status, download_url: r.download_url, error: r.error, warning: r.warning })
          }
        } catch {
          // swallow; next tick retries
        }
      }
      // Schedule the next tick only after this one finishes, so polls never overlap.
      if (!cancelled) {
        timer = setTimeout(() => { void tick() }, POLL_INTERVAL)
      }
    }

    void tick()
    return () => {
      cancelled = true
      if (timer) clearTimeout(timer)
    }
  }, [pendingKey])
}
