'use client'

import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useAuth } from '@/components/auth/auth-provider'
import { errorMessage } from '@/lib/auth-api'
import { useRouter } from 'next/navigation'
import {
  GraduationCap,
  Eye,
  EyeOff,
  ArrowRight,
  CalendarCheck,
  MessagesSquare,
  LineChart,
} from 'lucide-react'

const highlights = [
  { icon: CalendarCheck, title: 'スケジュールを一元管理', desc: '研修全体の予定と担当講師をひと目で把握。' },
  { icon: LineChart, title: '課題の進捗を可視化', desc: '提出・レビュー状況をリアルタイムに共有。' },
  { icon: MessagesSquare, title: 'その場でフィードバック', desc: 'コメントで講師と新人がスムーズに連携。' },
]

export default function LoginPage() {
  const router = useRouter()
  const [showPw, setShowPw] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const inFlight = useRef(false)
  const { user, loading, error: authError, login } = useAuth()

  useEffect(() => {
    if (!loading && user) router.replace('/home')
  }, [loading, user, router])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (inFlight.current || loading || user) return
    inFlight.current = true
    setSubmitting(true)
    setError('')
    const form = event.currentTarget
    const data = new FormData(form)
    const passwordInput = form.elements.namedItem('password') as HTMLInputElement
    try {
      await login(String(data.get('employeeId') ?? ''), String(data.get('password') ?? ''))
      passwordInput.value = ''
      router.replace('/home')
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      passwordInput.value = ''
      data.delete('password')
      setShowPw(false)
      inFlight.current = false
      setSubmitting(false)
    }
  }

  return (
    <div className="grid min-h-svh lg:grid-cols-2">
      {/* Brand panel */}
      <div className="relative hidden flex-col justify-between overflow-hidden bg-sidebar p-12 text-sidebar-foreground lg:flex">
        <div
          className="login-brand-background pointer-events-none absolute inset-0 opacity-40"
          aria-hidden
        />
        <div className="relative flex items-center gap-3">
          <div className="flex size-11 items-center justify-center rounded-xl bg-sidebar-primary text-sidebar-primary-foreground">
            <GraduationCap className="size-6" />
          </div>
          <div>
            <p className="text-lg font-bold text-sidebar-accent-foreground">研修ポータル</p>
            <p className="text-xs text-sidebar-foreground/70">新人研修スケジュール・課題管理</p>
          </div>
        </div>

        <div className="relative max-w-md">
          <h2 className="text-3xl font-bold leading-snug text-balance text-sidebar-accent-foreground">
            研修の全体像を、
            <br />
            ひとつの画面で。
          </h2>
          <p className="mt-4 text-sm leading-relaxed text-sidebar-foreground/75 text-pretty">
            チャットや複数のツールを行き来せず、スケジュールと課題の進捗をまとめて確認。講師も新人も、迷わず今日やるべきことに集中できます。
          </p>

          <ul className="mt-8 space-y-3">
            {highlights.map((h) => {
              const Icon = h.icon
              return (
                <li key={h.title} className="flex items-start gap-3">
                  <div className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg bg-sidebar-accent text-sidebar-primary">
                    <Icon className="size-[18px]" />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-sidebar-accent-foreground">{h.title}</p>
                    <p className="text-xs text-sidebar-foreground/65">{h.desc}</p>
                  </div>
                </li>
              )
            })}
          </ul>
        </div>

        <p className="relative text-xs text-sidebar-foreground/50">
          © 2026 研修ポータル ・ 社内利用限定
        </p>
      </div>

      {/* Form panel */}
      <div className="flex items-center justify-center bg-background p-6 sm:p-12">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <div className="flex size-10 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <GraduationCap className="size-5" />
            </div>
            <p className="text-base font-bold">研修ポータル</p>
          </div>

          <div className="mb-8">
            <h1 className="text-2xl font-bold tracking-tight text-[#054A99]">ログイン</h1>
            <p className="mt-1.5 text-sm text-[#054A99]">
              社員IDとパスワードを入力してください。
            </p>
          </div>

          {(error || authError) && <div role="alert" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">{error || authError}</div>}
          <form
            className="space-y-5"
            onSubmit={submit}
          >
            <div className="space-y-1.5">
              <label htmlFor="employeeId" className="text-sm font-medium">
                社員ID <span className="text-destructive">＊</span>
              </label>
              <input
                id="employeeId"
                name="employeeId"
                required
                maxLength={10}
                autoComplete="username"
                placeholder="00000"
                className="h-11 w-full rounded-lg border border-border bg-card px-3.5 font-mono text-sm outline-none placeholder:font-sans placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/20"
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="password" className="text-sm font-medium">
                パスワード <span className="text-destructive">＊</span>
              </label>
              <div className="relative">
                <input
                  id="password"
                  name="password"
                  required
                  type={showPw ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="••••••••"
                  className="h-11 w-full rounded-lg border border-border bg-card px-3.5 pr-11 text-sm outline-none placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/20"
                />
                <button
                  type="button"
                  onClick={() => setShowPw((v) => !v)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
                  aria-label={showPw ? 'パスワードを隠す' : 'パスワードを表示'}
                >
                  {showPw ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading || submitting || Boolean(user)}
              aria-busy={loading || submitting}
              className="group flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-primary text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/40"
            >
              {loading ? '確認中…' : submitting ? 'ログイン中…' : 'ログイン'}
              <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
            </button>
          </form>


        </div>
      </div>
    </div>
  )
}
