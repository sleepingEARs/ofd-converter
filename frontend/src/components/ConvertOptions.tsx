import { useEffect, useState } from 'react'
import { Card, Radio, Button, Space } from 'antd'
import { api } from '../api/client'
import { LossyWarningModal } from './LossyWarningModal'
import type { FileItem, FormatsResponse } from '../types/api'

/** Formats that trigger a lossy conversion warning. */
const LOSSY = new Set(['docx', 'md', 'txt'])

/** Warning messages for each lossy format. */
const LOSSY_WARNING: Record<string, string> = {
  docx: '版式转 DOCX 为有损转换，排版可能变化，仅供参考。\n发票、合同等特殊格式文件转换效果可能不理想，建议转 PDF/PNG 保留原始版式。',
  md: 'OFD 转 Markdown 为结构推断，复杂版面可能有损，仅供参考。\n发票、合同等特殊格式文件转换效果可能不理想，建议转 PDF/PNG 保留原始版式。',
  txt: 'OFD 转 TXT 仅提取文本，不含排版和结构。\n发票、合同等特殊格式文件转换效果可能不理想，建议转 PDF/PNG 保留原始版式。',
}

interface Props {
  selectedFile: FileItem | null
  onConvert: (targetFormat: string) => void
  converting: boolean
}

export function ConvertOptions({ selectedFile, onConvert, converting }: Props) {
  const [formats, setFormats] = useState<FormatsResponse>({})
  const [target, setTarget] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)

  useEffect(() => {
    api.formats().then(setFormats).catch(() => {})
  }, [])

  const available = selectedFile ? formats[selectedFile.source_type.toLowerCase()] ?? [] : []

  function startConvert() {
    if (!target) return
    if (LOSSY.has(target)) {
      setModalOpen(true)
    } else {
      onConvert(target)
    }
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
          <div style={{ padding: '6px 12px', background: '#fffbe6', border: '1px solid #ffe58f', borderRadius: 4, fontSize: 13 }}>
            ⚠️ 该格式为有损转换，发票、合同等特殊格式文件效果可能不理想，建议转 PDF/PNG 保留原始版式。
          </div>
        )}
        <Button type="primary" disabled={!target || converting} loading={converting} onClick={startConvert}>
          开始转换
        </Button>
      </Space>
      <LossyWarningModal
        open={modalOpen}
        warning={target ? LOSSY_WARNING[target] ?? '' : ''}
        onConfirm={() => { setModalOpen(false); if (target) onConvert(target) }}
        onCancel={() => setModalOpen(false)}
      />
    </Card>
  )
}
