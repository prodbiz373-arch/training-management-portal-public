'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from './auth-provider'

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, loading, error, refreshUser } = useAuth()
  const router = useRouter()
  useEffect(() => {
    if (!loading && !user && !error) router.replace('/')
  }, [loading, user, error, router])

  if (loading) return <div role="status" className="flex min-h-svh items-center justify-center bg-background text-muted-foreground">ログイン状態を確認しています…</div>
  if (error) return <div className="flex min-h-svh flex-col items-center justify-center gap-4 bg-background"><p role="alert">{error}</p><button onClick={() => void refreshUser()} className="rounded-lg bg-primary px-4 py-2 text-primary-foreground">再試行</button></div>
  if (!user) return <div role="status" className="min-h-svh bg-background">ログイン画面へ移動しています…</div>
  return children
}
