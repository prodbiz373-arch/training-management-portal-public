import { readFileSync } from 'node:fs'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import ProgressPage from '@/app/(app)/progress/page'
import { AuthProvider } from '@/components/auth/auth-provider'
import { RequireAuth } from '@/components/auth/require-auth'
import type { ProgressData } from '@/lib/progress-api'

const navigation = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }))
vi.mock('next/navigation', () => ({ useRouter: () => navigation, usePathname: () => '/progress' }))

const trainee = { syainId: 'trainee001', syainKbn: 1, name: '実新人', nameKana: null, authorities: ['ROLE_TRAINEE'] }
const instructor = { syainId: 'admin001', syainKbn: 2, name: '実講師', nameKana: null, authorities: ['ROLE_INSTRUCTOR'] }
const baseData: ProgressData = {
  businessDate: '2026-04-03', viewerRole: 'TRAINEE',
  schedules: [{ scheduleId: 'DB001', scheduleName: 'DB新人研修', canEdit: false,
    categories: [{ categoryNo: 1, categoryName: 'Java研修' }, { categoryNo: 2, categoryName: 'Web研修' }],
    reviewers: [{ employeeId: 'admin001', name: '実講師' }, { employeeId: 'trainee002', name: '編集新人' }] },
  { scheduleId: 'DB002', scheduleName: 'DB別研修', canEdit: false,
    categories: [{ categoryNo: 1, categoryName: '別分類' }],
    reviewers: [{ employeeId: 'editor002', name: '別研修編集者' }] }],
  statuses: [
    { statusCode: 1, statusName: '未提出' }, { statusCode: 2, statusName: '修正待' },
    { statusCode: 3, statusName: '提出済' }, { statusCode: 4, statusName: 'GPレビュー中' },
    { statusCode: 5, statusName: '講師レビュー中' }, { statusCode: 6, statusName: '完了' },
    { statusCode: 7, statusName: '完了（コメント有）' },
  ],
  items: [{
    scheduleId: 'DB001', scheduleName: 'DB新人研修', scheduleDateNo: 2, scheduleDate: '2026-04-03',
    categoryNo: 1, categoryName: 'Java研修', content: '実DB課題',
    assignedInstructorEmployeeId: 'admin001', assignedInstructorName: '実講師',
    traineeEmployeeId: 'trainee001', traineeName: '実新人', reviewerEmployeeId: null, reviewerName: null,
    statusCode: 1, statusName: '未提出', completedDate: null, canEdit: false, canComment: true,
    submissionUpdateCount: null, comments: [],
  }],
}

