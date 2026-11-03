'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Search, SlidersHorizontal, Send, Trash2, X } from 'lucide-react'
import { PageHeader } from '@/components/app-shell'
import { useAuth } from '@/components/auth/auth-provider'
import { StatusBadge } from '@/components/status-badge'
import { errorMessage } from '@/lib/auth-api'
import {
  addProgressComment,
  deleteProgressComment,
  getProgress,
  updateProgressSubmission,
  updateProgressComment,
  type ProgressComment,
  type ProgressData,
  type ProgressFilters,
  type ProgressItem,
} from '@/lib/progress-api'

const dayNames = ['日', '月', '火', '水', '木', '金', '土']
const fieldClass =
  'h-9 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none focus:border-ring focus:ring-2 focus:ring-ring/20'

type Filters = {
  scheduleId: string
  categoryNo: string
  submitter: string
  from: string
  to: string
  reviewerEmployeeId: string
  statusCode: string
}

const initialFilters: Filters = {
  scheduleId: '', categoryNo: '', submitter: '', from: '', to: '', reviewerEmployeeId: '', statusCode: '',
}

function itemKey(item: Pick<ProgressItem, 'scheduleId' | 'scheduleDateNo' | 'traineeEmployeeId'>) {
  return `${item.scheduleId}:${item.scheduleDateNo}:${item.traineeEmployeeId}`
}

function displayDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return { date: `${month}/${day}`, day: dayNames[new Date(Date.UTC(year, month - 1, day)).getUTCDay()] }
}

function filtersFromUrl(): Filters {
  const params = new URLSearchParams(window.location.search)
  return {
    scheduleId: params.get('scheduleId') ?? '',
    categoryNo: params.get('categoryNo') ?? '',
    submitter: params.get('submitter') ?? '',
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
    reviewerEmployeeId: params.get('reviewerEmployeeId') ?? '',
    statusCode: params.get('statusCode') ?? '',
  }
}

function apiFilters(filters: Filters, includeSelection = true): ProgressFilters {
  const params = new URLSearchParams(window.location.search)
  return {
    scheduleId: filters.scheduleId || undefined,
    categoryNo: filters.categoryNo ? Number(filters.categoryNo) : undefined,
    submitter: filters.submitter.trim() || undefined,
    from: filters.from || undefined,
    to: filters.to || undefined,
    reviewerEmployeeId: filters.reviewerEmployeeId || undefined,
    statusCode: filters.statusCode ? Number(filters.statusCode) : undefined,
    scheduleDateNo: includeSelection && params.get('scheduleDateNo') ? Number(params.get('scheduleDateNo')) : undefined,
    traineeEmployeeId: includeSelection ? params.get('traineeEmployeeId') || undefined : undefined,
  }
}

function replaceUrl(filters: Filters, selected?: ProgressItem | null) {
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value) })
  if (selected) {
    params.set('selectedScheduleId', selected.scheduleId)
    params.set('scheduleDateNo', String(selected.scheduleDateNo))
    params.set('traineeEmployeeId', selected.traineeEmployeeId)
  }
  window.history.replaceState(null, '', `/progress${params.size ? `?${params}` : ''}`)
}

