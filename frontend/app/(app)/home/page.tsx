'use client'

import Link from 'next/link'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { CalendarClock, FileClock, AlertCircle, CheckCircle2, ArrowUpRight } from 'lucide-react'
import { PageHeader } from '@/components/app-shell'
import { useAuth } from '@/components/auth/auth-provider'
import { StatusBadge } from '@/components/status-badge'
import { errorMessage } from '@/lib/auth-api'
import { getHomeDashboard, type HomeDashboard, type HomeSchedule, type HomeScheduleRow } from '@/lib/home-api'

const dayNames = ['日', '月', '火', '水', '木', '金', '土']

function displayDate(value: string) {
  const [, month, day] = value.split('-').map(Number)
  return `${month}/${day}`
}

function displayDay(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return dayNames[new Date(Date.UTC(year, month - 1, day)).getUTCDay()]
}

function displayPeriod(value: string) {
  return value.replaceAll('-', '/')
}

function progressHref(viewerRole: HomeDashboard['viewerRole'], schedule: HomeSchedule, row: HomeScheduleRow) {
  const params = new URLSearchParams()
  params.set('scheduleId', schedule.scheduleId)
  params.set('categoryNo', String(row.categoryNo))
  if (viewerRole === 'TRAINEE') {
    params.set('scheduleDateNo', String(row.scheduleDateNo))
  } else {
    params.set('from', row.scheduleDate)
    params.set('to', row.scheduleDate)
  }
  return `/progress?${params.toString()}`
}

