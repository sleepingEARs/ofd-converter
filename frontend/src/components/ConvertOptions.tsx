import { useEffect, useState } from 'react'
import { Card, Radio, Button, Space } from 'antd'
import { api } from '../api/client'
import type { FileItem, FormatsResponse } from '../types/api'

/** Formats that trigger a lossy conversion warning (inline only, no popup). */
const LOSSY = new Set(['docx', 'md', 'txt'])

/** Inline warning shown when selecting a lossy format. */
const INLINE_WARNING = '⚠️ 有损转换，以下情况效果可能不理想：发票/合同等特殊版式、表格、纯图片、横向文字/水印。建议转 PDF/PNG/JPG 保留原始版式。'

interface Props {
  selectedFile: FileItem | null
  onConvert: (targetFormat: string) => void
  converting: boolean
  /** Number of files checked for batch convert. */
  checkedCount: number
}

export function ConvertOptions({ selectedFile, onConvert, converting, checkedCount }: Props) {
  const [formats, setFormats] = useState<FormatsResponse>({})
  const [target, setTarget] = useState<string | null>(null)

  useEffect(() => {
    api.formats().then(setFormats).catch(() => {})
  }, [])

  const available = selectedFile ? formats[selectedFile.source_type.toLowerCase()] ?? [] : []
  const isBatch = checkedCount > 0
  const buttonLabel = isBatch
    ? `批量转换 (${checkedCount} 个文件)`
    : '开始转换'

  function startConvert() {
    if (!target) return
    onConvert(target)
  }

  return (
    <Card title="转换">
      <Space direction="vertical" style={{ width: '100%' }}>
        <Radio.Group value={target} onChange={(e) => setTarget(e.target.value)}>
          <Space wrap>
            {available.map((fmt) => <Radio key={fmt} value={fmt}>{fmt}</Radio>)}
          </Space>
        </Radio.Group>
        {target && LOSSY.has(target) && (
          <div style={{ padding: '6px 12px', background: '#fffbe6', border: '1px solid #ffe58f', borderRadius: 4, fontSize: 13, lineHeight: 1.8 }}>
            {INLINE_WARNING}
          </div>
        )}
        {isBatch && (
          <div style={{ padding: '4px 12px', background: '#e6f4ff', border: '1px solid #91caff', borderRadius: 4, fontSize: 13 }}>
            已选 {checkedCount} 个文件，将批量转换并打包为 ZIP 下载。
          </div>
        )}
        <Button type="primary" disabled={!target || converting || (!selectedFile && !isBatch)} loading={converting} onClick={startConvert}>
          {buttonLabel}
        </Button>
      </Space>
    </Card>
  )
}
