declare module 'ofd.js' {
  /**
   * ofd.js 1.5.0 named-export API (verified from minified source).
   * parseOfdDocument(config) accepts a config object whose `ofd` field may be a
   * File, Blob, ArrayBuffer, or URL string. success/fail are callbacks. secret/digest
   * are optional authorization codes (leave undefined for the open build).
   */
  export interface ParseOfdConfig {
    ofd: File | Blob | ArrayBuffer | string
    success?: (data: unknown) => void
    fail?: (error: Error | unknown) => void
    secret?: string
    digest?: string
    headers?: Record<string, string>
  }
  export function parseOfdDocument(config: ParseOfdConfig): void
  export function parseOfdDocumentFromBase64(config: ParseOfdConfig): void

  /** Returns the page count for document index. */
  export function getOFDPageCount(documentIndex: number): number

  /**
   * Renders a page. The 2nd arg is dpi (not width). Returns a render object (not a DOM node);
   * prefer renderOfdByIndex for a DOM node. Kept for completeness.
   */
  export function renderOfd(documentIndex: number, dpi?: number, opts?: Record<string, unknown>): unknown

  /**
   * Renders a single page to a <div> and returns it. dpi is the 3rd arg (default 96).
   * Verified: internally calls document.createElement('div') and returns it.
   */
  export function renderOfdByIndex(
    documentIndex: number,
    pageIndex: number,
    dpi?: number,
    opts?: Record<string, unknown>,
  ): HTMLElement

  const _default: {
    parseOfdDocument: typeof parseOfdDocument
    parseOfdDocumentFromBase64: typeof parseOfdDocumentFromBase64
    getOFDPageCount: typeof getOFDPageCount
    renderOfd: typeof renderOfd
    renderOfdByIndex: typeof renderOfdByIndex
  }
  export default _default
}
