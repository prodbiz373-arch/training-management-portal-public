import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { getHomeDashboard } from '@/lib/home-api'

beforeEach(() => vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080'))
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

it('GET /api/homeをCookie認証・no-storeで取得する', async () => {
  const dashboard = {
    businessDate: '2026-04-03',
    viewerRole: 'TRAINEE',
    taskSummary: { unsubmitted: 0, correctionRequired: 0, inReview: 0, completed: 0 },
    schedules: [],
  }
  const fetchMock = vi.fn(async () => new Response(JSON.stringify(dashboard)))
  vi.stubGlobal('fetch', fetchMock)

  expect(await getHomeDashboard()).toEqual(dashboard)
  expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/home', expect.objectContaining({
    credentials: 'include',
    cache: 'no-store',
  }))
})
