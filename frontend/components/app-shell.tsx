'use client'

import { useRef, useState } from 'react'
import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { LayoutDashboard, CalendarRange, ClipboardList, Users, GraduationCap, Menu, X, LogOut } from 'lucide-react'
import { cn } from '@/lib/utils'
import { usePortalSession } from '@/lib/portal-store'
import { useAuth } from '@/components/auth/auth-provider'
import { errorMessage } from '@/lib/auth-api'

const nav = [
  { href: '/home', label: 'ホーム', icon: LayoutDashboard, roles: ['講師', '新人'] as const },
  { href: '/schedule', label: 'スケジュール', icon: CalendarRange, roles: ['講師', '新人'] as const },
  { href: '/progress', label: '進捗・課題管理', icon: ClipboardList, roles: ['講師', '新人'] as const },
  { href: '/members', label: 'メンバ登録', icon: Users, roles: ['講師'] as const },
]

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const router = useRouter()
  const [open, setOpen] = useState(false)
  const { user: currentUser, ready } = usePortalSession()
  const { logout: apiLogout } = useAuth()
  const [logoutError, setLogoutError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  const logoutPending = useRef(false)
  const visibleNav = currentUser ? nav.filter((item) => (item.roles as readonly string[]).includes(currentUser.role)) : []
  const instructorOnly = pathname === '/members' || pathname.startsWith('/members/')
  const accessDenied = Boolean(currentUser && currentUser.role !== '講師' && instructorOnly)

  async function logout() {
    if (logoutPending.current || !window.confirm('ログアウトしますか？')) return
    logoutPending.current = true
    setLoggingOut(true)
    setLogoutError('')
    try {
      await apiLogout()
      router.replace('/')
    } catch (failure) {
      setLogoutError(errorMessage(failure))
    } finally {
      logoutPending.current = false
      setLoggingOut(false)
    }
  }

  if (!ready || !currentUser) return <div className="min-h-svh bg-background" />

  return (
    <div className="min-h-svh bg-background">
      <header className="sticky top-0 z-40 border-b border-sidebar-border bg-sidebar text-sidebar-foreground">
        <div className="mx-auto flex h-16 max-w-[1600px] items-center gap-4 px-4 lg:px-8">
          <Link href="/home" className="flex shrink-0 items-center gap-2.5">
            <div className="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground"><GraduationCap className="size-5" /></div>
            <div className="hidden leading-tight sm:block"><p className="text-sm font-bold text-sidebar-accent-foreground">研修ポータル</p><p className="text-[11px] text-sidebar-foreground/70">Training Portal</p></div>
          </Link>
          <nav className="hidden flex-1 items-center gap-1 lg:flex">
            {visibleNav.map((item) => { const active = pathname === item.href || pathname.startsWith(item.href + '/'); const Icon = item.icon; return (
              <Link key={item.href} href={item.href} className={cn('inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors', active ? 'bg-primary text-primary-foreground' : 'text-sidebar-foreground/80 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground')}><Icon className="size-[17px]" />{item.label}</Link>
            )})}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <div className="hidden text-right sm:block"><p className="text-sm font-medium leading-tight text-sidebar-accent-foreground">{currentUser.name}</p><p className="text-xs text-sidebar-foreground/70">{currentUser.role} ・ ID {currentUser.employeeId}</p></div>
            <button disabled={loggingOut} onClick={logout} className="rounded-lg p-2 text-sidebar-foreground/75 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground" aria-label="ログアウト"><LogOut className="size-[18px]" /></button>
            <button onClick={() => setOpen((v) => !v)} className="rounded-lg p-2 text-sidebar-foreground/75 hover:bg-sidebar-accent lg:hidden" aria-label="メニューを開く">{open ? <X className="size-5" /> : <Menu className="size-5" />}</button>
          </div>
        </div>
        {open && <nav className="border-t border-sidebar-border bg-sidebar px-4 py-3 lg:hidden"><div className="mx-auto grid max-w-[1600px] gap-1 sm:grid-cols-2">{visibleNav.map((item) => { const active = pathname === item.href || pathname.startsWith(item.href + '/'); const Icon = item.icon; return <Link key={item.href} href={item.href} onClick={() => setOpen(false)} className={cn('flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium', active ? 'bg-primary text-primary-foreground' : 'text-sidebar-foreground/80 hover:bg-sidebar-accent')}><Icon className="size-[17px]" />{item.label}</Link> })}</div></nav>}
      </header>
      <main className="mx-auto max-w-[1600px] p-4 lg:p-8">{logoutError && <p role="alert" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{logoutError}</p>}{accessDenied ? <div className="mx-auto mt-10 max-w-xl rounded-xl border border-destructive/20 bg-card p-6 text-center"><p className="text-lg font-bold text-[#054A99]">この画面を表示する権限がありません</p><p className="mt-2 text-sm text-muted-foreground">この操作を行う権限がありません。</p><Link href="/home" className="mt-4 inline-flex rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground">ホームへ戻る</Link></div> : children}</main>
    </div>
  )
}

export function PageHeader({ id: _id, title, description, actions }: { id: string; title: string; description?: string; actions?: React.ReactNode }) {
  return <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div><h1 className="text-2xl font-bold tracking-tight text-balance text-[#054A99]">{title}</h1>{description && <p className="mt-1.5 max-w-2xl text-sm leading-relaxed text-pretty text-[#054A99]">{description}</p>}</div>{actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}</div>
}
