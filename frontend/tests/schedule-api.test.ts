import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createSchedule, deleteSchedule, getNonWorkingDates, getSchedule, getScheduleMemberOptions, getSchedules, updateSchedule, updateSchedulePin } from '@/lib/schedule-api'

const schedule = {
  scheduleId: 'S001', scheduleName: 'API研修', startDate: '2026-04-01', endDate: '2026-04-30',
  canEdit: true, pinned: true, scheduleUpdateCount: 3, permissionUpdateCount: 1,
}
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080')
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

it('一覧APIをCookie付きで呼び出し、画面用の項目だけを返す', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify([{ ...schedule, internal: 'private' }])))
  expect(await getSchedules()).toEqual([schedule])
  expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/schedules', expect.objectContaining({ credentials: 'include' }))
})

it('詳細APIを社員IDを渡さずCookie付きで呼び出す', async () => {
  const detail = {
    ...schedule,
    excludeHolidays: true,
    permissions: [],
    categories: [],
    details: [],
  }
  fetchMock.mockResolvedValue(new Response(JSON.stringify(detail)))
  expect(await getSchedule('S 001')).toEqual(detail)
  expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/schedules/S%20001', expect.objectContaining({ credentials: 'include' }))
})

it('詳細APIは権限ごとのProgress利用状況を受け取る', async () => {
  const detail = {
    ...schedule,
    excludeHolidays: true,
    permissions: [{
      employeeId: 'trainee001', name: '初期新人', nameKana: null, syainKbn: 1 as const,
      canEdit: true, pinned: false, updateCount: 0, usedAsReviewer: true, hasComments: false,
    }],
    categories: [],
    details: [],
  }
  fetchMock.mockResolvedValue(new Response(JSON.stringify(detail)))
  await expect(getSchedule('S001')).resolves.toEqual(detail)
})

it('詳細APIのProgress利用状況が欠落している場合はINVALID_RESPONSEとして扱う', async () => {
  const invalid = {
    ...schedule,
    excludeHolidays: true,
    permissions: [{
      employeeId: 'trainee001', name: '初期新人', nameKana: null, syainKbn: 1,
      canEdit: false, pinned: false, updateCount: 0,
    }],
    categories: [],
    details: [],
  }
  fetchMock.mockResolvedValue(new Response(JSON.stringify(invalid)))
  await expect(getSchedule('S001')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})

it('スケジュール権限設定用の社員候補をCookie付きで取得する', async () => {
  const options = [{ syainId: 'trainee001', syainKbn: 1, name: '初期新人', nameKana: null }]
  fetchMock.mockResolvedValue(new Response(JSON.stringify(options)))
  await expect(getScheduleMemberOptions()).resolves.toEqual(options)
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/schedules/member-options',
    expect.objectContaining({ credentials: 'include' }),
  )
})

it('日付範囲の日本の休日一覧をCookie付きで呼び出す', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ dates: ['2026-10-10', '2026-10-11', '2026-10-12'] })))
  expect(await getNonWorkingDates('2026-10-05', '2026-10-16')).toEqual([
    '2026-10-10', '2026-10-11', '2026-10-12',
  ])
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/schedules/non-working-dates?startDate=2026-10-05&endDate=2026-10-16',
    expect.objectContaining({ credentials: 'include' }),
  )
})

it('更新APIへCSRFと更新カウンタを含む編集対象だけを送信する', async () => {
  const detail = { ...schedule, excludeHolidays: true, permissions: [], categories: [], details: [] }
  const request = {
    scheduleName: '変更後研修',
    scheduleUpdateCount: 3,
    permissions: [],
    deletedPermissions: [],
    categories: [],
    deletedCategories: [],
    details: [],
  }
  fetchMock.mockResolvedValue(new Response(JSON.stringify(detail)))
  expect(await updateSchedule('S001', request, { headerName: 'X-CSRF-TOKEN', token: 'csrf-value' })).toEqual(detail)
  expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/schedules/S001', expect.objectContaining({
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf-value' },
    body: JSON.stringify(request),
  }))
})

it('削除APIへCSRFとスケジュール更新カウンタを送信する', async () => {
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
  await expect(deleteSchedule('S 001', 3, { headerName: 'X-CSRF-TOKEN', token: 'csrf-value' })).resolves.toBeUndefined()
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/schedules/S%20001?scheduleUpdateCount=3',
    expect.objectContaining({
      method: 'DELETE', credentials: 'include', headers: { 'X-CSRF-TOKEN': 'csrf-value' },
    }),
  )
})

it('ピン留めAPIへCSRFと本人権限の更新カウンタだけを送信する', async () => {
  const result = { scheduleId: 'S001', pinned: false, permissionUpdateCount: 2 }
  fetchMock.mockResolvedValue(new Response(JSON.stringify(result)))
  await expect(updateSchedulePin('S 001', false, 1, {
    headerName: 'X-CSRF-TOKEN', token: 'csrf-value',
  })).resolves.toEqual(result)
  expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/schedules/S%20001/pin', expect.objectContaining({
    method: 'PUT', credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf-value' },
    body: JSON.stringify({ pinned: false, permissionUpdateCount: 1 }),
  }))
})

it('登録APIへCSRFと4テーブル分の登録対象を送信する', async () => {
  const detail = { ...schedule, excludeHolidays: true, permissions: [], categories: [], details: [] }
  const request = {
    scheduleId: 'S001', scheduleName: 'API研修', startDate: '2026-04-01', endDate: '2026-04-01',
    excludeHolidays: true,
    permissions: [{ employeeId: 'admin001', canEdit: true }, { employeeId: 'trainee001', canEdit: false }],
    categories: [{ categoryNo: 1, categoryName: 'Java研修' }],
    details: [{ scheduleDateNo: 1, scheduleDate: '2026-04-01', categoryNo: 1,
      instructorEmployeeId: 'admin001', content: '基礎', taskFlg: false }],
  }
  fetchMock.mockResolvedValue(new Response(JSON.stringify(detail), { status: 201 }))
  expect(await createSchedule(request, { headerName: 'X-CSRF-TOKEN', token: 'csrf-value' })).toEqual(detail)
  expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/schedules', expect.objectContaining({
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf-value' },
    body: JSON.stringify(request),
  }))
})

it('課題解除可否が欠落した応答は安全側に拒否する', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ ...schedule, permissions: [], categories: [],
    details: [{ scheduleDateNo: 1, taskFlg: true }] })))
  await expect(getSchedule('S001')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})