export default function HomePage() {
  const { user: currentUser, loading: authLoading } = useAuth()
  const [dashboard, setDashboard] = useState<HomeDashboard | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const version = useRef(0)

  const loadDashboard = useCallback(async () => {
    const current = ++version.current
    setLoading(true)
    setLoadError('')
    try {
      const result = await getHomeDashboard()
      if (current === version.current) setDashboard(result)
    } catch (failure) {
      if (current !== version.current) return
      setDashboard(null)
      setLoadError(errorMessage(failure))
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!authLoading && currentUser) void loadDashboard()
    return () => { version.current += 1 }
  }, [authLoading, currentUser, loadDashboard])

  const pinnedToday = useMemo(() => dashboard?.schedules
    .filter((schedule) => schedule.pinned)
    .slice(0, 2)
    .map((schedule) => ({
      schedule,
      today: schedule.details.find((row) => row.scheduleDate === dashboard.businessDate) ?? null,
    })) ?? [], [dashboard])

  if (authLoading || !currentUser) return null

  const taskSummary = dashboard?.taskSummary ?? { unsubmitted: 0, correctionRequired: 0, inReview: 0, completed: 0 }
  const stats = [
    { label: '未提出', value: taskSummary.unsubmitted, icon: AlertCircle, accent: 'text-destructive', ring: 'ring-destructive/20' },
    { label: '修正待ち', value: taskSummary.correctionRequired, icon: FileClock, accent: 'text-warning-foreground', ring: 'ring-warning-foreground/20' },
    { label: 'レビュー中', value: taskSummary.inReview, icon: CalendarClock, accent: 'text-warning-foreground', ring: 'ring-warning-foreground/20' },
    { label: '完了', value: taskSummary.completed, icon: CheckCircle2, accent: 'text-success-foreground', ring: 'ring-success-foreground/20' },
  ]

  return (
    <div>
      <PageHeader
        id="HOM01"
        title={`おかえりなさい、${currentUser.name.split(' ')[0]}さん`}
        description="権限が与えられているスケジュールの全体像です。今日の予定を先頭に、日付の昇順で表示しています。"
      />

      {loadError && <div role="alert" className="mb-4 rounded-lg border border-destructive/30 p-3">{loadError}<button disabled={loading} onClick={() => void loadDashboard()} className="ml-3 text-primary underline">ホームを再取得</button></div>}
      {loading && <p role="status" className="mb-3 text-sm text-muted-foreground">ホームを読み込んでいます…</p>}

      {!loading && dashboard && <>
        <div className="mb-6 overflow-hidden rounded-xl border border-sidebar-border bg-sidebar text-sidebar-foreground">
          {pinnedToday.length === 0 ? (
            <div className="p-6 text-sm text-sidebar-foreground/70">スケジュール一覧から、今日の予定に表示するスケジュールをピン留めしてください。</div>
          ) : (
            <div className={`grid ${pinnedToday.length === 2 ? 'md:grid-cols-2' : 'grid-cols-1'}`}>
              {pinnedToday.map(({ schedule, today }, index) => (
                <div key={schedule.scheduleId} className={`flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between sm:p-6 ${index === 1 ? 'border-t border-sidebar-border md:border-l md:border-t-0' : ''}`}>
                  <div className="flex items-center gap-4">
                    <div className="flex size-14 shrink-0 flex-col items-center justify-center rounded-xl bg-sidebar-accent text-sidebar-accent-foreground">
                      <span className="font-mono text-lg font-bold leading-none">{dashboard.businessDate.split('-')[2]}</span>
                      <span className="text-[10px] text-sidebar-foreground/60">{Number(dashboard.businessDate.split('-')[1])}月 {today ? `(${displayDay(today.scheduleDate)})` : ''}</span>
                    </div>
                    <div className="min-w-0">
                      <p className="text-xs font-medium uppercase tracking-wider text-sidebar-primary-foreground/60">今日の予定</p>
                      <p className="truncate text-xs text-sidebar-foreground/65">{schedule.scheduleName}</p>
                      {today ? <>
                        <p className="text-lg font-bold text-sidebar-accent-foreground">{today.categoryName} ・ {today.content}</p>
                        <p className="text-sm text-sidebar-foreground/70">担当講師: {today.instructorName}</p>
                      </> : <p className="text-base font-semibold text-sidebar-accent-foreground">本日の予定はありません</p>}
                    </div>
                  </div>
                  {today?.taskFlg && <Link
                    href={progressHref(dashboard.viewerRole, schedule, today)}
                    className="inline-flex shrink-0 items-center justify-center gap-1.5 rounded-lg bg-sidebar-primary px-4 py-2 text-sm font-medium text-sidebar-primary-foreground hover:bg-sidebar-primary/90"
                  >課題状況を見る <ArrowUpRight className="size-4" /></Link>}
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="mb-3"><h2 className="text-base font-bold text-[#054A99]">今日までの課題状況</h2></div>
        <div className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
          {stats.map((stat) => {
            const Icon = stat.icon
            return <div key={stat.label} className="rounded-xl border border-border bg-card p-4">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">{stat.label}</span>
                <span className={`flex size-8 items-center justify-center rounded-lg bg-muted ring-1 ring-inset ${stat.ring}`}><Icon className={`size-4 ${stat.accent}`} /></span>
              </div>
              <p className="mt-2 font-mono text-3xl font-bold tabular-nums">{stat.value}</p>
              <p className="mt-0.5 text-xs text-muted-foreground">件の課題</p>
            </div>
          })}
        </div>

        {dashboard.schedules.length === 0 && <div className="rounded-xl border border-border bg-card p-6 text-sm text-muted-foreground">表示するスケジュールがありません</div>}
        {dashboard.schedules.map((schedule) => <section key={schedule.scheduleId} className="mb-5 overflow-hidden rounded-xl border border-border bg-card">
          <div className="flex items-center justify-between border-b border-border bg-[#86D2FD] px-5 py-4">
            <div>
              <h2 className="text-sm font-bold text-[#054A99]">{schedule.scheduleName}</h2>
              <p className="text-xs text-[#054A99]/80">{displayPeriod(schedule.startDate)} 〜 {displayPeriod(schedule.endDate)}</p>
            </div>
            {dashboard.viewerRole === 'INSTRUCTOR' && <Link href="/schedule" className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline">一覧へ <ArrowUpRight className="size-3.5" /></Link>}
          </div>
          <div className="max-h-[31rem] overflow-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead><tr className="border-b border-border text-left text-xs text-muted-foreground"><th className="px-5 py-3 font-medium">日付</th><th className="px-4 py-3 font-medium">分類</th><th className="px-4 py-3 font-medium">内容</th><th className="px-4 py-3 font-medium">講師</th><th className="px-5 py-3 text-right font-medium">課題</th></tr></thead>
              <tbody>
                {schedule.details.map((row) => {
                  const isToday = row.scheduleDate === dashboard.businessDate
                  return <tr key={`${schedule.scheduleId}-${row.scheduleDateNo}`} className={`border-b border-border/70 last:border-0 transition-colors hover:bg-muted/50 ${isToday ? 'bg-primary/[0.04]' : ''}`}>
                    <td className="px-5 py-3"><div className="flex items-center gap-2">{isToday && <span className="size-1.5 rounded-full bg-primary" aria-hidden />}<span className="font-mono tabular-nums">{displayDate(row.scheduleDate)}</span><span className="text-xs text-muted-foreground">({displayDay(row.scheduleDate)})</span></div></td>
                    <td className="px-4 py-3 text-muted-foreground">{row.categoryName}</td>
                    <td className="px-4 py-3 font-medium">{row.content}</td>
                    <td className="px-4 py-3 text-muted-foreground">{row.instructorName}</td>
                    <td className="px-5 py-3 text-right">
                      {!row.taskFlg ? <span className="text-xs text-muted-foreground">課題なし</span>
                        : dashboard.viewerRole === 'INSTRUCTOR' ? <span className="whitespace-nowrap text-sm font-semibold text-[#054A99]">[完了] {row.completedCount}/{row.targetTraineeCount}</span>
                        : row.taskStatusName ? <StatusBadge status={row.taskStatusName} />
                        : <span className="text-xs text-muted-foreground">未提出</span>}
                    </td>
                  </tr>
                })}
                {schedule.details.length === 0 && <tr><td colSpan={5} className="px-5 py-6 text-center text-sm text-muted-foreground">表示するスケジュール内容がありません</td></tr>}
              </tbody>
            </table>
          </div>
        </section>)}
      </>}
    </div>
  )
}
