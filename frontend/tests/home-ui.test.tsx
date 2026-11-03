import { readFileSync } from 'node:fs'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import HomePage from '@/app/(app)/home/page'
import { AuthProvider } from '@/components/auth/auth-provider'
import { RequireAuth } from '@/components/auth/require-auth'
import type { HomeDashboard } from '@/lib/home-api'

const navigation = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }))
vi.mock('next/navigation', () => ({ useRouter: () => navigation, usePathname: () => '/home' }))

const trainee = { syainId: 'trainee001', syainKbn: 1, name: 'テスト 新人', nameKana: null, authorities: ['ROLE_TRAINEE'] }
const instructor = { syainId: 'admin001', syainKbn: 2, name: 'テスト 講師', nameKana: null, authorities: ['ROLE_INSTRUCTOR'] }
const todayRow = {
  scheduleDateNo: 2,
  scheduleDate: '2026-04-03',
  categoryNo: 1,
  categoryName: 'Java研修',
  content: '今日の実データ課題',
  instructorName: '初期講師',
  taskFlg: true,
  taskStatusCode: 1,
  taskStatusName: '未提出' as const,
  completedCount: null,
  targetTraineeCount: null,
}
const dashboard: HomeDashboard = {
  businessDate: '2026-04-03',
  viewerRole: 'TRAINEE',
  taskSummary: { unsubmitted: 2, correctionRequired: 1, inReview: 3, completed: 4 },
  schedules: [
    {
      scheduleId: 'DB001', scheduleName: 'DB新人研修', startDate: '2026-04-01', endDate: '2026-04-30', canEdit: false, pinned: true,
      details: [todayRow, { ...todayRow, scheduleDateNo: 1, scheduleDate: '2026-04-02', content: '前日の内容', taskFlg: false, taskStatusCode: null, taskStatusName: null }],
    },
    {
      scheduleId: 'DB002', scheduleName: 'DB別研修', startDate: '2026-04-01', endDate: '2026-05-31', canEdit: false, pinned: true,
      details: [{ ...todayRow, scheduleDateNo: 1, categoryName: 'Web研修', content: 'API連携演習' }],
    },
  ],
}

