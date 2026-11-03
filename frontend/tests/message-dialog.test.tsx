import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { ConfirmDialog, MessageDialog, type MessageKind } from '@/components/message-dialog'

const cases: [MessageKind | undefined, string][] = [
  ['error', 'text-[#DC2626]'], ['warning', 'text-[#D97706]'],
  ['confirmation', 'text-[#054A99]'], ['information', 'text-[#054A99]'],
  ['success', 'text-[#054A99]'], [undefined, 'text-[#054A99]'],
]
it.each(cases)('MessageDialog種別%sのタイトル色とOK動作', (kind, color) => {
  const onClose = vi.fn()
  render(<MessageDialog kind={kind} title="同じタイトル" message="本文は維持" onClose={onClose} />)
  expect(screen.getByRole('heading').className).toBe(`text-base font-bold ${color}`)
  expect(screen.getByText('本文は維持').className).toBe('mt-3 whitespace-pre-line text-sm leading-6 text-foreground')
  const ok = screen.getByRole('button', { name: 'OK' })
  expect(document.activeElement).toBe(ok)
  fireEvent.click(ok)
  expect(onClose).toHaveBeenCalledOnce()
})
it.each(cases)('ConfirmDialog種別%sのタイトル色とボタン動作', (kind, color) => {
  const onConfirm = vi.fn(), onCancel = vi.fn()
  render(<ConfirmDialog kind={kind} title="確認" message="本文" confirmLabel="削除する" destructive onConfirm={onConfirm} onCancel={onCancel} />)
  expect(screen.getByRole('heading').className).toBe(`text-base font-bold ${color}`)
  fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }))
  fireEvent.click(screen.getByRole('button', { name: '削除する' }))
  expect(onCancel).toHaveBeenCalledOnce()
  expect(onConfirm).toHaveBeenCalledOnce()
})
it('Escapeによる閉じる動作を維持する', () => {
  const onClose = vi.fn()
  render(<MessageDialog kind="error" title="エラー" message="本文" onClose={onClose} />)
  fireEvent.keyDown(document, { key: 'Escape' })
  expect(onClose).toHaveBeenCalledOnce()
})
it('処理中の確認ではボタンとEscapeを無効にする', () => {
  const onCancel = vi.fn(), onConfirm = vi.fn()
  render(<ConfirmDialog title="確認" message="本文" confirmLabel="実行" pending onCancel={onCancel} onConfirm={onConfirm} />)
  fireEvent.click(screen.getByRole('button', { name: '実行' }))
  fireEvent.keyDown(document, { key: 'Escape' })
  expect(onConfirm).not.toHaveBeenCalled()
  expect(onCancel).not.toHaveBeenCalled()
})
