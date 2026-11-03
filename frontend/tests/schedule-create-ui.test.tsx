import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ScheduleEditor } from '@/components/schedule-editor'
import { AuthProvider } from '@/components/auth/auth-provider'
import { RequireAuth } from '@/components/auth/require-auth'
import { UnsavedChangesProvider } from '@/components/unsaved-changes-guard'

const navigation = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))
vi.mock('next/navigation', () => ({ useRouter: () => navigation, usePathname: () => '/schedule/new' }))

const instructor = { syainId: 'admin001', syainKbn: 2, name: '初期講師', nameKana: null, authorities: ['ROLE_INSTRUCTOR'] }
const trainee = { syainId: 'trainee001', syainKbn: 1, name: '初期新人', nameKana: null, authorities: ['ROLE_TRAINEE'] }
const members = [
  { syainId: 'admin001', syainKbn: 2, name: '初期講師', nameKana: null, updateCount: 0 },
  { syainId: 'admin002', syainKbn: 2, name: '追加講師', nameKana: null, updateCount: 0 },
  { syainId: 'trainee001', syainKbn: 1, name: '初期新人', nameKana: null, updateCount: 0 },
]
let createResponse: (options: RequestInit) => Promise<Response>
let fetchMock: ReturnType<typeof vi.fn>
let authUser: typeof instructor | typeof trainee
let memberOptions: typeof members

