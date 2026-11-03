'use client'

import { useAuth } from './auth-provider'
import { RequireAuth } from './require-auth'

export function InstructorOnly({ children }: { children: React.ReactNode }) {
  const { user } = useAuth()
  return <RequireAuth>{user?.authorities.includes('ROLE_INSTRUCTOR') ? children :
    <p role="alert" className="rounded-xl border border-destructive/20 bg-card p-6 text-center">この画面を表示する権限がありません</p>
  }</RequireAuth>
}
