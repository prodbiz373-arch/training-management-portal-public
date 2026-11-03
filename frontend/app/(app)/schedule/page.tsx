'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Plus, CalendarRange, ChevronRight, Pin } from 'lucide-react'
import { PageHeader } from '@/components/app-shell'
import { MessageDialog, type MessageKind } from '@/components/message-dialog'
import { useAuth } from '@/components/auth/auth-provider'
import { ApiClientError, errorMessage } from '@/lib/auth-api'
import { getSchedules, updateSchedulePin, type ScheduleListItem } from '@/lib/schedule-api'

function displayDate(value: string) { return value.replaceAll('-', '/') }

const pinLimitApiMessage = 'ピン留めできるスケジュールは2件までです。'
const pinLimitDialogMessage = 'ピン留めできるスケジュールは2件までです。\n別のスケジュールのピン留めを解除してから設定してください。'

export default function SchedulePage() {
  const { user, ensureCsrf } = useAuth()
  const [schedules, setSchedules] = useState<ScheduleListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [pinningScheduleId, setPinningScheduleId] = useState<string | null>(null)
  const [messageDialog, setMessageDialog] = useState<{ title: string; message: string; kind: MessageKind } | null>(null)
  const version = useRef(0)
  const pinInFlight = useRef(false)
  const dialogTrigger = useRef<HTMLButtonElement | null>(null)

  const loadSchedules = useCallback(async () => {
    const current = ++version.current
    setLoading(true)
    setListError('')
    try {
      const rows = await getSchedules()
      if (current === version.current) setSchedules(rows)
    } catch (failure) {
      if (current !== version.current) return
      setSchedules([])
      setListError(errorMessage(failure))
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [])

  const closeMessageDialog = useCallback(() => {
    setMessageDialog(null)
    const trigger = dialogTrigger.current
    dialogTrigger.current = null
    window.setTimeout(() => trigger?.focus(), 0)
  }, [])

  const togglePin = async (schedule: ScheduleListItem, trigger: HTMLButtonElement) => {
    if (pinInFlight.current) return
    if (!schedule.pinned && schedules.filter((row) => row.pinned).length >= 2) {
      dialogTrigger.current = trigger
      setMessageDialog({ kind: 'warning', title: 'ピン留めできません', message: pinLimitDialogMessage })
      return
    }

    pinInFlight.current = true
    setPinningScheduleId(schedule.scheduleId)
    setListError('')
    try {
      const csrf = await ensureCsrf()
      await updateSchedulePin(schedule.scheduleId, !schedule.pinned, schedule.permissionUpdateCount, csrf)
      await loadSchedules()
    } catch (failure) {
      if (failure instanceof ApiClientError && failure.status === 400 && failure.message === pinLimitApiMessage) {
        dialogTrigger.current = trigger
        setMessageDialog({ kind: 'warning', title: 'ピン留めできません', message: pinLimitDialogMessage })
      } else {
        setListError(errorMessage(failure))
      }
    } finally {
      pinInFlight.current = false
      setPinningScheduleId(null)
    }
  }
  useEffect(() => {
    try {
      const message = window.sessionStorage.getItem('schedule-deleted-message')
        ?? window.sessionStorage.getItem('schedule-created-message')
      if (message) {
        setSuccessMessage(message)
        window.sessionStorage.removeItem('schedule-deleted-message')
        window.sessionStorage.removeItem('schedule-created-message')
      }
    } catch { /* 保存領域が利用できなくても一覧表示は継続する */ }
    void loadSchedules()
    return () => { version.current += 1 }
  }, [loadSchedules])

  return (
    <div>
      <PageHeader
        id="SCD01"
        title="スケジュール一覧"
        description="権限が与えられているスケジュールのみ表示されます。ピン留めは2件まで設定できます。"
        actions={
          user && <Link href="/schedule/new" className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground hover:bg-primary/90">
            <Plus className="size-4" />新規スケジュール
          </Link>
        }
      />

      {successMessage && <div role="status" className="mb-4 rounded-lg border border-primary/30 bg-primary/[0.04] p-3 text-sm text-primary">{successMessage}</div>}
      {listError && <div role="alert" className="mb-4 rounded-lg border border-destructive/30 p-3">{listError}<button disabled={loading} onClick={() => void loadSchedules()} className="ml-3 text-primary underline">一覧を再取得</button></div>}
      {loading && <p role="status" className="mb-3 text-sm text-muted-foreground">スケジュールを読み込んでいます…</p>}
      <div className="grid gap-3">
        {schedules.map((s) => {
          const canEdit = s.canEdit
          const details = <>
            <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-accent text-primary"><CalendarRange className="size-5" /></div>
            <div className="min-w-0 flex-1">
              <span className="font-mono text-xs text-muted-foreground">#{s.scheduleId}</span>
              <div className="mt-0.5 flex flex-wrap items-center gap-3">
                <p className="truncate text-base font-semibold">{s.scheduleName}</p>
                <span className={`inline-flex items-center rounded-md px-2.5 py-1 text-sm font-semibold ring-1 ring-inset ${canEdit ? 'bg-info text-info-foreground ring-info-foreground/15' : 'bg-muted text-muted-foreground ring-border'}`}>{canEdit ? '編集' : '参照のみ'}</span>
              </div>
              <p className="font-mono text-xs text-muted-foreground">{displayDate(s.startDate)}〜{displayDate(s.endDate)}</p>
            </div>
            <ChevronRight className="size-5 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
          </>
          return (
            <div key={s.scheduleId} className={`group flex items-center gap-4 rounded-xl border bg-card p-4 transition-colors sm:p-5 ${s.pinned ? 'border-primary/40 bg-primary/[0.025]' : 'border-border hover:border-primary/40 hover:bg-primary/[0.03]'}`}>
              <button
                type="button"
                disabled={pinningScheduleId !== null}
                aria-busy={pinningScheduleId === s.scheduleId}
                onClick={(event) => void togglePin(s, event.currentTarget)}
                className={`flex size-10 shrink-0 items-center justify-center rounded-lg border transition-colors ${s.pinned ? 'border-primary/30 bg-primary text-primary-foreground' : 'border-border bg-background text-muted-foreground hover:border-primary/40 hover:text-primary'}`}
                aria-label={s.pinned ? `${s.scheduleName}のピン留めを解除` : `${s.scheduleName}をピン留め`}
                title={s.pinned ? 'ピン留めを解除' : 'ピン留め'}
              >
                <Pin className={`size-4 ${s.pinned ? 'fill-current' : ''}`} />
              </button>
              <Link href={`/schedule/${encodeURIComponent(s.scheduleId)}`} className="flex min-w-0 flex-1 items-center gap-4">{details}</Link>
            </div>
          )
        })}
      </div>
      {messageDialog && <MessageDialog kind={messageDialog.kind} title={messageDialog.title} message={messageDialog.message} onClose={closeMessageDialog} />}
    </div>
  )
}
