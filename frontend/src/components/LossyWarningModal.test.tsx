import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LossyWarningModal } from './LossyWarningModal'

// AntD inserts a space between two CJK chars in buttons (e.g. "确 认"), so match
// the first char + optional space + second char. okText="确认" -> "确 认".
const confirmBtn = () => screen.getByRole('button', { name: /确\s?认/ })
const cancelBtn = () => screen.getByRole('button', { name: /取\s?消/ })

describe('LossyWarningModal', () => {
  it('shows warning text and calls onConfirm on OK', async () => {
    const onConfirm = vi.fn()
    render(<LossyWarningModal open={true} warning="版式转 DOCX 为有损转换" onConfirm={onConfirm} onCancel={() => {}} />)
    expect(await screen.findByText('版式转 DOCX 为有损转换')).toBeInTheDocument()
    await userEvent.click(confirmBtn())
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('calls onCancel on cancel', async () => {
    const onCancel = vi.fn()
    render(<LossyWarningModal open={true} warning="x" onConfirm={() => {}} onCancel={onCancel} />)
    await userEvent.click(cancelBtn())
    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})
