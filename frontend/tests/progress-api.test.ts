import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import {
  addProgressComment,
  deleteProgressComment,
  getProgress,
  updateProgressSubmission,
  type ProgressItem,
} from '@/lib/progress-api'

const response = (body: unknown, status = 200) => new Response(status === 204 ? null : JSON.stringify(body), { status })
const csrf = { headerName: 'X-CSRF-TOKEN' as const, token: 'csrf-token' }
const item = {
  scheduleId: 'SC 01', scheduleDateNo: 3, traineeEmployeeId: 'tr/01', submissionUpdateCount: 2,
} as ProgressItem
const progressData = {
  businessDate: '2026-04-03', viewerRole: 'TRAINEE', schedules: [], statuses: [], items: [],
}

beforeEach(() => vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080'))
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

it('Progress検索条件をGETクエリへ変換する', async () => {
  const fetchMock = vi.fn(async (_url: string, _options: RequestInit = {}) => response(progressData))
  vi.stubGlobal('fetch', fetchMock)
  await getProgress({ scheduleId: 'SC 01', categoryNo: 2, from: '2026-04-01', statusCode: 3 })
  const url = String(fetchMock.mock.calls[0][0])
  expect(url).toContain('/api/progress?')
  expect(url).toContain('scheduleId=SC+01')
  expect(url).toContain('categoryNo=2')
  expect(url).toContain('statusCode=3')
})

it('スケジュール別reviewersが欠落した旧レスポンスを正常な空配列として扱わない', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => response({
    ...progressData,
    reviewers: [{ employeeId: 'admin001', name: '旧形式講師' }],
    schedules: [{
      scheduleId: 'DB001', scheduleName: 'DB新人研修', canEdit: false, categories: [],
    }],
  })))
  await expect(getProgress()).rejects.toMatchObject({
    status: 0,
    code: 'INVALID_RESPONSE',
    message: 'サーバーからの応答を確認できませんでした',
  })
})

it('statusesの空配列と必須項目欠落を区別する', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => response(progressData)))
  await expect(getProgress()).resolves.toMatchObject({ statuses: [] })

  const { statuses: _statuses, ...withoutStatuses } = progressData
  vi.stubGlobal('fetch', vi.fn(async () => response(withoutStatuses)))
  await expect(getProgress()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})

it('ステータス名称はフロント固定値ではなくAPIのDBマスタ値を受け入れる', async () => {
  const data = {
    ...progressData,
    statuses: [{ statusCode: 1, statusName: 'DBマスタ名称' }],
  }
  vi.stubGlobal('fetch', vi.fn(async () => response(data)))
  await expect(getProgress()).resolves.toMatchObject({
    statuses: [{ statusCode: 1, statusName: 'DBマスタ名称' }],
  })
})

it('提出状況更新は複合キー・updateCount・CSRFを送る', async () => {
  const fetchMock = vi.fn(async (_url: string, _options: RequestInit = {}) => response({ updateCount: 3 }))
  vi.stubGlobal('fetch', fetchMock)
  await updateProgressSubmission(item, 'admin001', 6, csrf)
  const [url, options] = fetchMock.mock.calls[0]
  expect(url).toContain('/api/progress/SC%2001/3/trainees/tr%2F01')
  expect(options?.method).toBe('PUT')
  expect((options?.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('csrf-token')
  expect(JSON.parse(String(options?.body))).toEqual({ reviewerEmployeeId: 'admin001', statusCode: 6, updateCount: 2 })
})

it('コメント追加と削除は実APIを使用する', async () => {
  const fetchMock = vi.fn(async (_url: string, options: RequestInit) =>
    options.method === 'DELETE' ? response(null, 204) : response({ commentNo: 4 }))
  vi.stubGlobal('fetch', fetchMock)
  const comment = { commentNo: 4, updateCount: 1 } as never
  await addProgressComment(item, '実コメント', csrf)
  await deleteProgressComment(item, comment, csrf)
  expect(fetchMock.mock.calls[0][0]).toContain('/comments')
  expect(JSON.parse(String(fetchMock.mock.calls[0][1].body))).toEqual({ content: '実コメント' })
  expect(fetchMock.mock.calls[1][0]).toContain('/comments/4?updateCount=1')
  expect(fetchMock.mock.calls[1][1].method).toBe('DELETE')
})

it.each([
  [400, 'INVALID_INPUT', '入力内容を確認してください'],
  [403, 'ACCESS_DENIED', 'この操作を行う権限がありません'],
  [404, 'PROGRESS_NOT_FOUND', '課題が存在しません。検索条件を確認してください'],
  [409, 'PROGRESS_UPDATE_CONFLICT', '他のユーザーによって更新されています。最新の情報を再読み込みしてください。'],
  [500, 'INTERNAL_ERROR', '処理に失敗しました。時間をおいて再度お試しください'],
])('%iエラーを既存の安全な表示文言へ変換する', async (status, code, message) => {
  vi.stubGlobal('fetch', vi.fn(async () => response({ code, message: 'internal detail', errors: [] }, status)))
  await expect(getProgress()).rejects.toMatchObject({ status, code, message })
})