const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status })
let authUser: typeof trainee | typeof instructor
let progressResponse: () => Promise<Response>
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080')
  window.history.replaceState(null, '', '/progress')
  navigation.replace.mockReset(); navigation.push.mockReset()
  authUser = trainee
  progressResponse = async () => response(baseData)
  fetchMock = vi.fn(async (url: string, options: RequestInit = {}) => {
    if (url.endsWith('/api/auth/me')) return response(authUser)
    if (url.includes('/api/progress')) return progressResponse()
    if (url.endsWith('/api/auth/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' })
    throw new Error(`unexpected request: ${url} ${options.method}`)
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

function open() {
  return render(<AuthProvider><RequireAuth><ProgressPage /></RequireAuth></AuthProvider>)
}

it('mock-dataに依存せず、新人本人のAPI課題・未提出・コメント0件を表示して初期選択する', async () => {
  expect(readFileSync('app/(app)/progress/page.tsx', 'utf8')).not.toContain('@/lib/mock-data')
  open()
  expect((await screen.findAllByText('実DB課題')).length).toBeGreaterThan(0)
  expect(screen.getAllByText('実新人').length).toBeGreaterThan(0)
  expect(screen.getAllByText('未提出').length).toBeGreaterThan(0)
  expect(screen.getByText('コメントはありません。')).toBeTruthy()
  const status = screen.getByLabelText('ステータス') as HTMLSelectElement
  expect(status.disabled).toBe(true)
  expect(status.value).toBe('1')
  expect(status.className).toContain('text-foreground')
  expect(within(status).getAllByRole('option')).toHaveLength(7)
  expect((within(status).getByRole('option', { name: '未提出' }) as HTMLOptionElement).selected).toBe(true)
  expect(within(status).getByRole('option', { name: '完了（コメント有）' })).toBeTruthy()
  expect((screen.getByRole('button', { name: '保存' }) as HTMLButtonElement).disabled).toBe(true)
  expect((screen.getByLabelText('コメント入力') as HTMLTextAreaElement).disabled).toBe(false)
})

it('課題詳細のレビュー者ラベル横に編集権限の補足を折り返し可能な配置で表示する', async () => {
  open()
  await screen.findAllByText('実DB課題')

  const note = screen.getByText('※選択できるのは編集権限を持つ社員のみ')
  expect(note.className).toContain('text-xs')
  expect(note.className).toContain('text-muted-foreground/70')
  expect(note.parentElement?.className).toContain('flex-wrap')
  expect(screen.getByLabelText('レビュー者')).toBeTruthy()
  expect(screen.getByRole('button', { name: '保存' })).toBeTruthy()
})

it('Homeの実PK URLで対象課題を取得・選択し、リロード可能なURLを維持する', async () => {
  window.history.replaceState(null, '', '/progress?scheduleId=DB001&categoryNo=1&scheduleDateNo=2')
  open()
  await screen.findAllByText('実DB課題')
  const progressCall = fetchMock.mock.calls.find(([url]) => String(url).includes('/api/progress?'))
  expect(progressCall?.[0]).toContain('scheduleId=DB001')
  expect(progressCall?.[0]).toContain('categoryNo=1')
  expect(progressCall?.[0]).toContain('scheduleDateNo=2')
  expect((screen.getByLabelText('スケジュール') as HTMLSelectElement).value).toBe('DB001')
  expect(screen.getByText('課題詳細')).toBeTruthy()
})

it('講師のcanEdit=trueでは新人の編集権限者もレビュー者として実APIへ保存する', async () => {
  authUser = instructor
  progressResponse = async () => response({ ...baseData, viewerRole: 'INSTRUCTOR', items: baseData.items.map((item) => ({ ...item, canEdit: true, canComment: true })) })
  fetchMock.mockImplementation(async (url: string, options: RequestInit = {}) => {
    if (url.endsWith('/api/auth/me')) return response(authUser)
    if (url.endsWith('/api/auth/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' })
    if (options.method === 'PUT') return response({ reviewerEmployeeId: 'trainee002', reviewerName: '編集新人', statusCode: 6, statusName: '完了', completedDate: '2026-04-03', updateCount: 0 })
    if (url.includes('/api/progress')) return progressResponse()
    throw new Error('unexpected')
  })
  open()
  fireEvent.click(await screen.findByText('実DB課題'))
  fireEvent.change(screen.getByLabelText('レビュー者'), { target: { value: 'trainee002' } })
  fireEvent.change(screen.getByLabelText('ステータス'), { target: { value: '6' } })
  expect((screen.getByLabelText('ステータス') as HTMLSelectElement).value).toBe('6')
  fireEvent.click(screen.getByRole('button', { name: '保存' }))
  await waitFor(() => expect(fetchMock.mock.calls.some(([, options]) => options?.method === 'PUT')).toBe(true))
  const updateCall = fetchMock.mock.calls.find(([, options]) => options?.method === 'PUT')
  expect(JSON.parse(String(updateCall?.[1]?.body))).toMatchObject({ reviewerEmployeeId: 'trainee002' })
  expect(await screen.findByDisplayValue('2026-04-03')).toBeTruthy()
  expect(screen.getAllByText('完了').length).toBeGreaterThan(0)
})

it('講師のcanEdit=falseではレビュー更新・コメント操作を非活性にする', async () => {
  authUser = instructor
  progressResponse = async () => response({ ...baseData, viewerRole: 'INSTRUCTOR', items: baseData.items.map((item) => ({ ...item, canEdit: false, canComment: false })) })
  open()
  fireEvent.click(await screen.findByText('実DB課題'))
  expect((screen.getByLabelText('レビュー者') as HTMLSelectElement).disabled).toBe(true)
  expect((screen.getByLabelText('ステータス') as HTMLSelectElement).disabled).toBe(true)
  expect((screen.getByLabelText('コメント入力') as HTMLTextAreaElement).disabled).toBe(true)
  expect((screen.getByRole('button', { name: 'コメントを追加' }) as HTMLButtonElement).disabled).toBe(true)
})

it('コメント削除は投稿者本人にだけ表示し、本人は既存確認後に削除できる', async () => {
  authUser = instructor
  const comments = [
    { commentNo: 1, authorEmployeeId: 'admin001', authorName: '実講師', content: '講師本人コメント', updatedAt: '2026-04-03T10:00:00', updateCount: 0 },
    { commentNo: 2, authorEmployeeId: 'trainee001', authorName: '実新人', content: '新人のコメント', updatedAt: '2026-04-03T11:00:00', updateCount: 0 },
  ]
  progressResponse = async () => response({
    ...baseData,
    viewerRole: 'INSTRUCTOR',
    items: baseData.items.map((item) => ({ ...item, canEdit: true, canComment: true, comments })),
  })
  const confirm = vi.fn(() => true)
  vi.stubGlobal('confirm', confirm)
  fetchMock.mockImplementation(async (url: string, options: RequestInit = {}) => {
    if (url.endsWith('/api/auth/me')) return response(authUser)
    if (url.endsWith('/api/auth/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' })
    if (options.method === 'DELETE') return response(null, 204)
    if (url.includes('/api/progress')) return progressResponse()
    throw new Error('unexpected')
  })

  open()
  fireEvent.click((await screen.findAllByText('実DB課題'))[0])
  const ownComment = screen.getByText('講師本人コメント').closest('li')
  const otherComment = screen.getByText('新人のコメント').closest('li')
  expect(ownComment).not.toBeNull()
  expect(otherComment).not.toBeNull()
  expect(within(ownComment!).getByRole('button', { name: '削除' })).toBeTruthy()
  expect(within(otherComment!).queryByRole('button', { name: '削除' })).toBeNull()

  fireEvent.click(within(ownComment!).getByRole('button', { name: '削除' }))
  expect(confirm).toHaveBeenCalledWith('コメントを削除しますか？')
  await waitFor(() => expect(fetchMock.mock.calls.some(([, options]) => options?.method === 'DELETE')).toBe(true))
  expect(screen.getByText('新人のコメント')).toBeTruthy()
})

it('スケジュール・分類・期間を検索APIへ渡し、クリアで初期状態に戻す', async () => {
  open(); await screen.findAllByText('実DB課題')
  fireEvent.change(screen.getByLabelText('スケジュール'), { target: { value: 'DB001' } })
  fireEvent.change(screen.getByLabelText('分類'), { target: { value: '2' } })
  fireEvent.change(screen.getByLabelText('期間開始'), { target: { value: '2026-04-01' } })
  fireEvent.click(screen.getByRole('button', { name: '検索' }))
  await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('categoryNo=2'))).toBe(true))
  expect(window.location.search).toContain('scheduleId=DB001')
  fireEvent.click(screen.getByRole('button', { name: 'クリア' }))
  await waitFor(() => expect((screen.getByLabelText('スケジュール') as HTMLSelectElement).value).toBe(''))
})

