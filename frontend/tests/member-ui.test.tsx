import { fireEvent, render, screen, waitFor, act } from '@testing-library/react'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import MembersPage from '@/app/(app)/members/page'
import { AuthProvider } from '@/components/auth/auth-provider'
const navigation = vi.hoisted(() => ({ replace: vi.fn() }))
vi.mock('next/navigation', () => ({ useRouter: () => navigation, usePathname: () => '/members' }))
const member = { syainId: '001', syainKbn: 2, name: 'テスト講師', nameKana: null, updateCount: 0 }
const response = (body: unknown, status = 200) => status === 204 ? new Response(null, { status }) : new Response(JSON.stringify(body), { status })
let fetchMock: ReturnType<typeof vi.fn>
let rows: typeof member[]
let role: string
let mutation: () => Promise<Response>
let list: () => Promise<Response>
beforeEach(() => {
  vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080')
  role = 'ROLE_INSTRUCTOR'; rows = [member]; navigation.replace.mockReset()
  list = async () => response(rows)
  mutation = async () => response(member)
  fetchMock = vi.fn(async (url: string, init: RequestInit) => {
    if (url.endsWith('/me')) return response({ ...member, authorities: [role] })
    if (url.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'synthetic-csrf' })
    if (init.method && init.method !== 'GET') return mutation()
    if (url.endsWith('/members')) return list()
    return response({ ...member, updateCount: 7 })
  })
  vi.stubGlobal('fetch', fetchMock)
  vi.spyOn(window, 'confirm').mockReturnValue(true)
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })
const input = (name: string) => document.querySelector<HTMLInputElement>(`input[name="${name}"]`)!
async function open() {
  render(<AuthProvider><MembersPage /></AuthProvider>)
  await waitFor(() => expect(screen.getByRole('button', { name: '登録' }).hasAttribute('disabled')).toBe(false))
}
function submit() { fireEvent.submit(screen.getByRole('button', { name: /^(登録|更新)$/ }).closest('form')!) }
const requests = (method: string) => fetchMock.mock.calls.filter(([, init]) => init.method === method)
async function select() {
  fireEvent.click(screen.getByText('001'))
  await screen.findByRole('button', { name: '更新' })
  await waitFor(() => expect(screen.getByRole('button', { name: '更新' }).hasAttribute('disabled')).toBe(false))
}
it('講師の一覧をAPI順で表示する', async () => { rows = [member, { ...member, syainId: '002', name: '新人' }]; await open(); expect(screen.getAllByRole('row').slice(1).map(r => r.textContent)).toEqual([expect.stringContaining('001'), expect.stringContaining('002')]) })
it('0件を表示する', async () => { rows = []; await open(); expect(screen.getByText('メンバーは登録されていません')).toBeTruthy() })
it('通信失敗から再試行できる', async () => { list = async () => { throw new Error('internal') }; await open(); expect(screen.getByRole('alert').textContent).toContain('サーバーに接続できませんでした'); list = async () => response(rows); fireEvent.click(screen.getByText('一覧を再取得')); expect(await screen.findByText('001')).toBeTruthy() })
it('登録・一覧再取得・二重送信防止・パスワード消去・秘密の非保存', async () => {
  const storage = vi.spyOn(Storage.prototype, 'setItem')
  const logs = [vi.spyOn(console, 'log'), vi.spyOn(console, 'warn'), vi.spyOn(console, 'error')]
  let resolve!: (r: Response) => void
  mutation = () => new Promise(r => { resolve = r })
  await open()
  fireEvent.change(input('syainId'), { target: { value: '003' } })
  fireEvent.change(input('name'), { target: { value: '追加新人' } })
  fireEvent.change(input('password'), { target: { value: 'synthetic-password' } })
  submit(); submit()
  expect(input('password').value).toBe('')
  expect(screen.getByRole('button', { name: '登録' }).hasAttribute('disabled')).toBe(true)
  await waitFor(() => expect(requests('POST')).toHaveLength(1))
  expect(JSON.parse(requests('POST')[0][1].body as string)).toMatchObject({ syainId: '003', password: 'synthetic-password' })
  await act(async () => resolve(response(member)))
  expect(await screen.findByText('追加新人(003)を登録しました')).toBeTruthy()
  expect(fetchMock.mock.calls.filter(([url, init]) => url.endsWith('/members') && !init.method)).toHaveLength(2)
  expect(input('password').value).toBe(''); expect(storage).not.toHaveBeenCalled(); logs.forEach(log => expect(log).not.toHaveBeenCalled())
})
it('400を項目に表示しrejectedValueや内部情報を捨てる', async () => {
  mutation = async () => response({ code: 'INVALID_INPUT', message: 'SQL private', errors: [{ field: 'password', message: 'UTF-8で72バイト以内で入力してください', rejectedValue: 'private' }, { field: 'name', message: '必須項目です' }] }, 400)
  await open(); submit()
  expect(await screen.findByText('UTF-8で72バイト以内で入力してください')).toBeTruthy()
  expect(input('password').getAttribute('aria-describedby')).toBe('password-error')
  expect(input('name').getAttribute('aria-invalid')).toBe('true')
  expect(document.body.textContent).not.toContain('private')
})
it.each([
  ['MEMBER_DUPLICATE', '社員IDが重複しています'],
  ['MEMBER_SELF_DELETE_NOT_ALLOWED', 'ログイン中の自分自身は削除できません'],
  ['MEMBER_REFERENCED', '他のデータから参照されているため削除できません'],
])('%sを安全に表示する', async (code, message) => {
  mutation = async () => response({ code, message: 'private' }, 409)
  await open()
  if (code === 'MEMBER_DUPLICATE') submit()
  else { await select(); fireEvent.click(screen.getByRole('button', { name: '削除' })) }
  expect(await screen.findByText(message)).toBeTruthy()
  expect(document.body.textContent).not.toContain('private')
})
it('認証状態のバックグラウンド再確認中も自己削除の409を表示する', async () => {
  let resolveMe!: (response: Response) => void
  mutation = async () => response({ code: 'MEMBER_SELF_DELETE_NOT_ALLOWED' }, 409)
  await open(); await select()
  fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
    if (url.endsWith('/me')) return new Promise<Response>((resolve) => { resolveMe = resolve })
    if (url.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'synthetic-csrf' })
    if (init.method && init.method !== 'GET') return mutation()
    if (url.endsWith('/members')) return list()
    return response({ ...member, updateCount: 7 })
  })
  window.dispatchEvent(new Event('focus'))
  fireEvent.click(screen.getByRole('button', { name: '削除' }))
  await act(async () => resolveMe(response({ ...member, authorities: [role] })))
  expect((await screen.findByRole('alert')).textContent).toContain('ログイン中の自分自身は削除できません')
})
it('詳細の最新updateCountを送り空パスワードで更新できる', async () => {
  await open(); await select(); expect(input('syainId').disabled).toBe(true); submit()
  await screen.findByText('テスト講師(001)を更新しました')
  expect(JSON.parse(requests('PUT')[0][1].body as string)).toMatchObject({ updateCount: 7, password: '' })
  expect(fetchMock.mock.calls.filter(([url, init]) => url.endsWith('/members') && !init.method)).toHaveLength(2)
})
it('更新競合で編集を解除して一覧を再取得する', async () => {
  mutation = async () => response({ code: 'MEMBER_UPDATE_CONFLICT' }, 409)
  await open(); await select(); submit()
  expect(await screen.findByText(/他の操作で更新されています/)).toBeTruthy()
  await waitFor(() => expect(input('syainId').value).toBe(''))
  expect(fetchMock.mock.calls.filter(([url, init]) => url.endsWith('/members') && !init.method)).toHaveLength(2)
})
it('削除確認でIDと氏名を表示し204で一覧再取得する', async () => {
  mutation = async () => { rows = []; return response(null, 204) }
  await open(); await select(); fireEvent.click(screen.getByRole('button', { name: '削除' }))
  expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('社員ID: 001\n氏名: テスト講師'))
  expect(await screen.findByText('テスト講師(001)を削除しました')).toBeTruthy()
  expect(await screen.findByText('メンバーは登録されていません')).toBeTruthy()
})
it('APIの401で認証状態を破棄しログインへ戻る', async () => { list = async () => response({ code: 'AUTHENTICATION_REQUIRED' }, 401); render(<AuthProvider><MembersPage /></AuthProvider>); await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/')); expect(screen.queryByText('研修メンバ一覧')).toBeNull() })
it('新人は画面を表示せずメンバーAPIを呼ばない', async () => { role = 'ROLE_TRAINEE'; render(<AuthProvider><MembersPage /></AuthProvider>); await screen.findByRole('alert'); expect(screen.queryByText('研修メンバ一覧')).toBeNull(); expect(fetchMock).toHaveBeenCalledTimes(1) })
it('バックエンド403で編集画面を隠す', async () => { list = async () => response({ code: 'ACCESS_DENIED' }, 403); render(<AuthProvider><MembersPage /></AuthProvider>); expect((await screen.findByRole('alert')).textContent).toContain('権限'); expect(screen.queryByText('研修メンバ一覧')).toBeNull() })

it('自分の区分変更成功後は認証を再確認し、保存失敗にせず再ログインへ誘導する', async () => {
  let changed = false
  let listRequests = 0
  fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
    if (url.endsWith('/me')) return changed ? response({ code: 'AUTHENTICATION_REQUIRED' }, 401) : response({ ...member, authorities: [role] })
    if (url.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'synthetic-csrf' })
    if (init.method === 'PUT') { changed = true; return response({ ...member, syainKbn: 1, updateCount: 8 }) }
    if (url.endsWith('/members')) { listRequests++; return response([member]) }
    return response({ ...member, updateCount: 7 })
  })
  await open(); await select()
  fireEvent.change(document.querySelector('select[name="syainKbn"]')!, { target: { value: '1' } })
  submit()
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/'))
  expect(requests('PUT')).toHaveLength(1)
  expect(listRequests).toBe(1)
  expect(screen.queryByText('処理に失敗しました。時間をおいて再度お試しください')).toBeNull()
})

