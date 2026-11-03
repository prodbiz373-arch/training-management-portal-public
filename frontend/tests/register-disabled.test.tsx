import { expect, it, vi } from 'vitest'
const redirect = vi.hoisted(() => vi.fn(() => { throw new Error('redirect') }))
vi.mock('next/navigation', () => ({ redirect }))
import RegisterPage from '@/app/register/page'
it('自己登録画面は登録処理を提供せずログインへ戻す', () => {
  const before = window.localStorage.length
  expect(() => RegisterPage()).toThrow('redirect')
  expect(redirect).toHaveBeenCalledWith('/')
  expect(window.localStorage.length).toBe(before)
})