it('スケジュール変更時にそのスケジュールの編集権限者へ候補を切り替える', async () => {
  open(); await screen.findAllByText('実DB課題')
  fireEvent.change(screen.getByLabelText('スケジュール'), { target: { value: 'DB001' } })
  const firstReviewers = screen.getByLabelText('レビュー者検索')
  expect(within(firstReviewers).getByRole('option', { name: '実講師' })).toBeTruthy()
  expect(within(firstReviewers).getByRole('option', { name: '編集新人' })).toBeTruthy()
  expect(within(firstReviewers).queryByRole('option', { name: '別研修編集者' })).toBeNull()
  fireEvent.change(firstReviewers, { target: { value: 'trainee002' } })

  fireEvent.change(screen.getByLabelText('スケジュール'), { target: { value: 'DB002' } })
  const secondReviewers = screen.getByLabelText('レビュー者検索') as HTMLSelectElement
  expect(secondReviewers.value).toBe('')
  expect(within(secondReviewers).getByRole('option', { name: '別研修編集者' })).toBeTruthy()
  expect(within(secondReviewers).queryByRole('option', { name: '実講師' })).toBeNull()
  expect(within(secondReviewers).queryByRole('option', { name: '編集新人' })).toBeNull()
})

it('レビュー者候補が0件でも新人画面を表示し、空のプルダウンを扱う', async () => {
  progressResponse = async () => response({
    ...baseData,
    schedules: baseData.schedules.map((schedule) => ({ ...schedule, reviewers: [] })),
  })
  open()
  await screen.findAllByText('実DB課題')
  fireEvent.change(screen.getByLabelText('スケジュール'), { target: { value: 'DB001' } })
  const searchReviewers = screen.getByLabelText('レビュー者検索')
  expect(within(searchReviewers).getAllByRole('option')).toHaveLength(1)
  expect(within(screen.getByLabelText('レビュー者')).getAllByRole('option')).toHaveLength(1)
})

