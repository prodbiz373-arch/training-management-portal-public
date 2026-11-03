import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import SchedulePage from '@/app/(app)/schedule/page'
import { AuthProvider } from '@/components/auth/auth-provider'
import { RequireAuth } from '@/components/auth/require-auth'

const navigation = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }))
vi.mock('next/navigation', () => ({ useRouter: () => navigation, usePathname: () => '/schedule' }))
// schedule/page.tsxがmock-data.tsのschedulesを参照した場合、この最小モックでは描画できない。
vi.mock('@/lib/mock-data', () => ({ members: [] }))

const instructor = { syainId: 'instructor001', syainKbn: 2, name: 'テスト講師', nameKana: null, authorities: ['ROLE_INSTRUCTOR'] }
const trainee = { syainId: 'trainee001', syainKbn: 1, name: 'テスト新人', nameKana: null, authorities: ['ROLE_TRAINEE'] }
const schedules = [
  { scheduleId: 'S002', scheduleName: 'ピン留め研修', startDate: '2026-04-01', endDate: '2026-04-30', canEdit: true, pinned: true, scheduleUpdateCount: 3, permissionUpdateCount: 1 },
  { scheduleId: 'S001', scheduleName: '参照研修', startDate: '2026-05-01', endDate: '2026-05-31', canEdit: false, pinned: false, scheduleUpdateCount: 0, permissionUpdateCount: 0 },
]
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status })
let fetchMock: ReturnType<typeof vi.fn>
let authUser: typeof instructor | typeof trainee
let scheduleResponse: () => Promise<Response>
let pinResponse: (options: RequestInit) => Promise<Response>

