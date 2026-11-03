import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from '@/app/page'
import { AuthProvider, useAuth } from '@/components/auth/auth-provider'
import { RequireAuth } from '@/components/auth/require-auth'
import { AppShell } from '@/components/app-shell'
import { requestApi } from '@/lib/auth-api'
import { saveMembers } from '@/lib/portal-store'

const navigation = vi.hoisted(() => ({ pathname: '/', router: { replace: vi.fn(), push: vi.fn() } }))
vi.mock('next/navigation', () => ({ useRouter: () => navigation.router, usePathname: () => navigation.pathname }))
const instructor = { syainId: '001', syainKbn: 2, name: 'テスト講師', nameKana: null, authorities: ['ROLE_INSTRUCTOR'] }
const trainee = { ...instructor, syainId: '002', name: 'テスト新人', syainKbn: 1, authorities: ['ROLE_TRAINEE'] }
const csrf = { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status })
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080')
  navigation.pathname = '/'
  navigation.router.replace.mockReset()
  localStorage.clear()
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

function renderLogin() { return render(<AuthProvider><LoginPage /></AuthProvider>) }
async function fillLogin() {
  await waitFor(() => expect(screen.getByRole('button', { name: 'ログイン' }).hasAttribute('disabled')).toBe(false))
  fireEvent.change(screen.getByLabelText(/社員ID/), { target: { value: '001' } })
  fireEvent.change(screen.getByLabelText(/^パスワード/, { selector: 'input' }), { target: { value: 'test-password' } })
}
function renderShell() { return render(<AuthProvider><RequireAuth><AppShell><p>保護された内容</p></AppShell></RequireAuth></AuthProvider>) }

describe('ログイン画面', () => {
  it('成功時にhomeへ遷移し、パスワードを保存・ログ出力しない', async () => {
    const storage = vi.spyOn(Storage.prototype, 'setItem')
    const log = vi.spyOn(console, 'log')
    const warn = vi.spyOn(console, 'warn')
    const error = vi.spyOn(console, 'error')
    fetchMock.mockResolvedValueOnce(response({ code: 'AUTHENTICATION_REQUIRED' }, 401))
      .mockResolvedValueOnce(response(csrf)).mockResolvedValueOnce(response(instructor)).mockResolvedValueOnce(response(csrf))
    renderLogin()
    await fillLogin()
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }))
    await waitFor(() => expect(navigation.router.replace).toHaveBeenCalledWith('/home'))
    expect((screen.getByLabelText(/^パスワード/, { selector: 'input' }) as HTMLInputElement).value).toBe('')
    expect(storage).not.toHaveBeenCalled()
    for (const spy of [log, warn, error]) expect(spy).not.toHaveBeenCalled()
    expect(screen.queryByRole('link', { name: /新規メンバー登録/ })).toBeNull()
  })

  it('LOGIN_FAILEDを表示し、パスワードを消す', async () => {
    fetchMock.mockResolvedValueOnce(response({}, 401)).mockResolvedValueOnce(response(csrf))
      .mockResolvedValueOnce(response({ code: 'LOGIN_FAILED' }, 401))
    renderLogin()
    await fillLogin()
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }))
    expect((await screen.findByRole('alert')).textContent).toBe('社員IDまたはパスワードが正しくありません')
    expect((screen.getByLabelText(/^パスワード/, { selector: 'input' }) as HTMLInputElement).value).toBe('')
    expect(navigation.router.replace).not.toHaveBeenCalled()
  })

  it('429の待機案内を表示し、パスワードを消す', async () => {
    fetchMock.mockResolvedValueOnce(response({}, 401)).mockResolvedValueOnce(response(csrf))
      .mockResolvedValueOnce(response({ code: 'LOGIN_RATE_LIMITED', message: 'private internal' }, 429))
    renderLogin()
    await fillLogin()
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }))
    expect((await screen.findByRole('alert')).textContent).toBe('しばらく待ってから再試行してください')
    expect((screen.getByLabelText(/^パスワード/, { selector: 'input' }) as HTMLInputElement).value).toBe('')
    expect(navigation.router.replace).not.toHaveBeenCalled()
  })

  it('通信エラーを安全に表示する', async () => {
    fetchMock.mockResolvedValueOnce(response({}, 401)).mockRejectedValueOnce(new Error('private internal'))
    renderLogin()
    await fillLogin()
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }))
    expect((await screen.findByRole('alert')).textContent).toBe('サーバーに接続できませんでした')
  })

  it('連続submitでもリクエストは1回にする', async () => {
    let resolve: (value: Response) => void = () => {}
    fetchMock.mockResolvedValueOnce(response({}, 401)).mockImplementationOnce(() => new Promise<Response>((r) => { resolve = r }))
      .mockResolvedValueOnce(response(instructor)).mockResolvedValueOnce(response(csrf))
    renderLogin()
    await fillLogin()
    const form = screen.getByRole('button', { name: 'ログイン' }).closest('form')!
    fireEvent.submit(form)
    fireEvent.submit(form)
    expect(screen.getByRole('button', { name: 'ログイン中…' }).hasAttribute('disabled')).toBe(true)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    await act(async () => resolve(response(csrf)))
    await waitFor(() => expect(navigation.router.replace).toHaveBeenCalledWith('/home'))
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/login'))).toHaveLength(1)
  })

  it('既にログイン済みならme確認後にhomeへ遷移する', async () => {
    fetchMock.mockResolvedValue(response(instructor))
    renderLogin()
    await waitFor(() => expect(navigation.router.replace).toHaveBeenCalledWith('/home'))
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('古いlocalStorageの仮認証を信用しない', async () => {
    localStorage.setItem('training-portal-session-user:v1', JSON.stringify({ role: '講師', name: '偽ユーザー' }))
    fetchMock.mockResolvedValue(response({}, 401))
    renderLogin()
    await fillLogin()
    expect(navigation.router.replace).not.toHaveBeenCalled()
    expect(localStorage.getItem('training-portal-session-user:v1')).toBeNull()
  })
})