it('空のステータスマスタでは空枠にせず理由を表示し、保存を禁止する', async () => {
  authUser = instructor
  progressResponse = async () => response({
    ...baseData,
    viewerRole: 'INSTRUCTOR',
    statuses: [],
    items: baseData.items.map((item) => ({ ...item, canEdit: true, canComment: true })),
  })
  open()
  fireEvent.click(await screen.findByText('実DB課題'))

  const detailStatus = screen.getByLabelText('ステータス') as HTMLSelectElement
  expect(detailStatus.disabled).toBe(true)
  expect(within(detailStatus).getByRole('option', { name: 'ステータスマスタが登録されていません' })).toBeTruthy()
  expect(screen.getByText('ステータスマスタが登録されていません。')).toBeTruthy()
  expect((screen.getByRole('button', { name: '保存' }) as HTMLButtonElement).disabled).toBe(true)
  const searchStatus = screen.getByLabelText('ステータス検索') as HTMLSelectElement
  expect(searchStatus.disabled).toBe(true)
  expect(within(searchStatus).getByRole('option', { name: 'ステータスマスタなし' })).toBeTruthy()
})

it('スケジュール・課題が0件の正常レスポンスでもクラッシュしない', async () => {
  progressResponse = async () => response({ ...baseData, schedules: [], items: [] })
  open()
  expect(await screen.findByText('対象の課題はありません。')).toBeTruthy()
  expect(within(screen.getByLabelText('レビュー者検索')).getAllByRole('option')).toHaveLength(1)
  expect(screen.getByText('課題を選択してください')).toBeTruthy()
})

it('旧形式など必須reviewersが欠落したレスポンスはクラッシュせず形式エラーを表示する', async () => {
  progressResponse = async () => response({
    ...baseData,
    reviewers: [{ employeeId: 'admin001', name: '旧形式講師' }],
    schedules: [{
      scheduleId: 'DB001', scheduleName: 'DB新人研修', canEdit: false,
      categories: [{ categoryNo: 1, categoryName: 'Java研修' }],
    }],
  })
  open()
  expect((await screen.findByRole('alert')).textContent).toContain('サーバーからの応答を確認できませんでした')
  expect(screen.queryByText('実DB課題')).toBeNull()
})

it('必須statusesが欠落したレスポンスは空配列扱いせず形式エラーを表示する', async () => {
  const { statuses: _statuses, ...withoutStatuses } = baseData
  progressResponse = async () => response(withoutStatuses)
  open()
  expect((await screen.findByRole('alert')).textContent).toContain('サーバーからの応答を確認できませんでした')
  expect(screen.queryByText('実DB課題')).toBeNull()
})

it('取得中はモックを表示せず、エラー時は上部表示から再取得できる', async () => {
  let resolve!: (value: Response) => void
  progressResponse = () => new Promise<Response>((done) => { resolve = done })
  open()
  expect(await screen.findByText('進捗・課題を読み込んでいます…')).toBeTruthy()
  expect(screen.queryByText('実DB課題')).toBeNull()
  await act(async () => resolve(response({ code: 'INTERNAL_ERROR', message: 'private' }, 500)))
  expect((await screen.findByRole('alert')).textContent).toContain('処理に失敗しました')
  progressResponse = async () => response(baseData)
  fireEvent.click(screen.getByText('再取得'))
  expect((await screen.findAllByText('実DB課題')).length).toBeGreaterThan(0)
})

it('401は既存AuthProviderの認証解除処理へ連携する', async () => {
  progressResponse = async () => response({ code: 'AUTHENTICATION_REQUIRED' }, 401)
  open()
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/'))
  expect(screen.queryByText('実DB課題')).toBeNull()
})

