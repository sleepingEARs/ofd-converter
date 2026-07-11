import { Modal } from 'antd'

interface Props {
  open: boolean
  warning: string
  onConfirm: () => void
  onCancel: () => void
}

export function LossyWarningModal({ open, warning, onConfirm, onCancel }: Props) {
  return (
    <Modal
      open={open}
      title="有损转换提示"
      okText="确认"
      cancelText="取消"
      onOk={onConfirm}
      onCancel={onCancel}
    >
      <p>{warning}</p>
    </Modal>
  )
}