describe('保護画面とログアウト', () => {
  it('初期確認中は内容を見せず、401ならログイン画面へ戻す', async () => {
    navigation.pathname = '/home'
    let resolve: (value: Response) => void = () => {}
    fetchMock.mockImplementation(() => new Promise<Response>((r) => { resolve = r }))
    renderShell()
    expect(screen.getByRole('status').textContent).toContain('確認')
    expect(screen.queryByText('保護された内容')).toBeNull()
    await act(async () => resolve(response({}, 401)))
    await waitFor(() => expect(navigation.router.replace).toHaveBeenCalledWith('/'))
    expect(screen.queryByText('保護された内容')).toBeNull()
  })

  it('確認時の通信障害ではリダイレクトループにせず再試行できる', async () => {
    fetchMock.mockRejectedValueOnce(new Error('private')).mockResolvedValueOnce(response(instructor))
    renderShell()
    expect((await screen.findByRole('alert')).textContent).toBe('サーバーに接続できませんでした')
    expect(navigation.router.replace).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '再試行' }))
    expect(await screen.findByText('保護された内容')).toBeTruthy()
  })

  it('講師にはメンバー管理リンクと実ユーザー名を表示する', async () => {
    fetchMock.mockResolvedValue(response(instructor))
    renderShell()
    expect(await screen.findByRole('link', { name: 'メンバ登録' })).toBeTruthy()
    expect(screen.getByText('テスト講師')).toBeTruthy()
  })

  it('新人はスケジュールを直接表示でき、ホーム・スケジュール・進捗課題管理を利用できる', async () => {
    navigation.pathname = '/schedule'
    fetchMock.mockResolvedValue(response(trainee))
    renderShell()
    expect(await screen.findByText('保護された内容')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'ホーム' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'スケジュール' })).toBeTruthy()
    expect(screen.getByRole('link', { name: '進捗・課題管理' })).toBeTruthy()
    expect(screen.queryByRole('link', { name: 'メンバ登録' })).toBeNull()
  })

  it('新人にはメンバー管理リンクを表示せず、URL直接入力も表示拒否する', async () => {
    navigation.pathname = '/members'
    fetchMock.mockResolvedValue(response(trainee))
    renderShell()
    expect(await screen.findByText('この画面を表示する権限がありません')).toBeTruthy()
    expect(screen.queryByRole('link', { name: 'メンバ登録' })).toBeNull()
    expect(screen.queryByText('保護された内容')).toBeNull()
  })

  it('ログアウト時CSRFを送り、成功後は保護内容を消す', async () => {
    fetchMock.mockResolvedValueOnce(response(instructor)).mockResolvedValueOnce(response(csrf))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderShell()
    fireEvent.click(await screen.findByRole('button', { name: 'ログアウト' }))
    await waitFor(() => expect(navigation.router.replace).toHaveBeenCalledWith('/'))
    expect(screen.queryByText('保護された内容')).toBeNull()
    expect(fetchMock.mock.calls[2][1]).toMatchObject({ credentials: 'include', headers: { 'X-CSRF-TOKEN': 'test-csrf' } })
  })

  it('ログアウト失敗時に内部情報を表示せず、成功扱いにしない', async () => {
    fetchMock.mockResolvedValueOnce(response(instructor)).mockResolvedValueOnce(response(csrf))
      .mockResolvedValueOnce(response({ code: 'INTERNAL_ERROR', message: 'private sql' }, 500))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderShell()
    fireEvent.click(await screen.findByRole('button', { name: 'ログアウト' }))
    expect((await screen.findByRole('alert')).textContent).toBe('処理に失敗しました。時間をおいて再度お試しください')
    expect(navigation.router.replace).not.toHaveBeenCalled()
  })

  it('共通APIの401でuserとCSRFを破棄する', async () => {
    fetchMock.mockResolvedValueOnce(response(instructor)).mockResolvedValueOnce(response({ code: 'AUTHENTICATION_REQUIRED' }, 401))
    renderShell()
    await screen.findByText('保護された内容')
    await act(async () => { await requestApi('/api/members').catch(() => {}) })
    await waitFor(() => expect(navigation.router.replace).toHaveBeenCalledWith('/'))
    expect(screen.queryByText('保護された内容')).toBeNull()
  })

  it('再確認のmeが401になっても保護内容を消す', async () => {
    function Refresh() { const { refreshUser } = useAuth(); return <button onClick={() => void refreshUser()}>状態更新</button> }
    fetchMock.mockResolvedValueOnce(response(instructor)).mockResolvedValueOnce(response({}, 401))
    render(<AuthProvider><RequireAuth><Refresh /></RequireAuth></AuthProvider>)
    fireEvent.click(await screen.findByRole('button', { name: '状態更新' }))
    await waitFor(() => expect(navigation.router.replace).toHaveBeenCalledWith('/'))
    expect(screen.queryByRole('button', { name: '状態更新' })).toBeNull()
  })

  it('残る業務モックの保存にもパスワードを含めない', () => {
    saveMembers([{ no: 1, employeeId: '001', name: 'テスト', furigana: '', role: '講師', password: 'must-not-save' }])
    expect(localStorage.getItem('training-portal-members:v1')).not.toContain('password')
    expect(localStorage.getItem('training-portal-members:v1')).not.toContain('must-not-save')
  })
})