const editableComment = { commentNo: 1, authorEmployeeId: 'trainee001', authorName: '実新人', content: '編集前コメント', createdAt: '2026-04-03T10:00:00', updatedAt: '2026-04-03T10:00:00', updateCount: 2 }
function commentFixture(onPut: (options: RequestInit) => Promise<Response> = async () => response({ ...editableComment, content: '編集後コメント', updateCount: 3 })) {
  progressResponse = async () => response({ ...baseData, items: baseData.items.map((item) => ({ ...item, comments: [editableComment,
    { ...editableComment, commentNo: 2, authorEmployeeId: 'admin001', authorName: '実講師', content: '他人のコメント' }] })) })
  fetchMock.mockImplementation(async (url: string, options: RequestInit = {}) => {
    if (url.endsWith('/api/auth/me')) return response(authUser)
    if (url.endsWith('/api/auth/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' })
    if (options.method === 'PUT') return onPut(options)
    if (url.includes('/api/progress')) return progressResponse()
    throw new Error('unexpected request')
  })
}

it('本人だけに編集を表示し、既存内容・updateCountで保存する', async () => {
  commentFixture(async (options) => {
    expect(JSON.parse(options.body as string)).toEqual({ content: '編集後コメント', updateCount: 2 })
    return response({ ...editableComment, content: '編集後コメント', updateCount: 3 })
  })
  open()
  const own = (await screen.findByText('編集前コメント')).closest('li')!
  expect(within(screen.getByText('他人のコメント').closest('li')!).queryByRole('button', { name: '編集' })).toBeNull()
  fireEvent.click(within(own).getByRole('button', { name: '編集' }))
  expect((screen.getByLabelText('コメント編集内容') as HTMLTextAreaElement).value).toBe('編集前コメント')
  fireEvent.change(screen.getByLabelText('コメント編集内容'), { target: { value: '編集後コメント' } })
  fireEvent.click(screen.getByRole('button', { name: 'コメントを保存' }))
  await screen.findByText('編集後コメント')
  expect(screen.queryByLabelText('コメント編集内容')).toBeNull()
  expect(screen.getByText('他人のコメント')).toBeTruthy()
})

it('キャンセルは入力を破棄し、更新リクエストを送らない', async () => {
  commentFixture(); open()
  fireEvent.click(within((await screen.findByText('編集前コメント')).closest('li')!).getByRole('button', { name: '編集' }))
  fireEvent.change(screen.getByLabelText('コメント編集内容'), { target: { value: '破棄する編集' } })
  fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }))
  expect(screen.getByText('編集前コメント')).toBeTruthy()
  expect(fetchMock.mock.calls.filter(([, options]) => options.method === 'PUT')).toHaveLength(0)
})

it.each(['', '   ', 'x'.repeat(201)])('不正な編集内容は保存せず保持する: %s', async (value) => {
  commentFixture(); open()
  fireEvent.click(within((await screen.findByText('編集前コメント')).closest('li')!).getByRole('button', { name: '編集' }))
  fireEvent.change(screen.getByLabelText('コメント編集内容'), { target: { value } })
  fireEvent.click(screen.getByRole('button', { name: 'コメントを保存' }))
  expect(await screen.findByText('コメントは必須、200文字以内で入力してください。')).toBeTruthy()
  expect((screen.getByLabelText('コメント編集内容') as HTMLTextAreaElement).value).toBe(value)
  expect(fetchMock.mock.calls.filter(([, options]) => options.method === 'PUT')).toHaveLength(0)
})

it.each([409, 500])('保存エラー%sで入力と編集開始時の版を保持する', async (status) => {
  commentFixture(async () => response({ code: status === 409 ? 'PROGRESS_UPDATE_CONFLICT' : 'INTERNAL_ERROR' }, status)); open()
  fireEvent.click(within((await screen.findByText('編集前コメント')).closest('li')!).getByRole('button', { name: '編集' }))
  fireEvent.change(screen.getByLabelText('コメント編集内容'), { target: { value: '保存失敗でも保持' } })
  fireEvent.click(screen.getByRole('button', { name: 'コメントを保存' }))
  await screen.findByRole('alert')
  expect((screen.getByLabelText('コメント編集内容') as HTMLTextAreaElement).value).toBe('保存失敗でも保持')
  expect(JSON.parse(fetchMock.mock.calls.find(([, options]) => options.method === 'PUT')![1].body as string).updateCount).toBe(2)
})

