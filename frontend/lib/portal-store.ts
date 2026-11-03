'use client'

import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '@/components/auth/auth-provider'
import { members as seedMembers, type Member } from '@/lib/mock-data'

export type PortalMember = Member & { password?: string }
export type PortalSessionUser = Pick<Member, 'employeeId' | 'name' | 'furigana' | 'role'>

const MEMBERS_KEY = 'training-portal-members:v1'
const MEMBERS_EVENT = 'training-portal-members-updated'

function normalizeMembers(rows: PortalMember[]): PortalMember[] {
  return rows.map((row, index) => ({ no: index + 1, employeeId: row.employeeId, name: row.name, furigana: row.furigana, role: row.role }))
}

function seedRows(): PortalMember[] {
  // 既存モックメンバーはDB未接続時の画面確認用のため、パスワード未設定扱い。
  return normalizeMembers(seedMembers)
}

export function loadMembers(): PortalMember[] {
  if (typeof window === 'undefined') return seedRows()
  const raw = window.localStorage.getItem(MEMBERS_KEY)
  if (!raw) {
    const initial = seedRows()
    window.localStorage.setItem(MEMBERS_KEY, JSON.stringify(initial))
    return initial
  }
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) throw new Error('invalid member store')
    const rows = normalizeMembers(parsed as PortalMember[])
    // 旧モックで保存されたパスワードも認証切り替え時に除去する。
    if (parsed.some((row) => 'password' in row)) window.localStorage.setItem(MEMBERS_KEY, JSON.stringify(rows))
    return rows
  } catch {
    const initial = seedRows()
    window.localStorage.setItem(MEMBERS_KEY, JSON.stringify(initial))
    return initial
  }
}

export function saveMembers(rows: PortalMember[]) {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(MEMBERS_KEY, JSON.stringify(normalizeMembers(rows)))
  window.dispatchEvent(new Event(MEMBERS_EVENT))
}

export function usePortalMembers() {
  const [rows, setRows] = useState<PortalMember[]>([])
  useEffect(() => {
    const refresh = () => setRows(loadMembers())
    refresh()
    window.addEventListener(MEMBERS_EVENT, refresh)
    window.addEventListener('storage', refresh)
    return () => {
      window.removeEventListener(MEMBERS_EVENT, refresh)
      window.removeEventListener('storage', refresh)
    }
  }, [])
  return rows
}

// 業務モック画面の既存型への変換だけを行う。認証情報の保存・照合は行わない。
export function usePortalSession() {
  const { user: authUser, loading } = useAuth()
  const user = useMemo<PortalSessionUser | null>(() => authUser ? {
    employeeId: authUser.syainId,
    name: authUser.name,
    furigana: authUser.nameKana ?? '',
    role: authUser.authorities.includes('ROLE_INSTRUCTOR') ? '講師' : '新人',
  } : null, [authUser])
  return { user, ready: !loading }
}
