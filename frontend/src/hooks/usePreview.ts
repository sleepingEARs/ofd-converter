import { useState, useCallback } from 'react'
import { parseOfdDocument, getOFDPageCount, renderOfdByIndex } from 'ofd.js'
import type { FileItem } from '../types/api'

/**
 * ofd.js preview hook. Uses the verified ofd.js 1.5.0 named-export API:
 *   parseOfdDocument({ ofd, success, fail })  (success receives base64 data, ignored here)
 *   getOFDPageCount(docIdx) -> number
 *   renderOfdByIndex(docIdx, pageIdx, dpi?=96) -> HTMLElement
 */
export function usePreview() {
  const [loading, setLoading] = useState(false)
  const [pages, setPages] = useState<HTMLElement[]>([])
  const [currentPage, setCurrentPage] = useState(0)

  const preview = useCallback(async (file: FileItem | null) => {
    setPages([])
    setCurrentPage(0)
    if (!file || file.source_type !== 'OFD') {
      return
    }
    setLoading(true)
    try {
      await new Promise<void>((resolve, reject) => {
        parseOfdDocument({
          ofd: file.file,
          success: () => {
            try {
              const count = getOFDPageCount(0)
              const nodes = Array.from({ length: count }, (_, i) => renderOfdByIndex(0, i, 96))
              setPages(nodes)
              resolve()
            } catch (e) {
              reject(e)
            }
          },
          fail: (err) => reject(err),
        })
      })
    } catch {
      setPages([])
    } finally {
      setLoading(false)
    }
  }, [])

  return { preview, loading, pages, currentPage, setCurrentPage }
}