it('コメント更新の連打はPUTを1件に制限する', async () => {
  let resolve!: (value: Response) => void
  commentFixture(() => new Promise<Response>((r) => { resolve = r })); open()
  fireEvent.click(within((await screen.findByText('編集前コメント')).closest('li')!).getByRole('button', { name: '編集' }))
  const button = screen.getByRole('button', { name: 'コメントを保存' })
  fireEvent.click(button); fireEvent.click(button)
  await waitFor(() => expect(fetchMock.mock.calls.filter(([, options]) => options.method === 'PUT')).toHaveLength(1))
  await act(async () => resolve(response({ ...editableComment, content: '更新済み', updateCount: 3 })))
  await screen.findByText('更新済み')
})

it.each(['POST', 'PUT', 'DELETE'])('全件検索でコメント%s後のフォーカス復帰でも検索・複数一覧・選択を維持する', async (method) => {
  authUser = instructor
  const rows = [
    { ...baseData.items[0], canEdit: true, comments: [{ ...editableComment, authorEmployeeId: 'admin001' }] },
    { ...baseData.items[0], scheduleId: 'DB002', scheduleName: 'DB別研修', content: '別スケジュール課題', canEdit: true },
  ]
  vi.stubGlobal('confirm', vi.fn(() => true))
  fetchMock.mockImplementation(async (url: string, options: RequestInit = {}) => {
    if (url.endsWith('/api/auth/me')) return response({ ...instructor })
    if (url.endsWith('/api/auth/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' })
    if (options.method === 'DELETE') return new Response(null, { status: 204 })
    if (options.method === 'POST' || options.method === 'PUT') return response({ ...editableComment, authorEmployeeId: 'admin001', content: '保存したコメント', updateCount: 3 })
    if (url.includes('/api/progress')) return response({ ...baseData, viewerRole: 'INSTRUCTOR', items: rows })
    throw new Error('unexpected')
  })
  open()
  fireEvent.click(await screen.findByText('実DB課題'))
  fireEvent.change(screen.getByLabelText('提出者'), { target: { value: '未適用入力' } })
  fireEvent.click(screen.getByText('別スケジュール課題'))
  fireEvent.click(screen.getAllByText('実DB課題')[0])
  expect(new URLSearchParams(window.location.search).has('submitter')).toBe(false)
  if (method === 'DELETE') fireEvent.click(screen.getByRole('button', { name: '削除' }))
  else if (method === 'POST') {
    fireEvent.change(screen.getByLabelText('コメント入力'), { target: { value: '追加内容' } })
    fireEvent.click(screen.getByRole('button', { name: 'コメントを追加' }))
  } else {
    fireEvent.click(screen.getByRole('button', { name: '編集' }))
    fireEvent.click(screen.getByRole('button', { name: 'コメントを保存' }))
  }
  await waitFor(() => expect(fetchMock.mock.calls.filter(([, options]) => options.method === method)).toHaveLength(1))
  if (method === 'DELETE') await waitFor(() => expect(screen.queryByText('編集前コメント')).toBeNull())
  else await screen.findByText('保存したコメント')
  const initialGets = fetchMock.mock.calls.filter(([url, options]) => String(url).includes('/api/progress') && !options.method).length
  fireEvent(window, new Event('focus'))
  await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/api/auth/me'))).toHaveLength(2))
  await act(async () => { await Promise.resolve() })
  expect(fetchMock.mock.calls.filter(([url, options]) => String(url).includes('/api/progress') && !options.method)).toHaveLength(initialGets)
  expect((screen.getByLabelText('スケジュール') as HTMLSelectElement).value).toBe('')
  expect((screen.getByLabelText('提出者') as HTMLInputElement).value).toBe('未適用入力')
  expect(screen.getByText('別スケジュール課題')).toBeTruthy()
  expect(screen.getByText('課題詳細')).toBeTruthy()
  expect(new URLSearchParams(window.location.search).has('scheduleId')).toBe(false)
  expect(new URLSearchParams(window.location.search).get('selectedScheduleId')).toBe('DB001')
})

it('全件一覧の選択URLを再読み込みしても一覧検索を選択課題に絞らない', async () => {
  window.history.replaceState(null, '', '/progress?selectedScheduleId=DB001&scheduleDateNo=2&traineeEmployeeId=trainee001')
  open()
  await screen.findByText('課題詳細')
  const call = fetchMock.mock.calls.find(([url]) => String(url).includes('/api/progress'))!
  expect(call[0]).toBe('http://localhost:8080/api/progress')
  expect((screen.getByLabelText('スケジュール') as HTMLSelectElement).value).toBe('')
})