beforeEach(() => {
  vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080')
  navigation.replace.mockReset()
  navigation.push.mockReset()
  sessionStorage.clear()
  authUser = instructor
  scheduleResponse = async () => response(schedules)
  pinResponse = async () => response({ scheduleId: 'S001', pinned: true, permissionUpdateCount: 1 })
  fetchMock = vi.fn(async (url: string, options: RequestInit = {}) => {
    if (url.endsWith('/me')) return response(authUser)
    if (url.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'csrf-value' })
    if (url.endsWith('/pin') && options.method === 'PUT') return pinResponse(options)
    if (url.endsWith('/schedules')) return scheduleResponse()
    throw new Error(`unexpected request: ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

function open() {
  return render(<AuthProvider><RequireAuth><SchedulePage /></RequireAuth></AuthProvider>)
}

it('APIの一覧とpinnedを表示し、mock-data.tsのschedulesやlocalStorageに依存しない', async () => {
  const storage = vi.spyOn(Storage.prototype, 'setItem')
  open()
  expect((await screen.findByText('ピン留め研修')).closest('a')?.getAttribute('href')).toBe('/schedule/S002')
  expect(screen.getByText('参照研修')).toBeTruthy()
  expect(screen.getByRole('button', { name: 'ピン留め研修のピン留めを解除' })).toBeTruthy()
  expect(screen.getByText('#S002')).toBeTruthy()
  expect(screen.getByText('2026/04/01〜2026/04/30')).toBeTruthy()
  expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/schedules'))).toBe(true)
  expect(storage).not.toHaveBeenCalled()
})

it('新規登録後の成功メッセージを一度だけ表示する', async () => {
  sessionStorage.setItem('schedule-created-message', 'スケジュールを登録しました')
  open()
  expect(await screen.findByText('スケジュールを登録しました')).toBeTruthy()
  expect(sessionStorage.getItem('schedule-created-message')).toBeNull()
})

it('削除後の成功メッセージを一度だけ表示する', async () => {
  sessionStorage.setItem('schedule-deleted-message', 'スケジュールを削除しました')
  open()
  expect(await screen.findByText('スケジュールを削除しました')).toBeTruthy()
  expect(sessionStorage.getItem('schedule-deleted-message')).toBeNull()
})

it('新人もcanEditに従って編集表示となり、新規登録へ遷移できる', async () => {
  authUser = trainee
  open()
  const scheduleName = await screen.findByText('ピン留め研修')
  expect(scheduleName.closest('a')?.getAttribute('href')).toBe('/schedule/S002')
  expect(screen.getByText('編集')).toBeTruthy()
  expect(screen.getByText('参照のみ')).toBeTruthy()
  expect(screen.getByRole('link', { name: '新規スケジュール' }).getAttribute('href')).toBe('/schedule/new')
})

it('未ピンをピン留めし、成功後のGETレスポンス順をそのまま表示する', async () => {
  let gets = 0
  scheduleResponse = async () => response(gets++ === 0 ? schedules : [
    { ...schedules[1], pinned: true, permissionUpdateCount: 1 },
    schedules[0],
  ])
  open()
  fireEvent.click(await screen.findByRole('button', { name: '参照研修をピン留め' }))

  await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/schedules'))).toHaveLength(2))
  const pinCall = fetchMock.mock.calls.find(([url, options]) => url.endsWith('/pin') && options?.method === 'PUT')
  expect(JSON.parse(String(pinCall?.[1]?.body))).toEqual({ pinned: true, permissionUpdateCount: 0 })
  expect(screen.getAllByText(/#S00/).map((node) => node.textContent)).toEqual(['#S001', '#S002'])
  expect(screen.getByRole('button', { name: '参照研修のピン留めを解除' })).toBeTruthy()
})

it('ピン留め済みスケジュールを解除できる', async () => {
  let gets = 0
  scheduleResponse = async () => response(gets++ === 0 ? schedules : [
    { ...schedules[0], pinned: false, permissionUpdateCount: 2 },
    schedules[1],
  ])
  pinResponse = async () => response({ scheduleId: 'S002', pinned: false, permissionUpdateCount: 2 })
  open()
  fireEvent.click(await screen.findByRole('button', { name: 'ピン留め研修のピン留めを解除' }))
  expect(await screen.findByRole('button', { name: 'ピン留め研修をピン留め' })).toBeTruthy()
  const call = fetchMock.mock.calls.find(([url, options]) => url.endsWith('/pin') && options?.method === 'PUT')
  expect(JSON.parse(String(call?.[1]?.body))).toEqual({ pinned: false, permissionUpdateCount: 1 })
})

it('新人かつcanEdit=falseでも本人用ピン留めを変更できる', async () => {
  authUser = trainee
  let gets = 0
  scheduleResponse = async () => response(gets++ === 0 ? schedules : [schedules[0], { ...schedules[1], pinned: true }])
  open()
  fireEvent.click(await screen.findByRole('button', { name: '参照研修をピン留め' }))
  await waitFor(() => expect(fetchMock.mock.calls.some(([url, options]) => url.endsWith('/pin') && options?.method === 'PUT')).toBe(true))
  expect(screen.getByRole('link', { name: '新規スケジュール' })).toBeTruthy()
})

it('2件ピン留め済みで3件目を押すと警告し、API送信もローカル変更もしない', async () => {
  const threeSchedules = [
    schedules[0],
    { ...schedules[1], pinned: true },
    { ...schedules[1], scheduleId: 'S003', scheduleName: '3件目', pinned: false },
  ]
  scheduleResponse = async () => response(threeSchedules)
  open()
  const thirdPin = await screen.findByRole('button', { name: '3件目をピン留め' })
  fireEvent.click(thirdPin)

  const dialog = await screen.findByRole('dialog', { name: 'ピン留めできません' })
  expect(within(dialog).getByRole('heading').className).toContain('text-[#D97706]')
  expect(dialog.textContent).toContain('ピン留めできるスケジュールは2件までです。')
  expect(dialog.textContent).toContain('別のスケジュールのピン留めを解除してから設定してください。')
  expect(screen.getByRole('button', { name: '3件目をピン留め' })).toBeTruthy()
  expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/pin'))).toBe(false)
  fireEvent.click(within(dialog).getByRole('button', { name: 'OK' }))
  await waitFor(() => expect(document.activeElement).toBe(thirdPin))
})

it('ピン更新中は連打を防ぎ、PUTを1回だけ送信する', async () => {
  let resolvePin!: (value: Response) => void
  pinResponse = () => new Promise<Response>((resolve) => { resolvePin = resolve })
  open()
  const button = await screen.findByRole('button', { name: '参照研修をピン留め' }) as HTMLButtonElement
  fireEvent.click(button)
  fireEvent.click(button)
  await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/pin'))).toHaveLength(1))
  expect(button.disabled).toBe(true)
  await act(async () => resolvePin(response({ scheduleId: 'S001', pinned: true, permissionUpdateCount: 1 })))
})

it('バックエンドのピン上限エラーもMessageDialogへ表示する', async () => {
  pinResponse = async () => response({ code: 'INVALID_INPUT', message: 'ピン留めできるスケジュールは2件までです。' }, 400)
  open()
  fireEvent.click(await screen.findByRole('button', { name: '参照研修をピン留め' }))
  const dialog = await screen.findByRole('dialog', { name: 'ピン留めできません' })
  expect(within(dialog).getByRole('heading').className).toContain('text-[#D97706]')
  expect(dialog.textContent).toContain('別のスケジュールのピン留めを解除してから設定してください。')
  expect(screen.queryByRole('alert')).toBeNull()
})

it.each([
  [403, 'ACCESS_DENIED', 'この操作を行う権限がありません'],
  [409, 'SCHEDULE_UPDATE_CONFLICT', '他のユーザーによって更新されています。最新の情報を再読み込みしてください。'],
])('ピン更新APIの%sは既存の画面上部エラー表示を使う', async (status, code, message) => {
  pinResponse = async () => response({ code }, status)
  open()
  fireEvent.click(await screen.findByRole('button', { name: '参照研修をピン留め' }))
  expect((await screen.findByRole('alert')).textContent).toContain(message)
})

it('ピン更新APIの401は既存AuthProviderでログイン画面へ戻る', async () => {
  pinResponse = async () => response({ code: 'AUTHENTICATION_REQUIRED' }, 401)
  open()
  fireEvent.click(await screen.findByRole('button', { name: '参照研修をピン留め' }))
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/'))
})

it('一覧取得中はloadingを表示する', async () => {
  let resolve!: (value: Response) => void
  scheduleResponse = () => new Promise<Response>((done) => { resolve = done })
  open()
  expect(await screen.findByText('スケジュールを読み込んでいます…')).toBeTruthy()
  await act(async () => resolve(response(schedules)))
  expect(await screen.findByText('ピン留め研修')).toBeTruthy()
})

it('一覧取得エラーを既存の安全なエラー表示で再試行できる', async () => {
  scheduleResponse = async () => { throw new Error('private schedule failure') }
  open()
  expect((await screen.findByRole('alert')).textContent).toContain('サーバーに接続できませんでした')
  scheduleResponse = async () => response(schedules)
  screen.getByText('一覧を再取得').click()
  expect(await screen.findByText('ピン留め研修')).toBeTruthy()
})

it('一覧APIの401では既存AuthProviderによりログイン画面へ戻る', async () => {
  authUser = trainee
  scheduleResponse = async () => response({ code: 'AUTHENTICATION_REQUIRED' }, 401)
  open()
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/'))
  expect(screen.queryByText('スケジュール一覧')).toBeNull()
})
