import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ScheduleDetail } from '@/components/schedule-detail'
import { AuthProvider } from '@/components/auth/auth-provider'
import { RequireAuth } from '@/components/auth/require-auth'
import { UnsavedChangesProvider } from '@/components/unsaved-changes-guard'

const navigation = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }))
vi.mock('next/navigation', () => ({ useRouter: () => navigation, usePathname: () => '/schedule/S002' }))

const instructor = { syainId: 'instructor001', syainKbn: 2, name: 'テスト講師', nameKana: null, authorities: ['ROLE_INSTRUCTOR'] }
const trainee = { syainId: 'trainee001', syainKbn: 1, name: 'テスト新人', nameKana: null, authorities: ['ROLE_TRAINEE'] }
const detail = {
  scheduleId: 'S002', scheduleName: '実データ研修', startDate: '2026-10-01', endDate: '2026-11-30',
  excludeHolidays: true, canEdit: false, pinned: true, scheduleUpdateCount: 3, permissionUpdateCount: 1,
  permissions: [
    { employeeId: 'instructor001', name: 'テスト講師', nameKana: null, syainKbn: 2, canEdit: true, pinned: false, updateCount: 0, usedAsReviewer: false, hasComments: false },
    { employeeId: 'trainee001', name: 'テスト新人', nameKana: null, syainKbn: 1, canEdit: false, pinned: true, updateCount: 1, usedAsReviewer: false, hasComments: false },
  ],
  categories: [
    { categoryNo: 1, categoryName: 'Java研修', updateCount: 0 },
    { categoryNo: 2, categoryName: 'Web研修', updateCount: 0 },
  ],
  details: [
    { scheduleDateNo: 1, scheduleDate: '2026-10-02', categoryNo: 1, categoryName: 'Java研修', instructorEmployeeId: 'instructor001', instructorName: 'テスト講師', content: '10月の内容', taskFlg: true, taskRemovalBlocked: false, updateCount: 0 },
    { scheduleDateNo: 2, scheduleDate: '2026-11-02', categoryNo: 1, categoryName: 'Java研修', instructorEmployeeId: 'instructor001', instructorName: 'テスト講師', content: '11月の内容', taskFlg: false, taskRemovalBlocked: false, updateCount: 0 },
  ],
}
let authUser: typeof instructor | typeof trainee
let scheduleResponse: () => Promise<Response>
let memberOptionsResponse: () => Promise<Response>
let updateResponse: (options: RequestInit) => Promise<Response>
let deleteResponse: (options: RequestInit) => Promise<Response>
let fetchMock: ReturnType<typeof vi.fn>
const members = [
  { syainId: 'instructor001', syainKbn: 2, name: 'テスト講師', nameKana: null, updateCount: 0 },
  { syainId: 'instructor002', syainKbn: 2, name: '追加対象講師', nameKana: null, updateCount: 0 },
  { syainId: 'trainee001', syainKbn: 1, name: 'テスト新人', nameKana: null, updateCount: 0 },
  { syainId: 'trainee002', syainKbn: 1, name: '追加対象新人', nameKana: null, updateCount: 0 },
]