it.each(['登録', '更新'])('%s時の項目エラーに赤枠クラスとARIAを維持し、再検証時に通常へ戻す', async (mode) => {
  mutation = async () => response({ code: 'INVALID_INPUT', errors: [{ field: 'name', message: '必須項目です' }] }, 400)
  await open()
  if (mode === '更新') await select()
  submit(); await screen.findByText('必須項目です')
  const name = input('name')
  expect(name.getAttribute('aria-invalid')).toBe('true')
  expect(name.getAttribute('aria-describedby')).toBe('name-error')
  expect(document.getElementById('name-error')?.textContent).toBe('必須項目です')
  expect(name.className).toContain('aria-invalid:border-[#DC2626]')
  expect(name.className).toContain('aria-invalid:focus:border-[#DC2626]')
  expect(name.className).toContain('focus:ring-2')
  expect(input('nameKana').getAttribute('aria-invalid')).toBe('false')
  mutation = async () => response(member)
  fireEvent.change(name, { target: { value: '修正済み' } }); submit()
  await waitFor(() => expect(name.getAttribute('aria-invalid')).toBe('false'))
  expect(screen.queryByText('必須項目です')).toBeNull()
})

it('自分のパスワード変更成功後は認証を再確認し、保存失敗にせず再ログインへ誘導する', async () => {
  let changed = false
  let listRequests = 0
  fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
    if (url.endsWith('/me')) return changed ? response({ code: 'AUTHENTICATION_REQUIRED' }, 401) : response({ ...member, authorities: [role] })
    if (url.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'synthetic-csrf' })
    if (init.method === 'PUT') { changed = true; return response({ ...member, syainKbn: 2, updateCount: 8 }) }
    if (url.endsWith('/members')) { listRequests++; return response([member]) }
    return response({ ...member, updateCount: 7 })
  })
  await open(); await select()
  fireEvent.change(input('password'), { target: { value: 'new-synthetic-secret' } })
  submit()
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/'))
  expect(requests('PUT')).toHaveLength(1)
  expect(listRequests).toBe(1)
  expect(screen.queryByText('処理に失敗しました。時間をおいて再度お試しください')).toBeNull()
})
