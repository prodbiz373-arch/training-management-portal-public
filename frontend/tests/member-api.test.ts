import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { getMembers, getMember, createMember, updateMember, deleteMember } from '@/lib/member-api'
const member = { syainId: '001', syainKbn: 1 as const, name: 'テスト', nameKana: null, updateCount: 3 }
const csrf = { headerName: 'X-CSRF-TOKEN' as const, token: 'synthetic-token' }
let fetchMock: ReturnType<typeof vi.fn>
beforeEach(() => { vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://localhost:8080'); fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock) })
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })
it('一覧と詳細は5項目のみを返しIDをエンコードする', async () => {
  fetchMock.mockResolvedValueOnce(new Response(JSON.stringify([{ ...member, password: 'synthetic', createUserCd: 'internal' }])))
    .mockResolvedValueOnce(new Response(JSON.stringify(member)))
  expect(await getMembers()).toEqual([member]); expect(await getMember('a/b ?')).toEqual(member)
  expect(fetchMock.mock.calls[1][0]).toBe('http://localhost:8080/api/members/a%2Fb%20%3F')
  fetchMock.mock.calls.forEach(([, init]) => expect(init.credentials).toBe('include'))
})
it('全更新系でCookieとCSRFを送り削除204をJSON解析しない', async () => {
  const empty = new Response(null, { status: 204 }); const json = vi.spyOn(empty, 'json')
  fetchMock.mockResolvedValueOnce(new Response(JSON.stringify(member))).mockResolvedValueOnce(new Response(JSON.stringify(member))).mockResolvedValueOnce(empty)
  await createMember({ syainId: member.syainId, syainKbn: 1, name: member.name, nameKana: null, password: 'synthetic' }, csrf)
  await updateMember('a/b', { syainKbn: 1, name: member.name, nameKana: null, password: '', updateCount: 3 }, csrf)
  await deleteMember('a/b', csrf)
  expect(fetchMock.mock.calls.map(([, init]) => init.method)).toEqual(['POST', 'PUT', 'DELETE'])
  fetchMock.mock.calls.forEach(([, init]) => {
    expect(init.credentials).toBe('include'); expect(init.headers['X-CSRF-TOKEN']).toBe(csrf.token)
    expect(init.headers['X-Operation-Syain-Id']).toBeUndefined()
  })
  expect(fetchMock.mock.calls[2][0]).toContain('a%2Fb'); expect(json).not.toHaveBeenCalled()
})
it.each([[404, 'MEMBER_NOT_FOUND'], [500, 'INTERNAL_ERROR'], [403, 'ACCESS_DENIED']])('%sの内部メッセージを公開しない', async (status, code) => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ code, message: 'private SQL stacktrace' }), { status }))
  await expect(getMembers()).rejects.toMatchObject({ status, code })
  await expect(getMembers()).rejects.not.toHaveProperty('message', 'private SQL stacktrace')
})