beforeEach(() => {
  vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080')
  navigation.replace.mockReset()
  navigation.push.mockReset()
  sessionStorage.clear()
  authUser = trainee
  scheduleResponse = async () => new Response(JSON.stringify(detail))
  memberOptionsResponse = async () => new Response(JSON.stringify(members))
  updateResponse = async () => new Response(JSON.stringify(detail))
  deleteResponse = async () => new Response(null, { status: 204 })
  fetchMock = vi.fn(async (url: string, options: RequestInit = {}) => {
    if (url.endsWith('/me')) return new Response(JSON.stringify(authUser))
    if (url.endsWith('/csrf')) return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf-value' }))
    if (url.endsWith('/schedules/S002') && options.method === 'PUT') return updateResponse(options)
    if (url.includes('/schedules/S002?') && options.method === 'DELETE') return deleteResponse(options)
    if (url.endsWith('/schedules/S002')) return scheduleResponse()
    if (url.endsWith('/schedules/member-options')) return memberOptionsResponse()
    throw new Error(`unexpected request: ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

function open() {
  return render(<AuthProvider><RequireAuth><UnsavedChangesProvider><ScheduleDetail scheduleId="S002" /></UnsavedChangesProvider></RequireAuth></AuthProvider>)
}

function beforeUnloadPrevented() {
  const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent
  window.dispatchEvent(event)
  return event.defaultPrevented
}

it('参照権限でも実データ詳細を表示し、編集操作を非活性にする', async () => {
  open()
  expect(await screen.findByText('スケジュール詳細')).toBeTruthy()
  expect(screen.getByDisplayValue('実データ研修')).toBeTruthy()
  expect(screen.getAllByText('Java研修').length).toBeGreaterThan(0)
  expect(screen.getByDisplayValue('10月の内容')).toBeTruthy()
  expect((screen.getByLabelText('スケジュール名') as HTMLInputElement).readOnly).toBe(true)
  expect((screen.getByLabelText('スケジュール名') as HTMLInputElement).disabled).toBe(true)
  expect((screen.getByLabelText('新人を選択') as HTMLSelectElement).disabled).toBe(true)
  expect((screen.getByLabelText('trainee001の権限') as HTMLSelectElement).disabled).toBe(true)
  expect((screen.getByLabelText('trainee001を削除') as HTMLButtonElement).disabled).toBe(true)
  expect((screen.getByLabelText('分類名') as HTMLInputElement).readOnly).toBe(true)
  expect((screen.getByLabelText('1の分類名') as HTMLInputElement).readOnly).toBe(true)
  expect((screen.getByLabelText('1の分類を削除') as HTMLButtonElement).disabled).toBe(true)
  expect((screen.getByLabelText('1の分類') as HTMLSelectElement).disabled).toBe(true)
  expect((screen.getByLabelText('1の内容') as HTMLInputElement).readOnly).toBe(true)
  expect((screen.getByLabelText('1の課題') as HTMLInputElement).disabled).toBe(true)
  expect((screen.getByRole('button', { name: '削除' }) as HTMLButtonElement).disabled).toBe(true)
})

it('編集詳細は取得直後に警告せず、編集権限の差分を戻すと警告を解除する', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  open()
  const permission = await screen.findByLabelText('trainee001の権限') as HTMLSelectElement
  expect(beforeUnloadPrevented()).toBe(false)

  fireEvent.change(permission, { target: { value: '編集' } })
  expect(beforeUnloadPrevented()).toBe(true)
  fireEvent.click(screen.getByRole('link', { name: 'スケジュール一覧へ戻る' }))
  const dialog = screen.getByRole('dialog', { name: '編集内容が保存されていません' })
  fireEvent.click(within(dialog).getByRole('button', { name: '編集を続ける' }))
  expect(permission.value).toBe('編集')

  fireEvent.change(permission, { target: { value: '参照のみ' } })
  expect(beforeUnloadPrevented()).toBe(false)
})

it('参照モードでも月変更は可能で、変更後も編集操作は非活性のまま', async () => {
  open()
  expect(await screen.findByDisplayValue('10月の内容')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '次月' }))
  expect(await screen.findByText('2026年11月')).toBeTruthy()
  expect(screen.getByDisplayValue('11月の内容')).toBeTruthy()
  expect(screen.queryByDisplayValue('10月の内容')).toBeNull()
  expect((screen.getByLabelText('2の分類') as HTMLSelectElement).disabled).toBe(true)
  expect((screen.getByLabelText('2の内容') as HTMLInputElement).readOnly).toBe(true)
  fireEvent.click(screen.getByRole('button', { name: '前月' }))
  expect(await screen.findByDisplayValue('10月の内容')).toBeTruthy()
})

it('講師かつcanEdit=trueでは名称と既存の編集項目を編集できる', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  open()
  expect(await screen.findByText('スケジュール編集')).toBeTruthy()
  expect((screen.getByLabelText('スケジュール名') as HTMLInputElement).disabled).toBe(false)
  expect((screen.getByLabelText('スケジュール名') as HTMLInputElement).readOnly).toBe(false)
  expect((screen.getByLabelText('新人を選択') as HTMLSelectElement).disabled).toBe(false)
  expect((screen.getByLabelText('trainee001の権限') as HTMLSelectElement).disabled).toBe(false)
  expect((screen.getByLabelText('trainee001を削除') as HTMLButtonElement).disabled).toBe(true)
  expect(screen.getByText('新人は1人以上登録する必要があります。')).toBeTruthy()
  expect((screen.getByLabelText('分類名') as HTMLInputElement).readOnly).toBe(false)
  expect((screen.getByLabelText('1の分類名') as HTMLInputElement).readOnly).toBe(false)
  expect((screen.getByLabelText('1の分類を削除') as HTMLButtonElement).disabled).toBe(false)
  expect((screen.getByLabelText('1の分類') as HTMLSelectElement).disabled).toBe(false)
  expect((screen.getByLabelText('1の講師') as HTMLSelectElement).disabled).toBe(false)
  expect((screen.getByLabelText('1の内容') as HTMLInputElement).readOnly).toBe(false)
  expect((screen.getByLabelText('1の課題') as HTMLInputElement).disabled).toBe(false)
  expect((screen.getByRole('button', { name: '削除' }) as HTMLButtonElement).disabled).toBe(false)
  fireEvent.change(screen.getByLabelText('新人を選択'), { target: { value: 'trainee002' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[1])
  expect(screen.getByText('追加対象新人')).toBeTruthy()
  expect((screen.getByLabelText('trainee001を削除') as HTMLButtonElement).disabled).toBe(false)
  expect((screen.getByLabelText('trainee002を削除') as HTMLButtonElement).disabled).toBe(false)
  fireEvent.change(screen.getByLabelText('分類名'), { target: { value: '追加分類' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[2])
  expect(screen.getByDisplayValue('追加分類')).toBeTruthy()
  fireEvent.change(screen.getByLabelText('1の内容'), { target: { value: '変更後の内容' } })
  expect(screen.getByDisplayValue('変更後の内容')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '次月' }))
  expect(await screen.findByText('2026年11月')).toBeTruthy()
})

it('新人でもcanEdit=trueなら講師と同じスケジュール編集・削除操作ができる', async () => {
  authUser = trainee
  scheduleResponse = async () => new Response(JSON.stringify({
    ...detail,
    canEdit: true,
    permissions: detail.permissions.map((permission) => permission.employeeId === trainee.syainId
      ? { ...permission, canEdit: true }
      : permission),
  }))
  open()

  expect(await screen.findByText('スケジュール編集')).toBeTruthy()
  expect((screen.getByLabelText('スケジュール名') as HTMLInputElement).disabled).toBe(false)
  expect((screen.getByLabelText('新人を選択') as HTMLSelectElement).disabled).toBe(false)
  expect((screen.getByLabelText('分類名') as HTMLInputElement).readOnly).toBe(false)
  expect((screen.getByLabelText('1の分類') as HTMLSelectElement).disabled).toBe(false)
  expect((screen.getByLabelText('1の内容') as HTMLInputElement).readOnly).toBe(false)
  expect((screen.getByRole('button', { name: '削除' }) as HTMLButtonElement).disabled).toBe(false)
  expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/schedules/member-options'))).toBe(true)
  expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/members'))).toBe(false)
})

it('新人の追加・削除に応じて最後の新人の削除を即時に禁止する', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  open()
  const firstTraineeDelete = await screen.findByLabelText('trainee001を削除') as HTMLButtonElement
  expect(firstTraineeDelete.disabled).toBe(true)
  expect(firstTraineeDelete.title).toBe('新人は1人以上登録する必要があります。')

  fireEvent.change(screen.getByLabelText('新人を選択'), { target: { value: 'trainee002' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[1])
  expect(firstTraineeDelete.disabled).toBe(false)

  const secondTraineeDelete = screen.getByLabelText('trainee002を削除') as HTMLButtonElement
  expect(secondTraineeDelete.disabled).toBe(false)
  fireEvent.click(secondTraineeDelete)
  expect(screen.queryByLabelText('trainee002を削除')).toBeNull()
  expect(firstTraineeDelete.disabled).toBe(true)
})

it('講師の追加・削除・権限変更に応じて最後の編集可能講師を即時に保護する', async () => {
  authUser = trainee
  scheduleResponse = async () => new Response(JSON.stringify({
    ...detail,
    canEdit: true,
    permissions: detail.permissions.map((permission) => permission.employeeId === trainee.syainId
      ? { ...permission, canEdit: true }
      : permission),
  }))
  open()

  const firstPermission = await screen.findByLabelText('instructor001の権限') as HTMLSelectElement
  const firstDelete = screen.getByLabelText('instructor001を削除') as HTMLButtonElement
  expect(firstPermission.disabled).toBe(true)
  expect(firstDelete.disabled).toBe(true)
  expect(firstDelete.title).toBe('編集権限を持つ講師を1人以上登録する必要があります。')

  fireEvent.change(screen.getByLabelText('講師を選択'), { target: { value: 'instructor002' } })
  fireEvent.change(screen.getByLabelText('講師の権限'), { target: { value: '編集' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[0])
  expect(firstPermission.disabled).toBe(false)
  expect(firstDelete.disabled).toBe(false)

  fireEvent.change(firstPermission, { target: { value: '参照のみ' } })
  expect((screen.getByLabelText('instructor001の権限') as HTMLSelectElement).value).toBe('参照のみ')
  expect((screen.getByLabelText('instructor002の権限') as HTMLSelectElement).disabled).toBe(true)
  expect((screen.getByLabelText('instructor002を削除') as HTMLButtonElement).disabled).toBe(true)
})

it.each([
  ['講師', 'instructor002', 2, '追加対象講師', true, false],
  ['新人', 'trainee002', 1, '追加対象新人', true, false],
  ['講師', 'instructor002', 2, '追加対象講師', false, true],
  ['新人', 'trainee002', 1, '追加対象新人', false, true],
] as const)('%sが進捗・課題管理で使用中なら削除せずダイアログを表示する', async (
  _role, employeeId, syainKbn, name, usedAsReviewer, hasComments,
) => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({
    ...detail,
    canEdit: true,
    permissions: [...detail.permissions, {
      employeeId, name, nameKana: null, syainKbn, canEdit: usedAsReviewer,
      pinned: false, updateCount: 0, usedAsReviewer, hasComments,
    }],
  }))
  open()
  await screen.findByText('スケジュール編集')
  fireEvent.change(screen.getByLabelText('1の内容'), { target: { value: '維持される編集中内容' } })

  const removeButton = screen.getByLabelText(`${employeeId}を削除`) as HTMLButtonElement
  expect(removeButton.disabled).toBe(false)
  fireEvent.click(removeButton)

  const dialog = await screen.findByRole('dialog', { name: '削除できません' })
  expect(dialog.textContent).toContain('進捗・課題管理でレビュー者として登録、またはコメントを記載しているため削除できません')
  expect(screen.getByLabelText(`${employeeId}を削除`)).toBeTruthy()
  expect(screen.getByDisplayValue('維持される編集中内容')).toBeTruthy()
  expect(screen.queryByRole('alert')).toBeNull()
  fireEvent.click(within(dialog).getByRole('button', { name: 'OK' }))
  await waitFor(() => expect(document.activeElement).toBe(removeButton))
})

it.each([
  ['講師', 'instructor002', 2, '追加対象講師'],
  ['新人', 'trainee002', 1, '追加対象新人'],
] as const)('レビュー者である%sの編集権限は参照のみに変更できない', async (
  _role, employeeId, syainKbn, name,
) => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({
    ...detail,
    canEdit: true,
    permissions: [...detail.permissions, {
      employeeId, name, nameKana: null, syainKbn, canEdit: true,
      pinned: false, updateCount: 0, usedAsReviewer: true, hasComments: false,
    }],
  }))
  open()

  const permissionSelect = await screen.findByLabelText(`${employeeId}の権限`) as HTMLSelectElement
  expect(permissionSelect.value).toBe('編集')
  expect(permissionSelect.disabled).toBe(true)
  expect(permissionSelect.title).toBe('進捗・課題管理でレビュー者として登録されているため参照のみに変更できません')
})

it('コメント投稿者であるだけなら編集権限を参照のみに変更できる', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({
    ...detail,
    canEdit: true,
    permissions: [...detail.permissions, {
      employeeId: 'instructor002', name: '追加対象講師', nameKana: null, syainKbn: 2,
      canEdit: true, pinned: false, updateCount: 0, usedAsReviewer: false, hasComments: true,
    }],
  }))
  open()

  const permissionSelect = await screen.findByLabelText('instructor002の権限') as HTMLSelectElement
  expect(permissionSelect.disabled).toBe(false)
  fireEvent.change(permissionSelect, { target: { value: '参照のみ' } })
  expect(permissionSelect.value).toBe('参照のみ')
})

it('編集可能な講師がいない既存状態ではエラーを表示して更新できない', async () => {
  authUser = trainee
  scheduleResponse = async () => new Response(JSON.stringify({
    ...detail,
    canEdit: true,
    permissions: detail.permissions.map((permission) => ({
      ...permission,
      canEdit: permission.employeeId === trainee.syainId,
    })),
  }))
  open()

  expect(await screen.findByText('編集権限を持つ講師を1人以上登録する必要があります。')).toBeTruthy()
  fireEvent.change(screen.getByLabelText('1の内容'), { target: { value: '保存不可' } })
  expect((screen.getByRole('button', { name: '更新' }) as HTMLButtonElement).disabled).toBe(true)
  expect(fetchMock.mock.calls.some(([url, options]) => url.endsWith('/schedules/S002') && options?.method === 'PUT')).toBe(false)
})

it('新人を削除すると現在の画面状態から候補へ戻り、編集権限で再追加して保存後も維持する', async () => {
  authUser = instructor
  const assigned = {
    ...detail,
    canEdit: true,
    permissions: [...detail.permissions, {
      employeeId: 'trainee002', name: '追加対象新人', nameKana: null, syainKbn: 1,
      canEdit: false, pinned: false, updateCount: 0, usedAsReviewer: false, hasComments: false,
    }],
  }
  const updated = {
    ...assigned,
    scheduleUpdateCount: 4,
    permissions: assigned.permissions.map((permission) => permission.employeeId === 'trainee002'
      ? { ...permission, canEdit: true, updateCount: 1 }
      : permission),
  }
  let detailGets = 0
  scheduleResponse = async () => new Response(JSON.stringify(detailGets++ === 0 ? assigned : updated))
  updateResponse = async () => new Response(JSON.stringify(updated))
  open()

  const traineeSelect = await screen.findByLabelText('新人を選択')
  expect(within(traineeSelect).queryByRole('option', { name: '追加対象新人' })).toBeNull()
  fireEvent.click(screen.getByLabelText('trainee002を削除'))
  expect(within(traineeSelect).getByRole('option', { name: '追加対象新人' })).toBeTruthy()

  fireEvent.change(traineeSelect, { target: { value: 'trainee002' } })
  fireEvent.change(screen.getByLabelText('新人の権限'), { target: { value: '編集' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[1])
  expect((screen.getByLabelText('trainee002の権限') as HTMLSelectElement).value).toBe('編集')
  expect(within(traineeSelect).queryByRole('option', { name: '追加対象新人' })).toBeNull()

  // optionがない値を直接設定されても、同じ社員を重複追加しない。
  fireEvent.change(traineeSelect, { target: { value: 'trainee002' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[1])
  expect(screen.getAllByLabelText('trainee002の権限')).toHaveLength(1)

  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  expect(await screen.findByText('スケジュールを更新しました')).toBeTruthy()
  const putCall = fetchMock.mock.calls.find(([url, options]) => url.endsWith('/schedules/S002') && options?.method === 'PUT')
  const body = JSON.parse(String(putCall?.[1]?.body))
  expect(body.permissions).toContainEqual({ employeeId: 'trainee002', canEdit: true, updateCount: 0 })
  expect(body.deletedPermissions).not.toContainEqual(expect.objectContaining({ employeeId: 'trainee002' }))
  expect((screen.getByLabelText('trainee002の権限') as HTMLSelectElement).value).toBe('編集')
})

it('新人の削除を保存・再取得した後も候補に表示し、編集権限で再追加できる', async () => {
  authUser = instructor
  const assigned = {
    ...detail,
    canEdit: true,
    permissions: [...detail.permissions, {
      employeeId: 'trainee002', name: '追加対象新人', nameKana: null, syainKbn: 1,
      canEdit: false, pinned: false, updateCount: 0, usedAsReviewer: false, hasComments: false,
    }],
  }
  const removed = { ...assigned, scheduleUpdateCount: 4, permissions: detail.permissions }
  const readded = {
    ...removed,
    scheduleUpdateCount: 5,
    permissions: [...removed.permissions, {
      employeeId: 'trainee002', name: '追加対象新人', nameKana: null, syainKbn: 1,
      canEdit: true, pinned: false, updateCount: 0, usedAsReviewer: false, hasComments: false,
    }],
  }
  let serverState = assigned
  scheduleResponse = async () => new Response(JSON.stringify(serverState))
  updateResponse = async (options) => {
    const body = JSON.parse(String(options.body))
    serverState = body.permissions.some((permission: { employeeId: string }) => permission.employeeId === 'trainee002')
      ? readded : removed
    return new Response(JSON.stringify(serverState))
  }
  open()
  await screen.findByLabelText('trainee002を削除')

  fireEvent.click(screen.getByLabelText('trainee002を削除'))
  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  await waitFor(() => expect(fetchMock.mock.calls.filter(([, options]) => options?.method === 'PUT')).toHaveLength(1))
  expect(await screen.findByText('スケジュールを更新しました')).toBeTruthy()
  const traineeSelect = screen.getByLabelText('新人を選択')
  expect(within(traineeSelect).getByRole('option', { name: '追加対象新人' })).toBeTruthy()

  fireEvent.change(traineeSelect, { target: { value: 'trainee002' } })
  fireEvent.change(screen.getByLabelText('新人の権限'), { target: { value: '編集' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[1])
  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  await waitFor(() => expect(fetchMock.mock.calls.filter(([, options]) => options?.method === 'PUT')).toHaveLength(2))
  expect(await screen.findByText('スケジュールを更新しました')).toBeTruthy()
  expect((screen.getByLabelText('trainee002の権限') as HTMLSelectElement).value).toBe('編集')
})

it('講師も削除直後に候補へ戻り、重複せず再追加できる', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({
    ...detail,
    canEdit: true,
    permissions: [...detail.permissions, {
      employeeId: 'instructor002', name: '追加対象講師', nameKana: null, syainKbn: 2,
      canEdit: false, pinned: false, updateCount: 0, usedAsReviewer: false, hasComments: false,
    }],
  }))
  open()

  const instructorSelect = await screen.findByLabelText('講師を選択')
  expect(within(instructorSelect).queryByRole('option', { name: '追加対象講師' })).toBeNull()
  fireEvent.click(screen.getByLabelText('instructor002を削除'))
  expect(within(instructorSelect).getByRole('option', { name: '追加対象講師' })).toBeTruthy()
  fireEvent.change(instructorSelect, { target: { value: 'instructor002' } })
  fireEvent.click(screen.getAllByRole('button', { name: '追加' })[0])
  expect(screen.getAllByLabelText('instructor002の権限')).toHaveLength(1)
  expect(within(instructorSelect).queryByRole('option', { name: '追加対象講師' })).toBeNull()
})

it('候補APIが失敗しても詳細取得済みメンバーは削除直後に再追加でき、失敗を隠さない', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({
    ...detail,
    canEdit: true,
    permissions: [...detail.permissions, {
      employeeId: 'trainee002', name: '追加対象新人', nameKana: null, syainKbn: 1,
      canEdit: false, pinned: false, updateCount: 0, usedAsReviewer: false, hasComments: false,
    }],
  }))
  memberOptionsResponse = async () => new Response(JSON.stringify({ code: 'INTERNAL_ERROR' }), { status: 500 })
  open()

  expect((await screen.findByRole('alert')).textContent).toContain('メンバー候補を取得できませんでした')
  fireEvent.click(screen.getByLabelText('trainee002を削除'))
  expect(within(screen.getByLabelText('新人を選択')).getByRole('option', { name: '追加対象新人' })).toBeTruthy()
})

it('現在のdetailsで使用中の分類は削除せず、操作メッセージをモーダル表示する', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  open()
  await screen.findByDisplayValue('10月の内容')
  const deleteButton = screen.getByLabelText('1の分類を削除') as HTMLButtonElement

  fireEvent.click(deleteButton)

  const dialog = await screen.findByRole('dialog', { name: '削除できません' })
  expect(dialog.textContent).toContain('「Java研修」はスケジュール内容で使用されているため削除できません。')
  expect(screen.getByLabelText('1の分類名')).toBeTruthy()
  expect(screen.queryByRole('alert')).toBeNull()

  const okButton = within(dialog).getByRole('button', { name: 'OK' })
  expect(document.activeElement).toBe(okButton)
  fireEvent.click(okButton)
  expect(screen.queryByRole('dialog')).toBeNull()
  await waitFor(() => expect(document.activeElement).toBe(deleteButton))

  fireEvent.click(deleteButton)
  expect(await screen.findByRole('dialog', { name: '削除できません' })).toBeTruthy()
  fireEvent.keyDown(document, { key: 'Escape' })
  expect(screen.queryByRole('dialog')).toBeNull()
  await waitFor(() => expect(document.activeElement).toBe(deleteButton))
})

it('全detailsを別分類へ変更した後は旧分類を削除できる', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  open()
  await screen.findByDisplayValue('10月の内容')

  fireEvent.change(screen.getByLabelText('1の分類'), { target: { value: '2' } })
  fireEvent.click(screen.getByRole('button', { name: '次月' }))
  await screen.findByDisplayValue('11月の内容')
  fireEvent.change(screen.getByLabelText('2の分類'), { target: { value: '2' } })
  fireEvent.click(screen.getByLabelText('1の分類を削除'))

  expect(screen.queryByLabelText('1の分類名')).toBeNull()
  expect(screen.queryByText(/使用されているため削除できません/)).toBeNull()
})

it('変更時だけ更新でき、二重送信せず、成功後に詳細を再取得する', async () => {
  authUser = instructor
  const editable = { ...detail, canEdit: true }
  const updated = { ...editable, scheduleUpdateCount: 4, details: [{ ...editable.details[0], content: 'DB更新後' }, editable.details[1]] }
  let detailGets = 0
  scheduleResponse = async () => new Response(JSON.stringify(detailGets++ === 0 ? editable : updated))
  let resolveUpdate!: (response: Response) => void
  updateResponse = () => new Promise<Response>((resolve) => { resolveUpdate = resolve })
  open()
  const updateButton = await screen.findByRole('button', { name: '更新' }) as HTMLButtonElement
  const clearButton = screen.getByRole('button', { name: 'クリア' }) as HTMLButtonElement
  expect(updateButton.disabled).toBe(true)
  expect(clearButton.disabled).toBe(true)
  fireEvent.change(screen.getByLabelText('1の内容'), { target: { value: '画面変更' } })
  expect(updateButton.disabled).toBe(false)
  expect(clearButton.disabled).toBe(false)
  fireEvent.click(updateButton)
  fireEvent.click(updateButton)
  await waitFor(() => expect(fetchMock.mock.calls.filter(([url, options]) => url.endsWith('/schedules/S002') && options?.method === 'PUT')).toHaveLength(1))
  expect((screen.getByRole('button', { name: '更新中…' }) as HTMLButtonElement).disabled).toBe(true)
  await act(async () => resolveUpdate(new Response(JSON.stringify(updated))))
  expect(await screen.findByText('スケジュールを更新しました')).toBeTruthy()
  expect(screen.getByDisplayValue('DB更新後')).toBeTruthy()
  expect(beforeUnloadPrevented()).toBe(false)
  expect(detailGets).toBe(2)
  const putCall = fetchMock.mock.calls.find(([url, options]) => url.endsWith('/schedules/S002') && options?.method === 'PUT')
  const body = JSON.parse(String(putCall?.[1]?.body))
  expect(body.scheduleUpdateCount).toBe(3)
  expect(body.details[0].content).toBe('画面変更')
  expect(body.scheduleName).toBe(detail.scheduleName)
})

it('クリアはDB更新せず最後に取得した状態へ戻す', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  open()
  expect(await screen.findByDisplayValue('10月の内容')).toBeTruthy()
  fireEvent.change(screen.getByLabelText('1の内容'), { target: { value: '破棄対象' } })
  fireEvent.click(screen.getByRole('button', { name: 'クリア' }))
  expect(screen.getByDisplayValue('10月の内容')).toBeTruthy()
  expect(fetchMock.mock.calls.some(([url, options]) => url.endsWith('/schedules/S002') && options?.method === 'PUT')).toBe(false)
  expect((screen.getByRole('button', { name: '更新' }) as HTMLButtonElement).disabled).toBe(true)
})

it('削除確認のキャンセルではDELETEせず、確定時は二重送信を防いで一覧へ遷移する', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  let resolveDelete!: (response: Response) => void
  deleteResponse = () => new Promise<Response>((resolve) => { resolveDelete = resolve })
  open()
  const deleteButton = await screen.findByRole('button', { name: '削除' })

  fireEvent.click(deleteButton)
  let dialog = await screen.findByRole('dialog', { name: 'スケジュールを削除しますか？' })
  expect(dialog.textContent).toContain('「実データ研修」を削除します。')
  fireEvent.click(within(dialog).getByRole('button', { name: 'キャンセル' }))
  expect(fetchMock.mock.calls.some(([url, options]) => url.includes('/schedules/S002?') && options?.method === 'DELETE')).toBe(false)

  fireEvent.click(deleteButton)
  dialog = await screen.findByRole('dialog', { name: 'スケジュールを削除しますか？' })
  const confirm = within(dialog).getByRole('button', { name: '削除' })
  fireEvent.click(confirm)
  fireEvent.click(confirm)
  await waitFor(() => expect(fetchMock.mock.calls.filter(([url, options]) => url.includes('/schedules/S002?') && options?.method === 'DELETE')).toHaveLength(1))
  expect((within(dialog).getByRole('button', { name: '削除中…' }) as HTMLButtonElement).disabled).toBe(true)
  expect((within(dialog).getByRole('button', { name: 'キャンセル' }) as HTMLButtonElement).disabled).toBe(true)

  await act(async () => resolveDelete(new Response(null, { status: 204 })))
  expect(navigation.push).toHaveBeenCalledWith('/schedule')
  expect(sessionStorage.getItem('schedule-deleted-message')).toBe('スケジュールを削除しました')
})

it('課題提出状況がある削除エラーは詳細を維持してメッセージダイアログに表示する', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  deleteResponse = async () => new Response(JSON.stringify({
    code: 'INVALID_INPUT',
    message: '課題提出状況が登録されているため、このスケジュールは削除できません。',
  }), { status: 400 })
  open()
  fireEvent.click(await screen.findByRole('button', { name: '削除' }))
  fireEvent.click(within(await screen.findByRole('dialog', { name: 'スケジュールを削除しますか？' })).getByRole('button', { name: '削除' }))

  const dialog = await screen.findByRole('dialog', { name: '削除できません' })
  expect(dialog.textContent).toContain('課題提出状況が登録されているため、このスケジュールは削除できません。')
  expect(screen.getByDisplayValue('実データ研修')).toBeTruthy()
  expect(screen.queryByRole('alert')).toBeNull()
  expect(navigation.push).not.toHaveBeenCalled()
})

it.each([
  [403, 'ACCESS_DENIED', 'この操作を行う権限がありません'],
  [409, 'SCHEDULE_UPDATE_CONFLICT', '他のユーザーによって更新されています。最新の情報を再読み込みしてください。'],
])('削除APIの%sエラーは既存の画面上部表示を維持する', async (status, code, message) => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  deleteResponse = async () => new Response(JSON.stringify({ code }), { status })
  open()
  fireEvent.click(await screen.findByRole('button', { name: '削除' }))
  fireEvent.click(within(await screen.findByRole('dialog', { name: 'スケジュールを削除しますか？' })).getByRole('button', { name: '削除' }))
  expect((await screen.findByRole('alert')).textContent).toContain(message)
  expect(screen.getByDisplayValue('実データ研修')).toBeTruthy()
})

it('削除APIの401は既存AuthProviderによりログイン画面へ戻る', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  deleteResponse = async () => new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED' }), { status: 401 })
  open()
  fireEvent.click(await screen.findByRole('button', { name: '削除' }))
  fireEvent.click(within(await screen.findByRole('dialog', { name: 'スケジュールを削除しますか？' })).getByRole('button', { name: '削除' }))
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/'))
})

it.each([
  [400, 'INVALID_INPUT', '入力内容を確認してください'],
  [403, 'ACCESS_DENIED', 'この操作を行う権限がありません'],
  [409, 'SCHEDULE_UPDATE_CONFLICT', '他のユーザーによって更新されています。最新の情報を再読み込みしてください。'],
])('更新APIの%sエラーを表示する', async (status, code, message) => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  updateResponse = async () => new Response(JSON.stringify({ code }), { status })
  open()
  await screen.findByDisplayValue('10月の内容')
  fireEvent.change(screen.getByLabelText('1の内容'), { target: { value: 'エラー確認' } })
  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  expect((await screen.findByRole('alert')).textContent).toContain(message)
  expect(beforeUnloadPrevented()).toBe(true)
})

it.each([
  '新人は1人以上登録する必要があります。',
  '編集権限を持つ講師を1人以上登録する必要があります。',
  'この分類はスケジュール内容で使用されているため削除できません。',
  '進捗・課題管理でレビュー者として登録、またはコメントを記載しているため削除できません',
  '進捗・課題管理でレビュー者として登録されているため参照のみに変更できません',
])('更新APIのINVALID_INPUT業務エラー「%s」を表示する', async (message) => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  updateResponse = async () => new Response(JSON.stringify({ code: 'INVALID_INPUT', message }), { status: 400 })
  open()
  await screen.findByDisplayValue('10月の内容')
  fireEvent.change(screen.getByLabelText('1の内容'), { target: { value: 'エラー確認' } })
  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  expect((await screen.findByRole('alert')).textContent).toContain(message)
})

it('更新APIの401は既存AuthProviderによりログイン画面へ戻る', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  updateResponse = async () => new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED' }), { status: 401 })
  open()
  await screen.findByDisplayValue('10月の内容')
  fireEvent.change(screen.getByLabelText('1の内容'), { target: { value: 'セッション切れ' } })
  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/'))
})

it('詳細APIの401は既存AuthProviderによりログイン画面へ戻る', async () => {
  scheduleResponse = async () => new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED' }), { status: 401 })
  open()
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/'))
  expect(screen.queryByText('スケジュール詳細')).toBeNull()
})

it('権限のない詳細APIの403では内容を表示しない', async () => {
  scheduleResponse = async () => new Response(JSON.stringify({ code: 'ACCESS_DENIED' }), { status: 403 })
  open()
  expect((await screen.findByRole('alert')).textContent).toContain('この操作を行う権限がありません')
  expect(screen.queryByDisplayValue('実データ研修')).toBeNull()
})

it('Progress利用状況が欠落した詳細レスポンスを制約なしとして表示しない', async () => {
  const permissionsWithoutUsage = detail.permissions.map(({
    usedAsReviewer: _usedAsReviewer,
    hasComments: _hasComments,
    ...permission
  }) => permission)
  scheduleResponse = async () => new Response(JSON.stringify({
    ...detail,
    canEdit: true,
    permissions: permissionsWithoutUsage,
  }))
  open()

  expect((await screen.findByRole('alert')).textContent).toContain('サーバーからの応答を確認できませんでした')
  expect(screen.queryByDisplayValue('実データ研修')).toBeNull()
})

it('NULL内容を編集でき、内容を消すと課題チェックを外す', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true, details: detail.details.map(row => ({ ...row, content: null, taskFlg: false })) }))
  open()
  const content = await screen.findByLabelText('1の内容') as HTMLInputElement
  const task = screen.getByLabelText('1の課題') as HTMLInputElement
  expect(content.value).toBe('')
  expect(task.disabled).toBe(true)
  fireEvent.change(content, { target: { value: '課題内容' } })
  expect(task.disabled).toBe(false)
  fireEvent.click(task)
  expect(task.checked).toBe(true)
  fireEvent.change(content, { target: { value: '   ' } })
  expect(task.checked).toBe(false)
  expect(task.disabled).toBe(true)
})

it.each(['checkbox', 'blank', 'whitespace'])('解除禁止の課題は値を保持しポップアップを表示する: %s', async (operation) => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true,
    details: detail.details.map((row) => ({ ...row, taskRemovalBlocked: row.scheduleDateNo === 1 })) }))
  open()
  const content = await screen.findByLabelText('1の内容') as HTMLInputElement
  const task = screen.getByLabelText('1の課題') as HTMLInputElement
  if (operation === 'checkbox') fireEvent.click(task)
  else fireEvent.change(content, { target: { value: operation === 'blank' ? '' : '   ' } })
  expect((await screen.findByRole('heading', { name: '課題を解除できません' })).className).toContain('text-[#DC2626]')
  expect(screen.getByText('未提出以外の提出状況、またはコメントが登録されているため、課題を解除できません。')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'OK' }))
  expect(task.checked).toBe(true)
  expect(content.value).toBe('10月の内容')
})

it('名称変更は未保存警告の対象となり、保存成功後の名称を表示する', async () => {
  authUser = instructor
  let current = { ...detail, canEdit: true }
  scheduleResponse = async () => new Response(JSON.stringify(current))
  updateResponse = async (options) => {
    const request = JSON.parse(options.body as string)
    expect(request.scheduleName).toBe('変更後研修')
    expect(request).not.toHaveProperty('startDate')
    current = { ...current, scheduleName: request.scheduleName, scheduleUpdateCount: 4 }
    return new Response(JSON.stringify(current))
  }
  open()
  const name = await screen.findByLabelText('スケジュール名') as HTMLInputElement
  fireEvent.change(name, { target: { value: '変更後研修' } })
  expect(beforeUnloadPrevented()).toBe(true)
  fireEvent.change(name, { target: { value: detail.scheduleName } })
  expect(beforeUnloadPrevented()).toBe(false)
  fireEvent.change(name, { target: { value: '変更後研修' } })
  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  await screen.findByText('スケジュールを更新しました')
  expect(name.value).toBe('変更後研修')
  expect(beforeUnloadPrevented()).toBe(false)
})

it.each(['', '   ', '名'.repeat(41)])('不正な名称は保存APIへ送らない: %s', async (value) => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  open()
  const name = await screen.findByLabelText('スケジュール名')
  fireEvent.change(name, { target: { value } })
  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  const error = await screen.findByText('スケジュール名は必須、40文字以内で入力してください。')
  expect(error.className).toContain('text-[#DC2626]')
  expect(name.getAttribute('aria-invalid')).toBe('true')
  expect(name.getAttribute('aria-describedby')).toBe(error.id)
  expect(name.className).toContain('aria-invalid:border-[#DC2626]')
  expect(name.className).toContain('aria-invalid:focus:border-[#DC2626]')
  fireEvent.change(name, { target: { value: '正常な名称' } })
  expect(name.getAttribute('aria-invalid')).toBe('false')
  expect(name.hasAttribute('aria-describedby')).toBe(false)
  expect(screen.queryByText('スケジュール名は必須、40文字以内で入力してください。')).toBeNull()
  expect(fetchMock.mock.calls.filter(([, options]) => options.method === 'PUT')).toHaveLength(0)
})

it('名称保存失敗時は入力と未保存警告を保持する', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  updateResponse = async () => new Response(JSON.stringify({ code: 'SCHEDULE_UPDATE_CONFLICT' }), { status: 409 })
  open()
  const name = await screen.findByLabelText('スケジュール名') as HTMLInputElement
  fireEvent.change(name, { target: { value: '名'.repeat(40) } })
  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  await screen.findByText('他のユーザーによって更新されています。最新の情報を再読み込みしてください。')
  expect(name.value).toBe('名'.repeat(40))
  expect(name.getAttribute('aria-invalid')).toBe('false')
  expect(name.hasAttribute('aria-describedby')).toBe(false)
  expect(beforeUnloadPrevented()).toBe(true)
})

it.each(['Java研修', '  Java研修  '])('編集時の分類名重複を表示し、入力を保持して保存を送信しない: %s', async (duplicate) => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  open()
  const field = await screen.findByLabelText('2の分類名') as HTMLInputElement
  fireEvent.change(field, { target: { value: duplicate } })
  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  expect(await screen.findByText('同じ分類名は登録できません')).toBeTruthy()
  expect(field.value).toBe(duplicate)
  expect(fetchMock.mock.calls.filter(([, options]) => options.method === 'PUT')).toHaveLength(0)
  fireEvent.change(field, { target: { value: '重複しない分類' } })
  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  await waitFor(() => expect(fetchMock.mock.calls.filter(([, options]) => options.method === 'PUT')).toHaveLength(1))
  expect(screen.queryByText('同じ分類名は登録できません')).toBeNull()
})

it('既存分類と同名の分類追加も保存時に拒否する', async () => {
  authUser = instructor
  scheduleResponse = async () => new Response(JSON.stringify({ ...detail, canEdit: true }))
  open()
  const field = await screen.findByLabelText('分類名')
  fireEvent.change(field, { target: { value: ' Java研修 ' } })
  const section = field.closest('section')!
  fireEvent.click(within(section).getByRole('button', { name: '追加' }))
  fireEvent.click(screen.getByRole('button', { name: '更新' }))
  expect(await screen.findByText('同じ分類名は登録できません')).toBeTruthy()
  expect(fetchMock.mock.calls.filter(([, options]) => options.method === 'PUT')).toHaveLength(0)
})