export default function ProgressPage() {
  const { user, loading: authLoading } = useAuth()
  const [data, setData] = useState<ProgressData | null>(null)
  const [form, setForm] = useState<Filters>(initialFilters)
  const [appliedFilters, setAppliedFilters] = useState<Filters>(initialFilters)
  const employeeId = user?.syainId
  const employeeRole = user?.syainKbn
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [noticeVersion, setNoticeVersion] = useState(0)
  const requestVersion = useRef(0)

  const load = useCallback(async (filters: Filters, includeSelection = false) => {
    const current = ++requestVersion.current
    setNoticeVersion((version) => version + 1)
    setLoading(true)
    setLoadError('')
    try {
      const result = await getProgress(apiFilters(filters, includeSelection))
      if (current !== requestVersion.current) return
      setData(result)
      const params = new URLSearchParams(window.location.search)
      const scheduleId = params.get('selectedScheduleId') ?? params.get('scheduleId')
      const dateNo = params.get('scheduleDateNo')
      const traineeId = params.get('traineeEmployeeId')
      const requested = dateNo ? result.items.find((item) => item.scheduleId === scheduleId
        && item.scheduleDateNo === Number(dateNo)
        && (!traineeId || item.traineeEmployeeId === traineeId)) : null
      setSelectedKey(requested ? itemKey(requested)
        : result.viewerRole === 'TRAINEE' && result.items.length ? itemKey(result.items[0]) : null)
    } catch (failure) {
      if (current !== requestVersion.current) return
      setData(null)
      setSelectedKey(null)
      setLoadError(errorMessage(failure))
    } finally {
      if (current === requestVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (authLoading || !employeeId) return
    const initial = filtersFromUrl()
    setForm(initial)
    setAppliedFilters(initial)
    void load(initial, !new URLSearchParams(window.location.search).has('selectedScheduleId'))
    return () => { requestVersion.current += 1 }
  }, [authLoading, employeeId, employeeRole, load])

  const selected = data?.items.find((item) => itemKey(item) === selectedKey) ?? null
  const categories = useMemo(() => data?.schedules.find((row) => row.scheduleId === form.scheduleId)?.categories ?? [],
    [data, form.scheduleId])
  const reviewerOptions = useMemo(() => {
    const schedules = form.scheduleId
      ? data?.schedules.filter((row) => row.scheduleId === form.scheduleId) ?? []
      : data?.schedules ?? []
    return Array.from(new Map(schedules.flatMap((row) => row.reviewers)
      .map((reviewer) => [reviewer.employeeId, reviewer])).values())
  }, [data, form.scheduleId])

  if (authLoading || !user) return null

  function update<K extends keyof Filters>(key: K, value: Filters[K]) {
    setNoticeVersion((version) => version + 1)
    setForm((previous) => key === 'scheduleId'
      ? { ...previous, scheduleId: value, categoryNo: '', reviewerEmployeeId: '' }
      : { ...previous, [key]: value })
  }

  function search() {
    setAppliedFilters(form)
    replaceUrl(form)
    void load(form)
  }

  function clear() {
    setForm(initialFilters)
    setAppliedFilters(initialFilters)
    replaceUrl(initialFilters)
    void load(initialFilters)
  }

  function selectItem(item: ProgressItem) {
    setSelectedKey(itemKey(item))
    replaceUrl(appliedFilters, item)
  }

  function updateItem(next: ProgressItem) {
    setData((previous) => previous ? {
      ...previous,
      items: previous.items.map((item) => itemKey(item) === itemKey(next) ? next : item),
    } : previous)
  }

  return (
    <div>
      <PageHeader id="PRM01" title="進捗・課題管理" description="提出状況とレビューの進み具合を一覧で確認。行を選択すると詳細とコメントが表示されます。" />

      {loadError && <div role="alert" className="mb-4 rounded-lg border border-destructive/30 p-3">
        {loadError}<button disabled={loading} onClick={() => void load(appliedFilters)} className="ml-3 text-primary underline">再取得</button>
      </div>}
      {loading && <p role="status" className="mb-4 text-sm text-muted-foreground">進捗・課題を読み込んでいます…</p>}

      <section className="mb-5 overflow-hidden rounded-xl border border-border bg-card">
        <div className="flex items-center gap-2 border-b border-border bg-[#86D2FD] px-5 py-3.5 text-sm font-bold text-[#054A99]"><SlidersHorizontal className="size-4" />検索</div>
        <div className="p-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <div className="space-y-1"><label className="text-xs text-subheading">スケジュール</label>
              <select aria-label="スケジュール" className={fieldClass} value={form.scheduleId} onChange={(event) => update('scheduleId', event.target.value)}><option value="">すべて</option>{data?.schedules.map((schedule) => <option key={schedule.scheduleId} value={schedule.scheduleId}>{schedule.scheduleName}</option>)}</select></div>
            <div className="space-y-1"><label className="text-xs text-subheading">分類</label>
              <select aria-label="分類" className={fieldClass} value={form.categoryNo} disabled={!form.scheduleId} onChange={(event) => update('categoryNo', event.target.value)}><option value="">すべて</option>{categories.map((category) => <option key={category.categoryNo} value={category.categoryNo}>{category.categoryName}</option>)}</select></div>
            <div className="space-y-1"><label className="text-xs text-subheading">提出者</label><input aria-label="提出者" className={fieldClass} placeholder="氏名で絞り込み" value={form.submitter} onChange={(event) => update('submitter', event.target.value)} /></div>
            <div className="space-y-1 sm:col-span-2 lg:col-span-1"><label className="text-xs text-subheading">期間</label><div className="flex items-center gap-2"><input aria-label="期間開始" type="date" className={fieldClass} value={form.from} onChange={(event) => update('from', event.target.value)} /><span className="text-muted-foreground">〜</span><input aria-label="期間終了" type="date" className={fieldClass} value={form.to} onChange={(event) => update('to', event.target.value)} /></div></div>
            <div className="space-y-1"><label className="text-xs text-subheading">レビュー者</label>
              <select aria-label="レビュー者検索" className={fieldClass} value={form.reviewerEmployeeId} onChange={(event) => update('reviewerEmployeeId', event.target.value)}><option value="">すべて</option>{reviewerOptions.map((reviewer) => <option key={reviewer.employeeId} value={reviewer.employeeId}>{reviewer.name}</option>)}</select></div>
            <div className="space-y-1"><label className="text-xs text-subheading">ステータス</label>
              <select aria-label="ステータス検索" className={fieldClass} value={form.statusCode} disabled={Boolean(data && data.statuses.length === 0)} onChange={(event) => update('statusCode', event.target.value)}><option value="">{data && data.statuses.length === 0 ? 'ステータスマスタなし' : 'すべて'}</option>{data?.statuses.map((status) => <option key={status.statusCode} value={String(status.statusCode)}>{status.statusName}</option>)}</select></div>
          </div>
          <div className="mt-4 flex justify-end gap-2"><button type="button" onClick={clear} disabled={loading} className="h-9 rounded-lg border border-border bg-background px-4 text-sm font-medium hover:bg-muted disabled:opacity-50">クリア</button><button type="button" onClick={search} disabled={loading} className="h-9 rounded-lg bg-primary px-5 text-sm font-semibold text-primary-foreground hover:bg-primary/90 disabled:opacity-50">検索</button></div>
        </div>
      </section>

      <div className="grid gap-5 lg:grid-cols-[1fr_23rem]">
        <section className="overflow-hidden rounded-xl border border-border bg-card">
          <div className="flex items-center justify-between border-b border-border bg-[#86D2FD] px-5 py-3.5"><h2 className="text-sm font-bold text-[#054A99]">進捗・課題一覧</h2><span className="font-mono text-xs text-muted-foreground">{data?.items.length ?? 0} 件</span></div>
          <div className="max-h-[38rem] overflow-auto"><table className="w-full min-w-[640px] text-sm">
            <thead><tr className="border-b border-border text-left text-xs text-muted-foreground"><th className="px-5 py-3 font-medium">日付</th><th className="px-4 py-3 font-medium">内容 / スケジュール [分類]</th><th className="px-4 py-3 font-medium">提出者</th><th className="px-4 py-3 font-medium">レビュー者</th><th className="px-5 py-3 text-right font-medium">ステータス</th></tr></thead>
            <tbody>
              {!loading && data?.items.length === 0 && <tr><td colSpan={5} className="px-5 py-10 text-center text-sm text-muted-foreground">対象の課題はありません。</td></tr>}
              {data?.items.map((item) => { const shown = displayDate(item.scheduleDate); const active = itemKey(item) === selectedKey; return <tr key={itemKey(item)} onClick={() => selectItem(item)} className={`cursor-pointer border-b border-border/70 last:border-0 transition-colors hover:bg-muted/50 ${active ? 'bg-primary/[0.05]' : ''}`}>
                <td className="whitespace-nowrap px-5 py-3"><span className="font-mono tabular-nums">{shown.date}</span><span className="ml-1 text-xs text-muted-foreground">({shown.day})</span></td>
                <td className="px-4 py-3"><p className="font-medium">{item.content}</p><p className="text-xs text-muted-foreground">{item.scheduleName} [{item.categoryName}]</p></td><td className="px-4 py-3">{item.traineeName}</td><td className="px-4 py-3 text-muted-foreground">{item.reviewerName ?? '—'}</td><td className="px-5 py-3 text-right"><StatusBadge status={item.statusName} /></td>
              </tr> })}
            </tbody>
          </table></div>
        </section>

        <aside className="lg:sticky lg:top-20 lg:self-start">
          {selected && data ? <DetailPanel key={itemKey(selected)} item={selected} data={data} currentEmployeeId={user.syainId} noticeVersion={noticeVersion} onChange={updateItem} onClose={() => { setSelectedKey(null); replaceUrl(appliedFilters) }} /> :
            <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border bg-card px-6 py-16 text-center"><div className="mb-3 flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground"><Search className="size-5" /></div><p className="text-sm font-medium">課題を選択してください</p><p className="mt-1 text-xs text-muted-foreground text-pretty">一覧の行を選択すると、詳細とコメントがここに表示されます。</p></div>}
        </aside>
      </div>
    </div>
  )
}

function DetailPanel({ item, data, currentEmployeeId, noticeVersion, onChange, onClose }: { item: ProgressItem; data: ProgressData; currentEmployeeId: string; noticeVersion: number; onChange: (item: ProgressItem) => void; onClose: () => void }) {
  const { ensureCsrf } = useAuth()
  const [reviewerId, setReviewerId] = useState(item.reviewerEmployeeId ?? '')
  const [statusCode, setStatusCode] = useState(String(item.statusCode))
  const [draft, setDraft] = useState('')
  const [editingComment, setEditingComment] = useState<ProgressComment | null>(null)
  const [editContent, setEditContent] = useState('')
  const mutationPending = useRef(false)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')
  const [savedAtVersion, setSavedAtVersion] = useState<number | null>(null)
  const selectedStatus = Number(statusCode)
  const statusAvailable = data.statuses.some((status) => status.statusCode === selectedStatus)
  const completedDate = selectedStatus === 6 || selectedStatus === 7 ? item.completedDate ?? data.businessDate : ''
  const shown = displayDate(item.scheduleDate)
  const reviewers = data.schedules.find((schedule) => schedule.scheduleId === item.scheduleId)?.reviewers ?? []
  const selectClass = 'h-9 w-full rounded-lg border border-border bg-background px-2.5 text-sm text-foreground outline-none focus:border-ring focus:ring-2 focus:ring-ring/20 disabled:cursor-not-allowed disabled:opacity-60'

  async function save() {
    if (!statusAvailable || mutationPending.current) return
    const savingVersion = noticeVersion
    setSavedAtVersion(null)
    mutationPending.current = true
    setBusy(true); setActionError('')
    try {
      const result = await updateProgressSubmission(item, reviewerId || null, selectedStatus, await ensureCsrf())
      onChange({ ...item, reviewerEmployeeId: result.reviewerEmployeeId, reviewerName: result.reviewerName, statusCode: result.statusCode, statusName: result.statusName, completedDate: result.completedDate, submissionUpdateCount: result.updateCount })
      setSavedAtVersion(savingVersion)
    } catch (failure) { setActionError(errorMessage(failure)) } finally { mutationPending.current = false; setBusy(false) }
  }

  async function addComment() {
    const content = draft.trim(); if (!content || mutationPending.current) return
    mutationPending.current = true
    setBusy(true); setActionError('')
    try {
      const comment = await addProgressComment(item, content, await ensureCsrf())
      onChange({ ...item, comments: [...item.comments, comment], submissionUpdateCount: item.submissionUpdateCount ?? 0 }); setDraft('')
    } catch (failure) { setActionError(errorMessage(failure)) } finally { mutationPending.current = false; setBusy(false) }
  }

  async function removeComment(comment: ProgressComment) {
    if (mutationPending.current || !window.confirm('コメントを削除しますか？')) return
    mutationPending.current = true
    setBusy(true); setActionError('')
    try { await deleteProgressComment(item, comment, await ensureCsrf()); onChange({ ...item, comments: item.comments.filter((row) => row.commentNo !== comment.commentNo) }) }
    catch (failure) { setActionError(errorMessage(failure)) } finally { mutationPending.current = false; setBusy(false) }
  }

  function beginCommentEdit(comment: ProgressComment) {
    if (mutationPending.current || !item.canComment || comment.authorEmployeeId !== currentEmployeeId) return
    setEditingComment(comment); setEditContent(comment.content); setActionError('')
  }
  function cancelCommentEdit() {
    if (mutationPending.current) return
    setEditingComment(null); setEditContent(''); setActionError('')
  }
  async function saveCommentEdit() {
    if (!editingComment || mutationPending.current || !item.canComment
        || editingComment.authorEmployeeId !== currentEmployeeId) return
    const content = editContent.trim()
    if (!content || editContent.length > 200) {
      setActionError('コメントは必須、200文字以内で入力してください。'); return
    }
    mutationPending.current = true; setBusy(true); setActionError('')
    try {
      const updated = await updateProgressComment(item, editingComment, content, await ensureCsrf())
      onChange({ ...item, comments: item.comments.map((row) => row.commentNo === updated.commentNo ? updated : row) })
      setEditingComment(null); setEditContent('')
    } catch (failure) { setActionError(errorMessage(failure)) }
    finally { mutationPending.current = false; setBusy(false) }
  }

  return <div className="overflow-hidden rounded-xl border border-border bg-card">
    <div className="flex items-start justify-between border-b border-border bg-[#86D2FD] px-5 py-4"><div><p className="text-xs text-[#054A99]/80">課題詳細</p><h3 className="text-sm font-bold text-[#054A99]">{item.content}</h3></div><button onClick={onClose} className="rounded-md p-1 text-muted-foreground hover:bg-muted" aria-label="閉じる"><X className="size-4" /></button></div>
    <dl className="grid grid-cols-2 gap-px bg-border">{[['日付', `${shown.date}(${shown.day})`], ['スケジュール', item.scheduleName], ['提出者', item.traineeName], ['分類', item.categoryName]].map(([key, value]) => <div key={key} className="bg-card px-5 py-3"><dt className="text-xs text-muted-foreground">{key}</dt><dd className="mt-0.5 text-sm font-medium">{value}</dd></div>)}</dl>
    {savedAtVersion === noticeVersion && <p role="status" className="mx-5 mt-4 rounded-lg border border-primary/20 bg-primary/5 p-3 text-sm">更新しました</p>}
    {actionError && <p role="alert" className="mx-5 mt-4 rounded-lg border border-destructive/30 p-2 text-sm">{actionError}</p>}
    <div className="space-y-3 border-t border-border px-5 py-4"><p className="text-xs font-medium text-subheading">レビュー情報を編集</p>
      <div className="space-y-1"><div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5"><label className="text-xs text-muted-foreground">レビュー者</label><span className="text-xs text-muted-foreground/70">※選択できるのは編集権限を持つ社員のみ</span></div><select aria-label="レビュー者" className={selectClass} value={reviewerId} disabled={!item.canEdit || busy} onChange={(event) => setReviewerId(event.target.value)}><option value="">—</option>{reviewers.map((reviewer) => <option key={reviewer.employeeId} value={reviewer.employeeId}>{reviewer.name}</option>)}</select></div>
      <div className="grid grid-cols-2 gap-2"><div className="space-y-1"><label className="text-xs text-muted-foreground">ステータス</label><select aria-label="ステータス" className={selectClass} value={statusAvailable ? statusCode : ''} disabled={!item.canEdit || busy || editingComment !== null || !statusAvailable} onChange={(event) => setStatusCode(event.target.value)}>{!statusAvailable && <option value="">{data.statuses.length === 0 ? 'ステータスマスタが登録されていません' : '現在のステータスを確認できません'}</option>}{data.statuses.map((status) => <option key={status.statusCode} value={String(status.statusCode)}>{status.statusName}</option>)}</select>{data.statuses.length === 0 && <p role="status" className="text-xs text-destructive">ステータスマスタが登録されていません。</p>}</div><div className="space-y-1"><label className="text-xs text-muted-foreground">完了日</label><input aria-label="完了日" type="date" className={selectClass} value={completedDate} readOnly /></div></div>
      <div className="flex justify-end pt-1"><button onClick={() => void save()} disabled={!item.canEdit || busy || editingComment !== null || !statusAvailable} className="rounded-lg bg-primary px-3.5 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50">保存</button></div>
    </div>
    <div className="border-t border-border px-5 py-4"><p className="mb-3 text-xs font-medium text-subheading">コメント一覧</p><ul className="max-h-[19rem] space-y-3 overflow-y-auto pr-1">
      {item.comments.length === 0 && <li className="text-sm text-muted-foreground">コメントはありません。</li>}
      {item.comments.map((comment) => { const mine = comment.authorEmployeeId === currentEmployeeId; return <li key={comment.commentNo} className="rounded-lg bg-muted/50 p-3"><div className="flex items-center justify-between"><span className="text-sm font-medium">{comment.authorName ?? '—'}{mine && <span className="ml-1.5 rounded bg-primary/10 px-1.5 py-0.5 text-[10px] font-medium text-primary">自分</span>}</span><span className="font-mono text-[11px] text-muted-foreground">{comment.updatedAt.replace('T', ' ').slice(0, 16).replaceAll('-', '/')}</span></div>{editingComment?.commentNo === comment.commentNo ? <div className="mt-2 space-y-2"><textarea aria-label="コメント編集内容" rows={2} maxLength={200} value={editContent} disabled={busy} onChange={(event) => setEditContent(event.target.value)} className="w-full resize-none rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-ring focus:ring-2 focus:ring-ring/20 disabled:opacity-60" /><div className="flex justify-end gap-2"><button type="button" onClick={cancelCommentEdit} disabled={busy} className="rounded-lg border border-border px-3 py-1.5 text-xs disabled:opacity-50">キャンセル</button><button type="button" onClick={() => void saveCommentEdit()} disabled={busy} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground disabled:opacity-50">コメントを保存</button></div></div> : <><p className="mt-1 text-sm leading-relaxed text-foreground/80">{comment.content}</p>{mine && item.canComment && <button type="button" onClick={() => beginCommentEdit(comment)} disabled={busy || editingComment !== null} className="mt-1.5 mr-3 inline-flex items-center gap-1 text-xs text-primary hover:underline disabled:opacity-50">編集</button>}</>}{mine && <button onClick={() => void removeComment(comment)} disabled={busy || editingComment !== null} className="mt-1.5 inline-flex items-center gap-1 text-xs text-destructive hover:underline disabled:opacity-50"><Trash2 className="size-3" />削除</button>}</li> })}
    </ul><div className="mt-4"><p className="mb-1.5 text-xs font-medium text-subheading">コメント追加</p><div className="flex items-end gap-2"><textarea aria-label="コメント入力" rows={2} maxLength={200} value={draft} disabled={!item.canComment || busy || editingComment !== null} onChange={(event) => setDraft(event.target.value)} placeholder="フィードバックを入力…" className="w-full resize-none rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus:border-ring focus:ring-2 focus:ring-ring/20 disabled:cursor-not-allowed disabled:opacity-60" /><button onClick={() => void addComment()} disabled={!item.canComment || busy || editingComment !== null || !draft.trim()} className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50" aria-label="コメントを追加"><Send className="size-4" /></button></div></div></div>
  </div>
}
