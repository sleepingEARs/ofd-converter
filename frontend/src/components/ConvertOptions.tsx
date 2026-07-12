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
}

export function ConvertOptions({ selectedFile, onConvert, converting }: Props) {
  const [formats, setFormats] = useState<FormatsResponse>({})
  const [target, setTarget] = useState<string | null>(null)

  useEffect(() => {
    api.formats().then(setFormats).catch(() => {})
  }, [])

  const available = selectedFile ? formats[selectedFile.source_type.toLowerCase()] ?? [] : []

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
        <Button type="primary" disabled={!target || converting} loading={converting} onClick={startConvert}>
          开始转换
        </Button>
      </Space>
    </Card>
  )
}
