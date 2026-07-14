import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import dayjs from 'dayjs'
import zhCn from 'dayjs/locale/zh-cn'
import { App } from './App'
import './index.css'

// Register the Chinese dayjs locale and set it as default.
// Passing the locale object (not a string) guarantees Rollup cannot
// tree-shake the import, so rc-picker's weekday/month names resolve to Chinese.
dayjs.locale(zhCn)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ConfigProvider locale={zhCN}>
        <App />
      </ConfigProvider>
    </BrowserRouter>
  </StrictMode>,
)
