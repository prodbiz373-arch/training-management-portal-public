export type LoginRequest = { syainId: string; password: string }
export type AuthUser = {
  syainId: string
  syainKbn: 1 | 2
  name: string
  nameKana: string | null
  authorities: string[]
}
export type CsrfResponse = { headerName: 'X-CSRF-TOKEN'; token: string }
export type FieldError = { field: string; message: string }
export type ApiErrorResponse = { code: string; message: string; errors: FieldError[] }
export type ApiError = ApiErrorResponse

const fields = new Set(['syainId', 'syainKbn', 'name', 'nameKana', 'password',
  'reviewerEmployeeId', 'statusCode', 'content', 'updateCount'])
const fieldMessages = new Set(['必須項目です', '入力文字数が上限を超えています', '指定可能な値ではありません',
  '0以上で入力してください', 'UTF-8で72バイト以内で入力してください', '入力内容が不正です'])
function safeFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) return []
  return value.filter((item) => item && fields.has(item.field) && fieldMessages.has(item.message))
    .map((item) => ({ field: item.field, message: item.message }))
}
const memberMessages: Record<string, string> = {
  MEMBER_ROLE_CHANGE_NOT_ALLOWED: 'スケジュールの人数条件、担当講師の参照、または提出履歴があるため、社員区分を変更できません',
  MEMBER_NOT_FOUND: 'メンバーが存在しません。一覧から選び直してください',
  MEMBER_DUPLICATE: '社員IDが重複しています',
  MEMBER_UPDATE_CONFLICT: '他の操作で更新されています。一覧を再取得し、編集を解除しました。対象を選び直してください',
  MEMBER_REFERENCED: '他のデータから参照されているため削除できません',
  MEMBER_SELF_DELETE_NOT_ALLOWED: 'ログイン中の自分自身は削除できません',
  SCHEDULE_NOT_FOUND: 'スケジュールが存在しません。一覧から選び直してください',
  SCHEDULE_UPDATE_CONFLICT: '他のユーザーによって更新されています。最新の情報を再読み込みしてください。',
  SCHEDULE_DUPLICATE: 'スケジュールIDが重複しています',
  PROGRESS_NOT_FOUND: '課題が存在しません。検索条件を確認してください',
  PROGRESS_UPDATE_CONFLICT: '他のユーザーによって更新されています。最新の情報を再読み込みしてください。',
}
const scheduleInvalidInputMessages = new Set([
  '新人は1人以上登録する必要があります。',
  '編集権限を持つ講師を1人以上登録する必要があります。',
  'この分類はスケジュール内容で使用されているため削除できません。',
  '課題提出状況が登録されているため、このスケジュールは削除できません。',
  'ピン留めできるスケジュールは2件までです。',
  '進捗・課題管理でレビュー者として登録、またはコメントを記載しているため削除できません',
  '進捗・課題管理でレビュー者として登録されているため参照のみに変更できません',
])


export class ApiClientError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string, public readonly errors: FieldError[] = []) {
    super(message)
    this.name = 'ApiClientError'
  }
}

const unauthorizedListeners = new Set<() => void>()
export function onUnauthorized(listener: () => void) {
  unauthorizedListeners.add(listener)
  return () => { unauthorizedListeners.delete(listener) }
}

export function errorMessage(error: unknown): string {
  return error instanceof ApiClientError ? error.message : '処理に失敗しました。時間をおいて再度お試しください'
}

function baseUrl() {
  const value = process.env.NEXT_PUBLIC_API_BASE_URL?.trim()
  if (!value) throw new ApiClientError(0, 'CONFIG_ERROR', 'APIの接続設定を確認してください')
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) throw new Error()
    return value.replace(/\/+$/, '')
  } catch {
    throw new ApiClientError(0, 'CONFIG_ERROR', 'APIの接続設定を確認してください')
  }
}

// 次回の業務API接続でも同じ401/403処理を利用できる。
export async function requestApi<T>(path: string, options: RequestInit = {}): Promise<T> {
  const url = `${baseUrl()}${path}`
  let response: Response
  try {
    response = await fetch(url, { ...options, credentials: 'include', cache: 'no-store', signal: AbortSignal.timeout(15000) })
  } catch {
    throw new ApiClientError(0, 'NETWORK_ERROR', 'サーバーに接続できませんでした')
  }
  if (!response.ok) {
    let body: Partial<ApiError> = {}
    try { body = await response.json() } catch { /* HTML等の内部情報は表示しない */ }
    const code = typeof body?.code === 'string' ? body.code : 'HTTP_ERROR'
    if (response.status === 401 && path !== '/api/auth/login' && path !== '/api/auth/me') {
      unauthorizedListeners.forEach((listener) => listener())
    }
    // バックエンド契約の固定文言のみ表示し、未知のmessageや500の詳細は転記しない。
    const message = response.status >= 500 ? '処理に失敗しました。時間をおいて再度お試しください'
      : response.status === 401 && code === 'LOGIN_FAILED' ? '社員IDまたはパスワードが正しくありません'
      : response.status === 401 ? 'ログインが必要です'
      : response.status === 429 && code === 'LOGIN_RATE_LIMITED' ? 'しばらく待ってから再試行してください'
      : response.status === 403 && code === 'HTTPS_REQUIRED' ? 'HTTPSで接続してください'
      : response.status === 403 ? 'この操作を行う権限がありません'
      : response.status === 400 && code === 'INVALID_INPUT' && typeof body.message === 'string'
        && scheduleInvalidInputMessages.has(body.message) ? body.message
      : response.status === 400 && Object.hasOwn(memberMessages, code) ? memberMessages[code]
      : response.status === 400 ? '入力内容を確認してください'
      : (response.status === 404 || response.status === 409) && Object.hasOwn(memberMessages, code) ? memberMessages[code]
      : '処理に失敗しました。時間をおいて再度お試しください'
    throw new ApiClientError(response.status, code, message, response.status === 400 ? safeFieldErrors(body?.errors) : [])
  }
  if (response.status === 204) return undefined as T
  try { return await response.json() as T } catch {
    throw new ApiClientError(0, 'INVALID_RESPONSE', 'サーバーからの応答を確認できませんでした')
  }
}

export async function getCsrf(): Promise<CsrfResponse> {
  const csrf = await requestApi<CsrfResponse>('/api/auth/csrf')
  if (csrf?.headerName !== 'X-CSRF-TOKEN' || typeof csrf.token !== 'string' || !csrf.token) {
    throw new ApiClientError(0, 'INVALID_RESPONSE', 'サーバーからの応答を確認できませんでした')
  }
  return csrf
}

export async function login(syainId: string, password: string): Promise<{ user: AuthUser; csrf: CsrfResponse }> {
  const before = await getCsrf()
  const input: LoginRequest = { syainId, password }
  const user = await requestApi<AuthUser>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': before.token },
    body: JSON.stringify(input),
  })
  // ログイン成功時にセッションIDとCSRFが更新されるため、必ず取り直す。
  return { user, csrf: await getCsrf() }
}

export async function getCurrentUser(): Promise<AuthUser | null> {
  try { return await requestApi<AuthUser>('/api/auth/me') } catch (error) {
    if (error instanceof ApiClientError && error.status === 401) return null
    throw error
  }
}

export async function logout(csrf?: CsrfResponse | null): Promise<void> {
  const token = csrf ?? await getCsrf()
  await requestApi<void>('/api/auth/logout', { method: 'POST', headers: { 'X-CSRF-TOKEN': token.token } })
}
