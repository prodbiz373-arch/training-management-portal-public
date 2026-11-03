'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { usePathname } from 'next/navigation'
import * as api from '@/lib/auth-api'

type AuthContextValue = {
  user: api.AuthUser | null
  loading: boolean
  csrf: api.CsrfResponse | null
  error: string
  refreshUser: () => Promise<void>
  ensureCsrf: () => Promise<api.CsrfResponse>
  login: (syainId: string, password: string) => Promise<void>
  logout: () => Promise<void>
}
const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<api.AuthUser | null>(null)
  const [csrf, setCsrf] = useState<api.CsrfResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const pending = useRef<Promise<void> | null>(null)
  const initialCheckCompleted = useRef(false)
  const version = useRef(0)
  const authMutation = useRef(false)
  const pathname = usePathname()

  const clear = useCallback(() => {
    version.current += 1
    setUser(null)
    setCsrf(null)
    setError('')
    setLoading(false)
  }, [])

  const refreshUser = useCallback((): Promise<void> => {
    if (authMutation.current) return Promise.resolve()
    if (pending.current) return pending.current
    const current = version.current
    // 初回だけ保護画面への表示を待機する。既に認証済みの再確認（フォーカス復帰等）で
    // children をアンマウントすると、進行中の業務APIのエラー表示が失われるためである。
    if (!initialCheckCompleted.current) setLoading(true)
    const task = (async () => {
      try {
        const next = await api.getCurrentUser()
        if (current !== version.current) return
        setUser(next)
        if (!next) setCsrf(null)
        setError('')
      } catch (failure) {
        if (current !== version.current) return
        setUser(null)
        setError(api.errorMessage(failure))
      } finally {
        initialCheckCompleted.current = true
        if (current === version.current) setLoading(false)
        pending.current = null
      }
    })()
    pending.current = task
    return task
  }, [])

  useEffect(() => api.onUnauthorized(clear), [clear])
  useEffect(() => {
    try { window.localStorage.removeItem('training-portal-session-user:v1') } catch { /* 保存領域が使えなくてもAPI認証は継続する */ }
  }, [])
  useEffect(() => { void refreshUser() }, [pathname, refreshUser])
  useEffect(() => {
    // 他タブのログアウト・復帰時のセッション失効もmeを正として確認する。
    const check = () => { if (document.visibilityState === 'visible') void refreshUser() }
    window.addEventListener('focus', check)
    document.addEventListener('visibilitychange', check)
    return () => {
      window.removeEventListener('focus', check)
      document.removeEventListener('visibilitychange', check)
    }
  }, [refreshUser])

  const login = useCallback(async (syainId: string, password: string) => {
    const current = ++version.current
    authMutation.current = true
    try {
      const result = await api.login(syainId, password)
      if (current !== version.current) return
      setUser(result.user)
      setCsrf(result.csrf)
      setError('')
      setLoading(false)
    } finally {
      authMutation.current = false
    }
  }, [])

  const ensureCsrf = useCallback(async () => {
    if (csrf) return csrf
    const current = version.current
    const token = await api.getCsrf()
    if (current !== version.current) throw new api.ApiClientError(401, 'AUTHENTICATION_REQUIRED', 'ログインが必要です')
    setCsrf(token)
    return token
  }, [csrf])

  const logout = useCallback(async () => {
    version.current += 1
    authMutation.current = true
    try {
      await api.logout(csrf)
      clear()
    } catch (failure) {
      setCsrf(null) // CSRF不正時も次の明示操作で取り直す。自動再送はしない。
      if (failure instanceof api.ApiClientError && failure.status === 401) { clear(); return }
      throw failure
    } finally {
      authMutation.current = false
    }
  }, [csrf, clear])

  const value = useMemo(() => ({ user, loading, csrf, error, refreshUser, ensureCsrf, login, logout }),
    [user, loading, csrf, error, refreshUser, ensureCsrf, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProviderが必要です')
  return context
}