const savedSubmission = { reviewerEmployeeId: null, reviewerName: null, statusCode: 6, statusName: '完了', completedDate: '2026-04-03', updateCount: 1 }
function submissionMessages(onPut: () => Promise<Response>) {
  const first = { ...baseData.items[0], canEdit: true }
  const second = { ...first, scheduleId: 'DB002', scheduleName: 'DB別研修', content: '別の課題' }
  fetchMock.mockImplementation(async (url: string, options: RequestInit = {}) => {
    if (url.endsWith('/api/auth/me')) return response(authUser)
    if (url.endsWith('/api/auth/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' })
    if (options.method === 'PUT') return onPut()
    return response({ ...baseData, items: [first, second] })
  })
}
it('提出状況の成功時だけ通知し、二重送信・検索・複数一覧・選択を維持する', async () => {
  let finish!: (result: Response) => void
  submissionMessages(() => new Promise((resolve) => { finish = resolve }))
  open(); await screen.findByLabelText('ステータス')
  fireEvent.click(screen.getAllByText('実DB課題')[0])
  const url = window.location.search
  const save = screen.getByRole('button', { name: '保存' })
  fireEvent.click(save); fireEvent.click(save)
  await waitFor(() => expect(fetchMock.mock.calls.filter(([, options]) => options.method === 'PUT')).toHaveLength(1))
  expect(screen.queryByText('更新しました')).toBeNull()
  await act(async () => finish(response(savedSubmission)))
  expect((await screen.findByText('更新しました')).getAttribute('role')).toBe('status')
  expect((screen.getByLabelText('スケジュール') as HTMLSelectElement).value).toBe('')
  expect(screen.getByText('別の課題')).toBeTruthy()
  expect(window.location.search).toBe(url)
  expect(screen.getAllByText('実DB課題')).toHaveLength(2)
})
it('提出状況の再保存に失敗した場合は以前の成功通知も消す', async () => {
  let status = 200
  submissionMessages(async () => response(status === 200 ? savedSubmission : { code: 'INTERNAL_ERROR' }, status))
  open(); await screen.findByLabelText('ステータス')
  fireEvent.click(screen.getByRole('button', { name: '保存' })); await screen.findByText('更新しました')
  status = 500; fireEvent.click(screen.getByRole('button', { name: '保存' }))
  await screen.findByRole('alert')
  expect(screen.queryByText('更新しました')).toBeNull()
})
it('課題切替・検索条件編集・再検索で古い成功通知を残さない', async () => {
  submissionMessages(async () => response(savedSubmission))
  open(); await screen.findByLabelText('ステータス')
  fireEvent.click(screen.getByRole('button', { name: '保存' })); await screen.findByText('更新しました')
  fireEvent.click(screen.getByText('別の課題')); expect(screen.queryByText('更新しました')).toBeNull()
  fireEvent.click(screen.getByText('実DB課題')); expect(screen.queryByText('更新しました')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: '保存' })); await screen.findByText('更新しました')
  fireEvent.change(screen.getByLabelText('提出者'), { target: { value: '実' } })
  expect(screen.queryByText('更新しました')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: '保存' })); await screen.findByText('更新しました')
  fireEvent.click(screen.getByRole('button', { name: '検索' }))
  await waitFor(() => expect(screen.queryByText('更新しました')).toBeNull())
})
it('保存待ち中に検索条件を変更した場合も古い応答の成功通知を出さない', async () => {
  let finish!: (result: Response) => void
  submissionMessages(() => new Promise((resolve) => { finish = resolve }))
  open(); await screen.findByLabelText('ステータス')
  fireEvent.click(screen.getByRole('button', { name: '保存' }))
  await waitFor(() => expect(fetchMock.mock.calls.some(([, options]) => options.method === 'PUT')).toBe(true))
  fireEvent.change(screen.getByLabelText('提出者'), { target: { value: '変更' } })
  await act(async () => finish(response(savedSubmission)))
  expect(screen.queryByText('更新しました')).toBeNull()
  expect(screen.getAllByText('実DB課題')).toHaveLength(2)
})
