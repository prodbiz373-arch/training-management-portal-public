import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getCsrf, getCurrentUser, login, logout, onUnauthorized, requestApi, ApiClientError } from '@/lib/auth-api'

const user = { syainId: '001', syainKbn: 2, name: '講師', nameKana: null, authorities: ['ROLE_INSTRUCTOR'] }
const token = (value: string) => ({ headerName: 'X-CSRF-TOKEN', token: value })
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status })
let fetchMock: ReturnType<typeof vi.fn>
beforeEach(() => {
  vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080')
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals() })

describe('認証APIクライアント', () => {
  it('同じCookieセッションでCSRFを取得してログインし、CSRFを再取得する', async () => {
    fetchMock.mockResolvedValueOnce(response(token('before'))).mockResolvedValueOnce(response(user)).mockResolvedValueOnce(response(token('after')))
    expect(await login('001', 'test-secret')).toEqual({ user, csrf: token('after') })
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      'http://localhost:8080/api/auth/csrf', 'http://localhost:8080/api/auth/login', 'http://localhost:8080/api/auth/csrf',
    ])
    for (const [, options] of fetchMock.mock.calls) expect(options.credentials).toBe('include')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'POST', headers: { 'X-CSRF-TOKEN': 'before', 'Content-Type': 'application/json' }, body: JSON.stringify({ syainId: '001', password: 'test-secret' }) })
  })

  it('meの401は未ログインとして返す', async () => {
    fetchMock.mockResolvedValue(response({ code: 'AUTHENTICATION_REQUIRED' }, 401))
    expect(await getCurrentUser()).toBeNull()
  })

  it('meの認証済みユーザーを返す', async () => {
    fetchMock.mockResolvedValue(response(user))
    expect(await getCurrentUser()).toEqual(user)
  })

  it('LOGIN_FAILEDを固定の安全なメッセージにする', async () => {
    fetchMock.mockResolvedValueOnce(response(token('csrf'))).mockResolvedValueOnce(response({ code: 'LOGIN_FAILED', message: 'internal secret' }, 401))
    await expect(login('001', 'secret')).rejects.toThrow('社員IDまたはパスワードが正しくありません')
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('通信不能の内部例外を公開しない', async () => {
    fetchMock.mockRejectedValue(new Error('internal host secret'))
    await expect(getCsrf()).rejects.toThrow('サーバーに接続できませんでした')
  })

  it('ログアウトでは現在のCSRFを送り204のJSONを読まない', async () => {
    const json = vi.fn()
    fetchMock.mockResolvedValue({ ok: true, status: 204, json })
    await logout({ headerName: 'X-CSRF-TOKEN', token: 'current' })
    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/auth/logout', expect.objectContaining({ method: 'POST', credentials: 'include', headers: { 'X-CSRF-TOKEN': 'current' } }))
    expect(json).not.toHaveBeenCalled()
  })

  it('再読み込み後にCSRFがなければログアウト前に取得する', async () => {
    fetchMock.mockResolvedValueOnce(response(token('new'))).mockResolvedValueOnce(new Response(null, { status: 204 }))
    await logout()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('保護APIの401をContextへ通知し、403は権限エラーとする', async () => {
    const listener = vi.fn()
    const unsubscribe = onUnauthorized(listener)
    fetchMock.mockResolvedValueOnce(response({ code: 'AUTHENTICATION_REQUIRED' }, 401)).mockResolvedValueOnce(response({ code: 'ACCESS_DENIED' }, 403))
    await expect(requestApi('/api/members')).rejects.toMatchObject({ status: 401 })
    expect(listener).toHaveBeenCalledOnce()
    await expect(requestApi('/api/members')).rejects.toMatchObject({ status: 403, message: 'この操作を行う権限がありません' })
    expect(listener).toHaveBeenCalledOnce()
    unsubscribe()
  })

  it('500や非JSON応答は内部情報を表示しない', async () => {
    fetchMock.mockResolvedValueOnce(response({ code: 'INTERNAL_ERROR', message: 'SQL secret stacktrace' }, 500))
    await expect(getCurrentUser()).rejects.toThrow('処理に失敗しました。時間をおいて再度お試しください')
    fetchMock.mockResolvedValue(new Response('<html>internal secret</html>', { status: 500 }))
    await expect(getCurrentUser()).rejects.toThrow('処理に失敗しました。時間をおいて再度お試しください')
  })

  it('接続設定がなければ別のサーバーへ暗黙に送信しない', async () => {
    vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', '')
    await expect(getCsrf()).rejects.toBeInstanceOf(ApiClientError)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('想定外のCSRFヘッダーを送信しない', async () => {
    fetchMock.mockResolvedValue(response({ headerName: 'Authorization', token: 'secret' }))
    await expect(getCsrf()).rejects.toThrow('サーバーからの応答を確認できませんでした')
  })
})

it('429では固定の待機案内を使い、既存認証の破棄イベントを発生させない', async () => {
  const expired = vi.fn(); const unsubscribe = onUnauthorized(expired)
  fetchMock.mockResolvedValue(response({ code: 'LOGIN_RATE_LIMITED', message: 'private internal' }, 429))
  try { await expect(requestApi('/api/auth/login', { method: 'POST' })).rejects.toMatchObject({ status: 429, message: 'しばらく待ってから再試行してください' }); expect(expired).not.toHaveBeenCalled() } finally { unsubscribe() }
})
