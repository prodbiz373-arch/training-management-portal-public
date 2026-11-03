import { requestApi, type CsrfResponse } from './auth-api'
export type { ApiErrorResponse, FieldError } from './auth-api'

export type Member = {
  syainId: string
  syainKbn: 1 | 2
  name: string
  nameKana: string | null
  updateCount: number
}
export type MemberCreateRequest = Omit<Member, 'updateCount'> & { password: string }
export type MemberUpdateRequest = Omit<Member, 'syainId'> & { password?: string | null }

// DTOの5項目だけを画面へ渡し、余分なプロパティを保持しない。
function memberDto(member: Member): Member {
  return { syainId: member.syainId, syainKbn: member.syainKbn, name: member.name,
    nameKana: member.nameKana, updateCount: member.updateCount }
}
export async function getMembers(): Promise<Member[]> {
  return (await requestApi<Member[]>('/api/members')).map(memberDto)
}
export async function getMember(syainId: string): Promise<Member> {
  return memberDto(await requestApi<Member>(`/api/members/${encodeURIComponent(syainId)}`))
}
export async function createMember(request: MemberCreateRequest, csrf: CsrfResponse): Promise<Member> {
  return memberDto(await requestApi<Member>('/api/members', { method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token }, body: JSON.stringify(request) }))
}
export async function updateMember(syainId: string, request: MemberUpdateRequest, csrf: CsrfResponse): Promise<Member> {
  return memberDto(await requestApi<Member>(`/api/members/${encodeURIComponent(syainId)}`, { method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token }, body: JSON.stringify(request) }))
}
export async function deleteMember(syainId: string, csrf: CsrfResponse): Promise<void> {
  await requestApi<void>(`/api/members/${encodeURIComponent(syainId)}`, {
    method: 'DELETE', headers: { 'X-CSRF-TOKEN': csrf.token },
  })
}
