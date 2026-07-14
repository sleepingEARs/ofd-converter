import { useState, useCallback, useEffect } from 'react'
import {
  Table, Select, Input, Button, DatePicker, Space, Typography, message,
} from 'antd'
import dayjs from 'dayjs'
import { api, clearAdminToken } from '../api/client'
import type { AdminLogEntry, AdminLogsResponse } from '../types/api'
import type { ColumnsType } from 'antd/es/table'

const { Title } = Typography
const { RangePicker } = DatePicker

const OPERATION_TYPES = [
  { value: 'UPLOAD', label: '上传' },
  { value: 'CONVERT', label: '转换' },
  { value: 'DOWNLOAD', label: '下载' },
  { value: 'MCP_CALL', label: 'MCP调用' },
]

const STATUS_TYPES = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'TIMEOUT', label: '超时' },
  { value: 'PENDING', label: '处理中' },
]

const COLUMNS: ColumnsType<AdminLogEntry> = [
  { title: '时间', dataIndex: 'created_at', key: 'created_at', width: 160,
    render: (v: number) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-' },
  { title: '操作类型', dataIndex: 'operation_type', key: 'operation_type', width: 90,
    render: (v: string) => {
      const m: Record<string, string> = { UPLOAD: '上传', CONVERT: '转换', DOWNLOAD: '下载', MCP_CALL: 'MCP' }
      return m[v] || v
    } },
  { title: '状态', dataIndex: 'status', key: 'status', width: 70,
    render: (v: string) => {
      const colors: Record<string, string> = { SUCCESS: '#52c41a', FAILED: '#ff4d4f', TIMEOUT: '#faad14', PENDING: '#1890ff' }
      const labels: Record<string, string> = { SUCCESS: '成功', FAILED: '失败', TIMEOUT: '超时', PENDING: '处理中' }
      return <span style={{ color: colors[v] || '#999' }}>{labels[v] || v}</span>
    } },
  { title: '文件名', dataIndex: 'filename', key: 'filename', ellipsis: true,
    render: (v: string | null) => v || '-' },
  { title: '客户端IP', dataIndex: 'client_ip', key: 'client_ip', width: 130 },
  { title: '客户端UA', dataIndex: 'user_agent', key: 'user_agent', width: 200, ellipsis: true },
  { title: '任务ID', dataIndex: 'task_id', key: 'task_id', width: 120, ellipsis: true },
  { title: '目标格式', dataIndex: 'target_format', key: 'target_format', width: 80 },
  { title: '耗时', dataIndex: 'duration_ms', key: 'duration_ms', width: 80,
    render: (v: number | null) => v != null ? `${v}ms` : '-' },
  { title: '错误信息', dataIndex: 'error_message', key: 'error_message', ellipsis: true },
]

export function AdminPage() {
  const [authed, setAuthed] = useState(false)
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<AdminLogsResponse | null>(null)
  const [filters, setFilters] = useState<Record<string, string | number | undefined>>({})
  const [pagination, setPagination] = useState({ page: 1, size: 20 })

  useEffect(() => {
    if (sessionStorage.getItem('admin_token')) {
      setAuthed(true)
    }
  }, [])

  const fetchLogs = useCallback(async (page: number, size: number) => {
    setLoading(true)
    try {
      const res = await api.adminLogs({ page, size, ...filters })
      setData(res)
      setPagination({ page: res.page, size: res.size })
    } catch (e: any) {
      if (e.message?.includes('401') || e.message?.includes('密码')) {
        clearAdminToken()
        setAuthed(false)
        message.error('密码错误或已过期，请重新登录')
      } else {
        message.error('查询失败: ' + (e.message || '未知错误'))
      }
    } finally {
      setLoading(false)
    }
  }, [filters])

  useEffect(() => {
    if (authed) {
      fetchLogs(1, 20)
    }
  }, [authed, fetchLogs])

  const handleLogin = async () => {
    if (!password) return
    try {
      const res = await fetch('/api/admin/logs?page=1&size=1', {
        headers: { 'X-Admin-Token': password },
      })
      if (res.ok) {
        sessionStorage.setItem('admin_token', password)
        setAuthed(true)
        setPassword('')
      } else {
        message.error('密码错误')
      }
    } catch {
      message.error('网络错误')
    }
  }

  const handleSearch = () => {
    fetchLogs(1, pagination.size)
  }

  const handleTableChange = (pag: { current?: number; pageSize?: number }) => {
    fetchLogs(pag.current || 1, pag.pageSize || 20)
  }

  const handleLogout = () => {
    clearAdminToken()
    setAuthed(false)
    setData(null)
  }

  if (!authed) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <div style={{ width: 320, textAlign: 'center' }}>
          <Title level={4}>管理页面登录</Title>
          <Input.Password
            placeholder="请输入管理密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onPressEnter={handleLogin}
            style={{ marginBottom: 16 }}
          />
          <Button type="primary" onClick={handleLogin} block>登录</Button>
        </div>
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>转换日志</Title>
        <Space>
          <Button onClick={handleLogout}>退出</Button>
        </Space>
      </div>

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          placeholder="操作类型"
          allowClear
          style={{ width: 120 }}
          options={OPERATION_TYPES}
          value={filters.operation_type || undefined}
          onChange={(v) => setFilters((f) => ({ ...f, operation_type: v }))}
        />
        <Select
          placeholder="状态"
          allowClear
          style={{ width: 100 }}
          options={STATUS_TYPES}
          value={filters.status || undefined}
          onChange={(v) => setFilters((f) => ({ ...f, status: v }))}
        />
        <RangePicker
          showTime
          placeholder={['开始时间', '结束时间']}
          onChange={(dates) => {
            setFilters((f) => ({
              ...f,
              start_date: dates?.[0] ? dates[0].valueOf() : undefined,
              end_date: dates?.[1] ? dates[1].valueOf() : undefined,
            }))
          }}
        />
        <Input.Search
          placeholder="搜索文件名 / IP / 任务ID"
          allowClear
          style={{ width: 260 }}
          value={filters.search as string || ''}
          onChange={(e) => setFilters((f) => ({ ...f, search: e.target.value || undefined }))}
          onSearch={handleSearch}
        />
        <Button type="primary" onClick={handleSearch} loading={loading}>查询</Button>
      </Space>

      <Table
        rowKey="id"
        columns={COLUMNS}
        dataSource={data?.logs || []}
        loading={loading}
        pagination={{
          current: pagination.page,
          pageSize: pagination.size,
          total: data?.total || 0,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
        onChange={handleTableChange}
        scroll={{ x: 1100 }}
        size="small"
      />
    </div>
  )
}

export default AdminPage