beforeEach(() => {
  vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080')
  navigation.push.mockReset(); navigation.replace.mockReset(); sessionStorage.clear()
  authUser = instructor
  memberOptions = members
  createResponse = async () => new Response(JSON.stringify({
    scheduleId: 'NEW001', permissions: [], categories: [], details: [],
  }), { status: 201 })
  fetchMock = vi.fn(async (url: string, options: RequestInit = {}) => {
    if (url.endsWith('/me')) return new Response(JSON.stringify(authUser))
    if (url.endsWith('/schedules/member-options')) return new Response(JSON.stringify(memberOptions))
    if (url.endsWith('/csrf')) return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf-value' }))
    if (url.includes('/schedules/non-working-dates?')) {
      const requestUrl = new URL(url)
      const start = requestUrl.searchParams.get('startDate') ?? ''
      const end = requestUrl.searchParams.get('endDate') ?? ''
      const known = ['2026-10-10', '2026-10-11', '2026-10-12', '2026-10-31', '2026-11-01']
      return new Response(JSON.stringify({ dates: known.filter((date) => date >= start && date <= end) }))
    }
    if (url.endsWith('/schedules') && options.method === 'POST') return createResponse(options)
    throw new Error(`unexpected request: ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

function open() { return render(<AuthProvider><RequireAuth><UnsavedChangesProvider><ScheduleEditor /></UnsavedChangesProvider></RequireAuth></AuthProvider>) }

function beforeUnloadPrevented() {
  const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent
  window.dispatchEvent(event)
  return event.defaultPrevented
}

async function decideBasic() {
  await screen.findByLabelText('スケジュールID')
  await waitFor(() => expect((screen.getByRole('button', { name: '決定' }) as HTMLButtonElement).disabled).toBe(false))
  fireEvent.change(screen.getByLabelText('スケジュールID'), { target: { value: 'NEW001' } })
  fireEvent.change(screen.getByLabelText('スケジュール名'), { target: { value: '新規研修' } })
  fireEvent.change(screen.getByLabelText('開始日'), { target: { value: '2026-10-05' } })
  fireEvent.change(screen.getByLabelText('終了日'), { target: { value: '2026-10-05' } })
  fireEvent.click(screen.getByRole('button', { name: '決定' }))
  await waitFor(() => expect((screen.getByLabelText('スケジュールID') as HTMLInputElement).disabled).toBe(true))
}

async function makeReady() {
  await decideBasic()
  fireEvent.change(screen.getByLabelText('新人を選択'), { target: { value: 'trainee001' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[1])
  fireEvent.change(screen.getByLabelText('分類名'), { target: { value: 'Java研修' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[2])
  fireEvent.change(screen.getByLabelText('1の分類'), { target: { value: '1' } })
  fireEvent.change(screen.getByLabelText('1の講師'), { target: { value: 'admin001' } })
  fireEvent.change(screen.getByLabelText('1の内容'), { target: { value: 'オリエンテーション' } })
}

it('初期データ取得後は警告せず、新規入力の差分だけを離脱確認する', async () => {
  open()
  await screen.findByText('初期講師')
  expect(beforeUnloadPrevented()).toBe(false)

  const scheduleId = screen.getByLabelText('スケジュールID') as HTMLInputElement
  fireEvent.change(scheduleId, { target: { value: 'DIRTY001' } })
  expect(beforeUnloadPrevented()).toBe(true)
  fireEvent.click(screen.getByRole('link', { name: 'スケジュール一覧へ戻る' }))

  const dialog = screen.getByRole('dialog', { name: '編集内容が保存されていません' })
  fireEvent.click(within(dialog).getByRole('button', { name: '編集を続ける' }))
  expect(scheduleId.value).toBe('DIRTY001')
  expect(navigation.push).not.toHaveBeenCalled()

  fireEvent.change(scheduleId, { target: { value: '' } })
  expect(beforeUnloadPrevented()).toBe(false)
})

it('初期状態は基本情報だけ活性で、決定・解除により状態を切り替える', async () => {
  open()
  await screen.findByText('初期講師')
  expect((screen.getByLabelText('スケジュールID') as HTMLInputElement).disabled).toBe(false)
  expect((screen.getByLabelText('講師を選択').closest('fieldset') as HTMLFieldSetElement).disabled).toBe(true)
  expect((screen.getByRole('button', { name: '前月' }).closest('fieldset') as HTMLFieldSetElement).disabled).toBe(true)
  expect((screen.getByRole('button', { name: '登録' }) as HTMLButtonElement).disabled).toBe(true)

  await decideBasic()
  expect((screen.getByLabelText('スケジュールID') as HTMLInputElement).disabled).toBe(true)
  expect((screen.getByLabelText('講師を選択').closest('fieldset') as HTMLFieldSetElement).disabled).toBe(false)
  expect((screen.getByRole('button', { name: '前月' }).closest('fieldset') as HTMLFieldSetElement).disabled).toBe(false)
  expect((screen.getByLabelText('admin001の権限') as HTMLSelectElement).value).toBe('編集')
  expect((screen.getByLabelText('admin002の権限') as HTMLSelectElement).value).toBe('編集')
  expect(screen.getByText('新人は1人以上登録する必要があります。')).toBeTruthy()

  fireEvent.click(screen.getByRole('button', { name: '解除' }))
  expect(await screen.findByRole('dialog', { name: '基本情報の決定を解除しますか？' })).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '解除する' }))
  expect((screen.getByLabelText('スケジュールID') as HTMLInputElement).disabled).toBe(false)
  expect((screen.getByLabelText('講師を選択').closest('fieldset') as HTMLFieldSetElement).disabled).toBe(true)
})

it('新人・分類・日別内容を設定して二重送信せず登録し、一覧へ遷移する', async () => {
  let resolveCreate!: (value: Response) => void
  createResponse = () => new Promise((resolve) => { resolveCreate = resolve })
  open()
  await makeReady()
  const register = screen.getByRole('button', { name: '登録' }) as HTMLButtonElement
  expect(register.disabled).toBe(false)
  fireEvent.click(register); fireEvent.click(register)
  await waitFor(() => expect(fetchMock.mock.calls.filter(([url, options]) => url.endsWith('/schedules') && options?.method === 'POST')).toHaveLength(1))
  expect((screen.getByRole('button', { name: '登録中…' }) as HTMLButtonElement).disabled).toBe(true)
  await act(async () => resolveCreate(new Response(JSON.stringify({
    scheduleId: 'NEW001', permissions: [], categories: [], details: [],
  }), { status: 201 })))
  expect(navigation.push).toHaveBeenCalledWith('/schedule')
  expect(sessionStorage.getItem('schedule-created-message')).toBe('スケジュールを登録しました')
  const call = fetchMock.mock.calls.find(([url, options]) => url.endsWith('/schedules') && options?.method === 'POST')
  const body = JSON.parse(String(call?.[1]?.body))
  expect(body.permissions).toEqual(expect.arrayContaining([
    { employeeId: 'admin001', canEdit: true }, { employeeId: 'admin002', canEdit: true },
    { employeeId: 'trainee001', canEdit: false },
  ]))
  expect(body.details[0]).toEqual(expect.objectContaining({ scheduleDateNo: 1, scheduleDate: '2026-10-05', categoryNo: 1 }))
})

it('新人も登録画面を利用でき、本人を編集権限者として自動設定する', async () => {
  authUser = trainee
  open()
  await screen.findByText('初期新人')

  expect((screen.getByLabelText('trainee001の権限') as HTMLSelectElement).value).toBe('編集')
  expect((screen.getByLabelText('trainee001の権限') as HTMLSelectElement).disabled).toBe(true)
  expect((screen.getByLabelText('trainee001を削除') as HTMLButtonElement).disabled).toBe(true)
  expect((screen.getByLabelText('admin001の権限') as HTMLSelectElement).value).toBe('編集')
  expect((screen.getByLabelText('admin002の権限') as HTMLSelectElement).value).toBe('編集')
  expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/schedules/member-options'))).toBe(true)
  expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/members'))).toBe(false)
})

it('編集可能な講師を必須表示し、最後の編集可能講師の削除・権限変更を禁止する', async () => {
  authUser = trainee
  open()
  await screen.findByText('初期新人')

  const firstInstructorDelete = screen.getByLabelText('admin001を削除') as HTMLButtonElement
  expect(firstInstructorDelete.disabled).toBe(false)
  fireEvent.click(firstInstructorDelete)

  const lastInstructorPermission = screen.getByLabelText('admin002の権限') as HTMLSelectElement
  const lastInstructorDelete = screen.getByLabelText('admin002を削除') as HTMLButtonElement
  expect(lastInstructorPermission.disabled).toBe(true)
  expect(lastInstructorDelete.disabled).toBe(true)
  expect(lastInstructorDelete.title).toBe('編集権限を持つ講師を1人以上登録する必要があります。')
  expect(screen.getByText('編集権限を持つ講師を1人以上登録する必要があります。')).toBeTruthy()
})

it('編集可能な講師がいない場合は必須エラーを表示して登録できない', async () => {
  authUser = trainee
  memberOptions = members.filter((member) => member.syainKbn === 1)
  open()
  await decideBasic()

  expect(screen.getByText('編集権限を持つ講師を1人以上登録する必要があります。')).toBeTruthy()
  expect((screen.getByRole('button', { name: '登録' }) as HTMLButtonElement).disabled).toBe(true)
})

it('使用中分類の削除ではモーダルを表示して分類を残す', async () => {
  open(); await makeReady()
  fireEvent.change(screen.getByLabelText('1の分類名'), { target: { value: 'Java基礎' } })
  fireEvent.change(screen.getByLabelText('分類名'), { target: { value: 'Web研修' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[2])
  fireEvent.change(screen.getByLabelText('2の分類名'), { target: { value: 'Web基礎' } })
  fireEvent.click(screen.getByLabelText('2の分類を削除'))
  expect(screen.queryByLabelText('2の分類名')).toBeNull()

  fireEvent.click(screen.getByLabelText('1の分類を削除'))
  const dialog = await screen.findByRole('dialog', { name: '削除できません' })
  expect(dialog.textContent).toContain('「Java基礎」はスケジュール内容で使用されているため削除できません。')
  expect(screen.getByLabelText('1の分類名')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'OK' }))
  expect(screen.queryByRole('dialog')).toBeNull()
})

it('登録前の表示月を前後に切り替えられる', async () => {
  open()
  await screen.findByText('初期講師')
  fireEvent.change(screen.getByLabelText('スケジュールID'), { target: { value: 'NEW002' } })
  fireEvent.change(screen.getByLabelText('スケジュール名'), { target: { value: '月跨ぎ研修' } })
  fireEvent.change(screen.getByLabelText('開始日'), { target: { value: '2026-10-30' } })
  fireEvent.change(screen.getByLabelText('終了日'), { target: { value: '2026-11-02' } })
  fireEvent.click(screen.getByRole('button', { name: '決定' }))
  await waitFor(() => expect((screen.getByLabelText('スケジュールID') as HTMLInputElement).disabled).toBe(true))
  fireEvent.change(screen.getByLabelText('分類名'), { target: { value: 'Java研修' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[2])

  expect(screen.getByText('10/30 (金)')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '次月' }))
  expect(screen.getByText('11/2 (月)')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '前月' }))
  expect(screen.getByText('10/30 (金)')).toBeTruthy()
})

it('休日除外ONで土日・スポーツの日を除き、除外後の日付番号を連番にする', async () => {
  open()
  await screen.findByText('初期講師')
  fireEvent.change(screen.getByLabelText('スケジュールID'), { target: { value: 'HOLIDAY1' } })
  fireEvent.change(screen.getByLabelText('スケジュール名'), { target: { value: '休日除外研修' } })
  fireEvent.change(screen.getByLabelText('開始日'), { target: { value: '2026-10-05' } })
  fireEvent.change(screen.getByLabelText('終了日'), { target: { value: '2026-10-16' } })
  fireEvent.click(screen.getByRole('button', { name: '決定' }))

  expect(await screen.findByText('10/13 (火)')).toBeTruthy()
  expect(screen.queryByText('10/10 (土)')).toBeNull()
  expect(screen.queryByText('10/11 (日)')).toBeNull()
  expect(screen.queryByText('10/12 (月)')).toBeNull()
  expect(screen.getByLabelText('6の分類')).toBeTruthy()
  expect(screen.getByLabelText('9の分類')).toBeTruthy()
  expect(screen.queryByLabelText('10の分類')).toBeNull()
})

it('休日除外OFFでスポーツの日も生成する', async () => {
  open()
  await screen.findByText('初期講師')
  fireEvent.change(screen.getByLabelText('スケジュールID'), { target: { value: 'HOLIDAY2' } })
  fireEvent.change(screen.getByLabelText('スケジュール名'), { target: { value: '休日含む研修' } })
  fireEvent.change(screen.getByLabelText('開始日'), { target: { value: '2026-10-05' } })
  fireEvent.change(screen.getByLabelText('終了日'), { target: { value: '2026-10-13' } })
  fireEvent.click(screen.getByLabelText('土・日・祝日を含まない'))
  fireEvent.click(screen.getByRole('button', { name: '決定' }))

  expect(await screen.findByText('10/12 (月)')).toBeTruthy()
  expect(screen.getByLabelText('8の分類')).toBeTruthy()
  expect(fetchMock.mock.calls.some(([url]) => url.includes('/schedules/non-working-dates?'))).toBe(false)
})

it('分類追加ボタンは折り返さず必要最小幅を持つ', async () => {
  open()
  await screen.findByText('初期講師')
  const addCategory = screen.getAllByRole('button', { name: '追加' })[2]
  expect(addCategory.className).toContain('whitespace-nowrap')
  expect(addCategory.className).toContain('min-w-[5.5rem]')
})

it.each([
  [400, 'INVALID_INPUT', '入力内容を確認してください'],
  [403, 'ACCESS_DENIED', 'この操作を行う権限がありません'],
  [409, 'SCHEDULE_DUPLICATE', 'スケジュールIDが重複しています'],
])('登録APIの%sエラーを従来の上部領域に表示する', async (status, code, message) => {
  createResponse = async () => new Response(JSON.stringify({ code }), { status })
  open(); await makeReady()
  fireEvent.click(screen.getByRole('button', { name: '登録' }))
  expect((await screen.findByRole('alert')).textContent).toContain(message)
  expect(beforeUnloadPrevented()).toBe(true)
})

it('登録APIの401で既存認証処理を実行する', async () => {
  createResponse = async () => new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED' }), { status: 401 })
  open(); await makeReady()
  fireEvent.click(screen.getByRole('button', { name: '登録' }))
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/'))
})

it('内容は任意で、空白時は課題を無効にしチェックを外す', async () => {
  open()
  await makeReady()
  const task = screen.getByLabelText('1の課題') as HTMLInputElement
  fireEvent.click(task)
  expect(task.checked).toBe(true)
  fireEvent.change(screen.getByLabelText('1の内容'), { target: { value: '   ' } })
  expect(task.disabled).toBe(true)
  expect(task.checked).toBe(false)
  expect((screen.getByRole('button', { name: /^登録$/ }) as HTMLButtonElement).disabled).toBe(false)
  fireEvent.click(screen.getByRole('button', { name: /^登録$/ }))
  await waitFor(() => expect(fetchMock.mock.calls.some(([url, options]) => url.endsWith('/schedules') && options.method === 'POST' && JSON.parse(options.body as string).details[0].content === null)).toBe(true))
})
