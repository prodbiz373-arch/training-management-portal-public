import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { useState } from 'react'
import { UnsavedChangesProvider, useUnsavedChangesGuard } from '@/components/unsaved-changes-guard'

const navigation = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('next/navigation', () => ({ useRouter: () => navigation, usePathname: () => '/schedule/new' }))

function Harness() {
  const [value, setValue] = useState('')
  useUnsavedChangesGuard({ dirty: value !== '' })
  return <>
    <input aria-label="編集値" value={value} onChange={(event) => setValue(event.target.value)} />
    <a href="/home">Homeへ移動</a>
  </>
}

function open() {
  return render(<UnsavedChangesProvider><Harness /></UnsavedChangesProvider>)
}

function dispatchBeforeUnload() {
  const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent
  window.dispatchEvent(event)
  return event
}

beforeEach(() => {
  navigation.push.mockReset()
  window.history.replaceState({}, '', '/schedule/new')
})

it('差分があるアプリ内遷移を確認し、編集継続と破棄遷移を選べる', () => {
  open()
  fireEvent.change(screen.getByLabelText('編集値'), { target: { value: '未保存' } })
  fireEvent.click(screen.getByRole('link', { name: 'Homeへ移動' }))

  let dialog = screen.getByRole('dialog', { name: '編集内容が保存されていません' })
  expect(dialog.textContent).toContain('移動すると、入力した内容は失われます。')
  fireEvent.click(within(dialog).getByRole('button', { name: '編集を続ける' }))
  expect((screen.getByLabelText('編集値') as HTMLInputElement).value).toBe('未保存')
  expect(navigation.push).not.toHaveBeenCalled()

  fireEvent.click(screen.getByRole('link', { name: 'Homeへ移動' }))
  dialog = screen.getByRole('dialog', { name: '編集内容が保存されていません' })
  fireEvent.click(within(dialog).getByRole('button', { name: '保存せずに移動' }))
  expect(navigation.push).toHaveBeenCalledWith('/home')
})

it('初期値との差分が消えるとbeforeunload警告も解除する', () => {
  open()
  expect(dispatchBeforeUnload().defaultPrevented).toBe(false)
  fireEvent.change(screen.getByLabelText('編集値'), { target: { value: '未保存' } })
  expect(dispatchBeforeUnload().defaultPrevented).toBe(true)
  fireEvent.change(screen.getByLabelText('編集値'), { target: { value: '' } })
  expect(dispatchBeforeUnload().defaultPrevented).toBe(false)
})

it.each([
  ['戻る', -1, 1],
  ['進む', 1, -1],
] as const)('ブラウザの%sで元画面へ戻し、確定後に本来の履歴方向へ移動する', (_, destinationIndex, restoreDirection) => {
  const go = vi.spyOn(window.history, 'go').mockImplementation(() => undefined)
  open()
  fireEvent.change(screen.getByLabelText('編集値'), { target: { value: '未保存' } })

  act(() => window.dispatchEvent(new PopStateEvent('popstate', {
    state: { __trainingPortalHistoryIndex: destinationIndex },
  })))
  expect(go).toHaveBeenCalledWith(restoreDirection)
  expect(screen.getByRole('dialog', { name: '編集内容が保存されていません' })).toBeTruthy()

  act(() => window.dispatchEvent(new PopStateEvent('popstate', {
    state: { __trainingPortalHistoryIndex: 0 },
  })))
  fireEvent.click(screen.getByRole('button', { name: '保存せずに移動' }))
  expect(go).toHaveBeenLastCalledWith(destinationIndex)
})