const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status })
let authUser: typeof trainee | typeof instructor
let homeResponse: () => Promise<Response>
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080')
  navigation.replace.mockReset()
  navigation.push.mockReset()
  authUser = trainee
  homeResponse = async () => response(dashboard)
  fetchMock = vi.fn(async (url: string) => {
    if (url.endsWith('/api/auth/me')) return response(authUser)
    if (url.endsWith('/api/home')) return homeResponse()
    throw new Error(`unexpected request: ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

function open() {
  return render(<AuthProvider><RequireAuth><HomePage /></RequireAuth></AuthProvider>)
}

function statCard(label: string) {
  const labelNode = screen.getAllByText(label).find((node) => node.className === 'text-sm text-muted-foreground')
  if (!labelNode) throw new Error(`${label} card not found`)
  return labelNode.closest('.rounded-xl') as HTMLElement
}

it('ホーム自身はmock-dataを参照せず、APIの新人向け課題・スケジュールを表示する', async () => {
  const source = readFileSync('app/(app)/home/page.tsx', 'utf8')
  expect(source).not.toContain('@/lib/mock-data')
  open()

  expect((await screen.findAllByText('DB新人研修')).length).toBeGreaterThan(0)
  expect(screen.getAllByText('今日の実データ課題').length).toBeGreaterThan(0)
  expect(within(statCard('未提出')).getByText('2')).toBeTruthy()
  expect(within(statCard('修正待ち')).getByText('1')).toBeTruthy()
  expect(within(statCard('レビュー中')).getByText('3')).toBeTruthy()
  expect(within(statCard('完了')).getByText('4')).toBeTruthy()
  expect(screen.getByText('前日の内容')).toBeTruthy()
  expect(screen.getByText('課題なし')).toBeTruthy()
  expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/api/home'))).toBe(true)
})

it('HomeからProgressへ表示名ではなく実DB複合キーで遷移する', async () => {
  open()
  const link = (await screen.findAllByRole('link', { name: /課題状況を見る/ }))[0] as HTMLAnchorElement
  expect(link.href).toContain('/progress?')
  expect(link.href).toContain('scheduleId=DB001')
  expect(link.href).toContain('categoryNo=1')
  expect(link.href).toContain('scheduleDateNo=2')
  expect(link.href).not.toContain('schedule=DB')
})

it('未提出と修正待ちの既存アイコン・色を維持する', async () => {
  open()
  await screen.findAllByText('DB新人研修')
  expect(statCard('未提出').querySelector('svg')?.getAttribute('class')).toContain('text-destructive')
  expect(statCard('修正待ち').querySelector('svg')?.getAttribute('class')).toContain('text-warning-foreground')
})

it('講師はAPI集計の完了数と対象新人総数を表示する', async () => {
  authUser = instructor
  homeResponse = async () => response({
    ...dashboard,
    viewerRole: 'INSTRUCTOR',
    schedules: dashboard.schedules.slice(0, 1).map((schedule) => ({
      ...schedule,
      details: schedule.details.map((row) => row.taskFlg
        ? { ...row, taskStatusCode: null, taskStatusName: null, completedCount: 1, targetTraineeCount: 2 }
        : row),
    })),
  })
  open()
  expect(await screen.findByText('[完了] 1/2')).toBeTruthy()
  expect(screen.getByRole('link', { name: /一覧へ/ })).toBeTruthy()
})

it('ピン2件を中央で分割し、API順のまま今日の予定へ表示する', async () => {
  open()
  const labels = await screen.findAllByText('今日の予定')
  expect(labels).toHaveLength(2)
  expect(labels[0].closest('.grid')?.getAttribute('class')).toContain('md:grid-cols-2')
  expect(screen.getAllByText('DB新人研修')[0]).toBeTruthy()
  expect(screen.getAllByText('DB別研修')[0]).toBeTruthy()
})

it('ピン0件と1件を既存表示で扱う', async () => {
  homeResponse = async () => response({ ...dashboard, schedules: dashboard.schedules.map((schedule) => ({ ...schedule, pinned: false })) })
  const view = open()
  expect(await screen.findByText('スケジュール一覧から、今日の予定に表示するスケジュールをピン留めしてください。')).toBeTruthy()
  view.unmount()

  homeResponse = async () => response({ ...dashboard, schedules: [{ ...dashboard.schedules[0], pinned: true }] })
  open()
  expect(await screen.findAllByText('今日の予定')).toHaveLength(1)
})

it('課題・スケジュール0件をエラーにせず表示する', async () => {
  homeResponse = async () => response({
    ...dashboard,
    taskSummary: { unsubmitted: 0, correctionRequired: 0, inReview: 0, completed: 0 },
    schedules: [],
  })
  open()
  expect(await screen.findByText('表示するスケジュールがありません')).toBeTruthy()
  expect(within(statCard('未提出')).getByText('0')).toBeTruthy()
})

it('取得中は仮データを表示せずローディングを表示する', async () => {
  let resolve!: (value: Response) => void
  homeResponse = () => new Promise<Response>((done) => { resolve = done })
  open()
  expect(await screen.findByText('ホームを読み込んでいます…')).toBeTruthy()
  expect(screen.queryByText('DB新人研修')).toBeNull()
  await act(async () => resolve(response(dashboard)))
  expect((await screen.findAllByText('DB新人研修')).length).toBeGreaterThan(0)
})

it('APIエラーは画面上部に表示し、仮データへフォールバックせず再試行できる', async () => {
  homeResponse = async () => { throw new Error('private failure') }
  open()
  expect((await screen.findByRole('alert')).textContent).toContain('サーバーに接続できませんでした')
  expect(screen.queryByText('DB新人研修')).toBeNull()
  homeResponse = async () => response(dashboard)
  fireEvent.click(screen.getByText('ホームを再取得'))
  expect((await screen.findAllByText('DB新人研修')).length).toBeGreaterThan(0)
})

it('ホームAPIの401は既存AuthProviderでログイン画面へ戻る', async () => {
  homeResponse = async () => response({ code: 'AUTHENTICATION_REQUIRED' }, 401)
  open()
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/'))
  expect(screen.queryByText('DB新人研修')).toBeNull()
})

it('NULL内容の通常行を表示しても課題リンクを作らず既存集計を維持する', async () => {
  homeResponse = async () => response({
    ...dashboard,
    schedules: [{
      ...dashboard.schedules[0],
      details: [{ ...todayRow, content: null, taskFlg: false, taskStatusCode: null, taskStatusName: null }],
    }],
  })
  open()
  expect((await screen.findAllByText('DB新人研修')).length).toBeGreaterThan(0)
  expect(screen.getAllByText('課題なし').length).toBeGreaterThan(0)
  expect(screen.queryByRole('link', { name: /課題状況を見る/ })).toBeNull()
  expect(screen.queryByRole('alert')).toBeNull()
  expect(within(statCard('未提出')).getByText('2')).toBeTruthy()
})
