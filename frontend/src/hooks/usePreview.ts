import { useState, useCallback } from 'react'
import type { FileItem } from '../types/api'

/**
 * Preview hook - server-side rendering. OFD preview via backend OFD->PNG conversion
 * (ofd.js 1.5.0 requires a paid license, so we render server-side with ofdrw instead).
 * Fetches page images from /api/preview/{file_id}?page=N.
 */
export function usePreview() {
  const [loading, setLoading] = useState(false)
  const [pages, setPages] = useState<string[]>([])  // image URLs
  const [currentPage, setCurrentPage] = useState(0)
  const [error, setError] = useState<string | null>(null)

  const preview = useCallback(async (file: FileItem | null) => {
    setPages([])
    setCurrentPage(0)
    setError(null)
    if (!file || file.source_type !== 'OFD') {
      return
    }
    setLoading(true)
    try {
      const res = await fetch(`/api/preview/${file.file_id}`)
      if (!res.ok) {
        const body = await res.json().catch(() => null)
        throw new Error(body?.error?.message ?? '预览失败')
      }
      const data = await res.json()
      const pageUrls = (data.pages as string[]).map((p) => `/api/preview/${file.file_id}?page=${p}`)
      setPages(pageUrls)
    } catch (e) {
      setError((e as Error).message)
      setPages([])
    } finally {
      setLoading(false)
    }
  }, [])

  return { preview, loading, pages, currentPage, setCurrentPage, error }
}
