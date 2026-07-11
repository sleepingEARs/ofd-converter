import { Upload } from 'antd'
import type { UploadProps } from 'antd'
import { useUpload } from '../hooks/useUpload'
import type { FileItem } from '../types/api'

interface Props {
  onUploaded: (file: FileItem) => void
}

export function UploadZone({ onUploaded }: Props) {
  const { upload, uploading } = useUpload(onUploaded)

  const props: UploadProps = {
    name: 'file',
    multiple: true,
    accept: '.ofd,.pdf,.png,.jpg,.jpeg,.docx',
    beforeUpload: (file) => {
      void upload(file as unknown as File)
      return false
    },
    showUploadList: false,
    disabled: uploading,
  }

  return (
    <Upload.Dragger {...props}>
      <p className="ant-upload-text">点击或拖拽文件到此处上传</p>
      <p className="ant-upload-hint">支持 OFD / PDF / 图片 / DOCX，单文件 ≤ 50MB</p>
    </Upload.Dragger>
  )
}
