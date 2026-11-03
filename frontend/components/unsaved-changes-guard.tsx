'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { ConfirmDialog } from '@/components/message-dialog'

const HISTORY_INDEX_KEY = '__trainingPortalHistoryIndex'

type GuardRegistration = { id: symbol; dirty: boolean; busy: boolean }
type PendingNavigation =
  | { kind: 'route'; href: string }
  | { kind: 'history'; direction: -1 | 1 }

type UnsavedChangesContextValue = {
  update: (registration: GuardRegistration) => void
  remove: (id: symbol) => void
  navigateWithoutPrompt: (navigate: () => void) => void
}

const UnsavedChangesContext = createContext<UnsavedChangesContextValue>({
  update: () => undefined,
  remove: () => undefined,
  navigateWithoutPrompt: (navigate) => navigate(),
})

function historyIndex(state: unknown): number | null {
  if (!state || typeof state !== 'object') return null
  const value = (state as Record<string, unknown>)[HISTORY_INDEX_KEY]
  return typeof value === 'number' ? value : null
}

function stateWithHistoryIndex(state: unknown, index: number) {
  const source = state && typeof state === 'object' ? state as Record<string, unknown> : {}
  return { ...source, [HISTORY_INDEX_KEY]: index }
}

export function UnsavedChangesProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const guard = useRef<GuardRegistration | null>(null)
  const [pendingNavigation, setPendingNavigation] = useState<PendingNavigation | null>(null)
  const currentHistoryIndex = useRef(0)
  const restoringHistory = useRef(false)
  const bypassHistoryGuard = useRef(false)
  const bypassRouteGuard = useRef(false)

  const update = useCallback((registration: GuardRegistration) => {
    guard.current = registration
    if (!registration.dirty) setPendingNavigation(null)
  }, [])
  const remove = useCallback((id: symbol) => {
    if (guard.current?.id === id) {
      guard.current = null
      setPendingNavigation(null)
    }
  }, [])
  const navigateWithoutPrompt = useCallback((navigate: () => void) => {
    bypassRouteGuard.current = true
    setPendingNavigation(null)
    navigate()
  }, [])

  useEffect(() => {
    bypassRouteGuard.current = false
  }, [pathname])

  useEffect(() => {
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!guard.current?.dirty || bypassRouteGuard.current) return
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [])

  useEffect(() => {
    const handleClick = (event: MouseEvent) => {
      const activeGuard = guard.current
      if (!activeGuard?.dirty || bypassRouteGuard.current || event.defaultPrevented
          || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
      const target = event.target
      if (!(target instanceof Element)) return
      const anchor = target.closest('a[href]')
      if (!(anchor instanceof HTMLAnchorElement) || anchor.target === '_blank' || anchor.hasAttribute('download')) return
      const destination = new URL(anchor.href, window.location.href)
      if (destination.origin !== window.location.origin) return
      if (destination.href === window.location.href) return
      event.preventDefault()
      if (activeGuard.busy || pendingNavigation) return
      setPendingNavigation({ kind: 'route', href: `${destination.pathname}${destination.search}${destination.hash}` })
    }
    document.addEventListener('click', handleClick, true)
    return () => document.removeEventListener('click', handleClick, true)
  }, [pendingNavigation])

  useEffect(() => {
    const originalPushState = window.history.pushState.bind(window.history)
    const originalReplaceState = window.history.replaceState.bind(window.history)
    const existingIndex = historyIndex(window.history.state)
    currentHistoryIndex.current = existingIndex ?? 0
    if (existingIndex === null) {
      originalReplaceState(stateWithHistoryIndex(window.history.state, currentHistoryIndex.current), '', window.location.href)
    }

    window.history.pushState = (data: unknown, unused: string, url?: string | URL | null) => {
      currentHistoryIndex.current += 1
      originalPushState(stateWithHistoryIndex(data, currentHistoryIndex.current), unused, url)
    }
    window.history.replaceState = (data: unknown, unused: string, url?: string | URL | null) => {
      originalReplaceState(stateWithHistoryIndex(data, currentHistoryIndex.current), unused, url)
    }

    const handlePopState = (event: PopStateEvent) => {
      const nextIndex = historyIndex(event.state)
      if (restoringHistory.current) {
        restoringHistory.current = false
        if (nextIndex !== null) currentHistoryIndex.current = nextIndex
        return
      }
      if (bypassHistoryGuard.current) {
        bypassHistoryGuard.current = false
        if (nextIndex !== null) currentHistoryIndex.current = nextIndex
        return
      }
      const activeGuard = guard.current
      if (!activeGuard?.dirty) {
        if (nextIndex !== null) currentHistoryIndex.current = nextIndex
        return
      }
      // App Routerが離脱先を描画する前に履歴を戻し、確認後のpopstateだけを通す。
      event.stopImmediatePropagation()
      const direction: -1 | 1 = nextIndex !== null && nextIndex > currentHistoryIndex.current ? 1 : -1
      restoringHistory.current = true
      window.history.go(-direction)
      if (!activeGuard.busy) setPendingNavigation({ kind: 'history', direction })
    }
    window.addEventListener('popstate', handlePopState)
    return () => {
      window.removeEventListener('popstate', handlePopState)
      window.history.pushState = originalPushState
      window.history.replaceState = originalReplaceState
    }
  }, [])

  const discardAndNavigate = useCallback(() => {
    const pending = pendingNavigation
    if (!pending) return
    setPendingNavigation(null)
    if (pending.kind === 'route') {
      bypassRouteGuard.current = true
      router.push(pending.href)
    } else {
      bypassHistoryGuard.current = true
      window.history.go(pending.direction)
    }
  }, [pendingNavigation, router])

  const value = useMemo(() => ({ update, remove, navigateWithoutPrompt }), [navigateWithoutPrompt, remove, update])
  return <UnsavedChangesContext.Provider value={value}>
    {children}
    {pendingNavigation && <ConfirmDialog
      title="編集内容が保存されていません"
      message={"スケジュールを編集中です。保存せずに他の画面へ移動しますか？\n移動すると、入力した内容は失われます。"}
      cancelLabel="編集を続ける"
      confirmLabel="保存せずに移動"
      destructive
      onCancel={() => setPendingNavigation(null)}
      onConfirm={discardAndNavigate}
    />}
  </UnsavedChangesContext.Provider>
}

export function useUnsavedChangesGuard({ dirty, busy = false }: { dirty: boolean; busy?: boolean }) {
  const context = useContext(UnsavedChangesContext)
  const id = useRef(Symbol('unsaved-changes-guard'))
  useEffect(() => {
    context.update({ id: id.current, dirty, busy })
  }, [busy, context, dirty])
  useEffect(() => () => context.remove(id.current), [context])
  return { navigateWithoutPrompt: context.navigateWithoutPrompt }
}
