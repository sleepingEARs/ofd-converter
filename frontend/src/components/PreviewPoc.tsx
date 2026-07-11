import { useRef, useState } from 'react'
import { parseOfdDocument, getOFDPageCount, renderOfdByIndex } from 'ofd.js'

/**
 * PoC: proves ofd.js 1.5.0 named exports work inside a React 19 component.
 * Verified API (from minified source inspection):
 *   parseOfdDocument({ ofd: File|Blob|ArrayBuffer|url, success, fail, secret?, digest? })
 *   getOFDPageCount(docIdx) -> number
 *   renderOfdByIndex(docIdx, pageIdx, dpi?) -> HTMLElement (a <div>)
 */
export function PreviewPoc() {
  const containerRef = useRef<HTMLDivElement>(null)
  const [pageCount, setPageCount] = useState<number | null>(null)

  function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file || !containerRef.current) return
    containerRef.current.innerHTML = ''
    parseOfdDocument({
      ofd: file,
      success: () => {
        const count = getOFDPageCount(0)
        setPageCount(count)
        for (let i = 0; i < count; i++) {
          const node = renderOfdByIndex(0, i, 96)
          containerRef.current!.appendChild(node)
        }
      },
      fail: (err) => {
        setPageCount(null)
        console.error('ofd.js parse failed', err)
      },
    })
  }

  return (
    <div>
      <input data-testid="ofd-input" type="file" accept=".ofd" onChange={onFile} />
      <div ref={containerRef} />
      {pageCount !== null && <p>页数: {pageCount}</p>}
    </div>
  )
}
