'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { ArrowLeft, CalendarRange, Check, ChevronLeft, ChevronRight, ListChecks, Plus, Tags, Trash2, Users2 } from 'lucide-react'
import { PageHeader } from '@/components/app-shell'
import { ConfirmDialog, MessageDialog, type MessageKind } from '@/components/message-dialog'
import { useUnsavedChangesGuard } from '@/components/unsaved-changes-guard'
import { useAuth } from '@/components/auth/auth-provider'
import { errorMessage } from '@/lib/auth-api'
import { createSchedule, getNonWorkingDates, getScheduleMemberOptions, type ScheduleCreateRequest, type ScheduleMemberOption } from '@/lib/schedule-api'

type Permission = ScheduleMemberOption & { canEdit: boolean }
type Category = { categoryNo: number; categoryName: string }
type Detail = { scheduleDateNo: number; scheduleDate: string; categoryNo: number; instructorEmployeeId: string; content: string; taskFlg: boolean }
type Month = { year: number; month: number }
const fieldClass = 'h-10 w-full rounded-lg border border-border bg-background px-3 text-sm outline-none focus:border-ring focus:ring-2 focus:ring-ring/20 disabled:bg-muted disabled:opacity-70'
const editableInstructorRequiredMessage = '編集権限を持つ講師を1人以上登録する必要があります。'

function SectionCard({ icon: Icon, title, desc, disabled, children }: { icon: React.ComponentType<{ className?: string }>; title: string; desc?: string; disabled?: boolean; children: React.ReactNode }) {
  return <section className={`overflow-hidden rounded-xl border border-border bg-card ${disabled ? 'opacity-55' : ''}`}><div className="flex items-center gap-3 border-b border-border bg-[#86D2FD] px-5 py-4 text-[#054A99]"><span className="flex size-9 items-center justify-center rounded-lg bg-white/50"><Icon className="size-[18px]" /></span><div><h2 className="text-sm font-bold">{title}</h2>{desc && <p className="text-xs text-[#054A99]/80">{desc}</p>}</div></div><fieldset disabled={disabled} className="p-5 disabled:pointer-events-none">{children}</fieldset></section>
}
function parseDate(value: string) { const [y, m, d] = value.split('-').map(Number); return new Date(y, m - 1, d) }
function formatDate(value: Date) { return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}` }
function monthOf(value: string): Month { return { year: Number(value.slice(0, 4)), month: Number(value.slice(5, 7)) } }
function shiftMonth(value: Month, offset: number): Month { const date = new Date(value.year, value.month - 1 + offset, 1); return { year: date.getFullYear(), month: date.getMonth() + 1 } }
function monthValue(value: Month) { return value.year * 12 + value.month }
function dateLabel(value: string) { const date = parseDate(value); return `${date.getMonth() + 1}/${date.getDate()} (${['日', '月', '火', '水', '木', '金', '土'][date.getDay()]})` }

function scheduleEditorSnapshot({ scheduleId, scheduleName, startDate, endDate, excludeHolidays, basicDecided, permissions, categories, details, newCategory }: {
  scheduleId: string
  scheduleName: string
  startDate: string
  endDate: string
  excludeHolidays: boolean
  basicDecided: boolean
  permissions: Permission[]
  categories: Category[]
  details: Detail[]
  newCategory: string
}) {
  return JSON.stringify({
    scheduleId,
    scheduleName,
    startDate,
    endDate,
    excludeHolidays,
    basicDecided,
    permissions: permissions.map(({ syainId, canEdit }) => ({ syainId, canEdit }))
      .sort((left, right) => left.syainId.localeCompare(right.syainId)),
    categories: categories.map(({ categoryNo, categoryName }) => ({ categoryNo, categoryName }))
      .sort((left, right) => left.categoryNo - right.categoryNo),
    details: details.map(({ scheduleDateNo, scheduleDate, categoryNo, instructorEmployeeId, content, taskFlg }) => ({
      scheduleDateNo, scheduleDate, categoryNo, instructorEmployeeId, content, taskFlg,
    })).sort((left, right) => left.scheduleDateNo - right.scheduleDateNo),
    newCategory,
  })
}

function emptyScheduleEditorSnapshot(permissions: Permission[]) {
  return scheduleEditorSnapshot({
    scheduleId: '', scheduleName: '', startDate: '', endDate: '', excludeHolidays: true,
    basicDecided: false, permissions, categories: [], details: [], newCategory: '',
  })
}

export function ScheduleEditor() {
  const router = useRouter()
  const { user, ensureCsrf } = useAuth()
  const [members, setMembers] = useState<ScheduleMemberOption[]>([])
  const [membersLoading, setMembersLoading] = useState(true)
  const [scheduleId, setScheduleId] = useState('')
  const [scheduleName, setScheduleName] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [excludeHolidays, setExcludeHolidays] = useState(true)
  const [basicDecided, setBasicDecided] = useState(false)
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [details, setDetails] = useState<Detail[]>([])
  const [personPick, setPersonPick] = useState<{ 1: string; 2: string }>({ 1: '', 2: '' })
  const [permissionPick, setPermissionPick] = useState<{ 1: boolean; 2: boolean }>({ 1: false, 2: true })
  const [newCategory, setNewCategory] = useState('')
  const [categoryError, setCategoryError] = useState('')
  const [month, setMonth] = useState<Month | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [deciding, setDeciding] = useState(false)
  const [messageDialog, setMessageDialog] = useState<{ title: string; message: string; kind: MessageKind } | null>(null)
  const [releaseConfirmation, setReleaseConfirmation] = useState(false)
  const [initialSnapshot, setInitialSnapshot] = useState<string | null>(null)
  const dialogTrigger = useRef<HTMLButtonElement | null>(null)
  const initialized = useRef(false)
  const submitInFlight = useRef(false)

  const initialPermissions = useCallback((source: ScheduleMemberOption[]) => {
    const initial = source.filter((member) => member.syainKbn === 2)
      .map((member) => ({ ...member, canEdit: true }))
    const actor = source.find((member) => member.syainId === user?.syainId)
    return actor && !initial.some((member) => member.syainId === actor.syainId)
      ? [...initial, { ...actor, canEdit: true }]
      : initial
  }, [user?.syainId])
  useEffect(() => {
    let active = true
    void getScheduleMemberOptions().then((rows) => {
      if (!active) return
      setMembers(rows)
      if (!initialized.current) {
        const initial = initialPermissions(rows)
        setPermissions(initial)
        setInitialSnapshot(emptyScheduleEditorSnapshot(initial))
        initialized.current = true
      }
    })
      .catch((failure) => {
        if (!active) return
        setSubmitError(errorMessage(failure))
        if (!initialized.current) {
          setInitialSnapshot(emptyScheduleEditorSnapshot([]))
          initialized.current = true
        }
      })
      .finally(() => { if (active) setMembersLoading(false) })
    return () => { active = false }
  }, [initialPermissions])
  const closeMessageDialog = useCallback(() => { setMessageDialog(null); const trigger = dialogTrigger.current; dialogTrigger.current = null; window.setTimeout(() => trigger?.focus(), 0) }, [])

  const permissionsByRole = (kbn: 1 | 2) => permissions.filter((row) => row.syainKbn === kbn)
  const editableInstructorCount = permissions.filter((row) => row.syainKbn === 2 && row.canEdit).length
  const hasEditableInstructor = editableInstructorCount > 0
  const candidates = (kbn: 1 | 2) => members.filter((member) => member.syainKbn === kbn && !permissions.some((row) => row.syainId === member.syainId))
  const instructorPermissions = permissionsByRole(2)
  const monthRows = useMemo(() => details.filter((row) => { if (!month) return false; const value = monthOf(row.scheduleDate); return value.year === month.year && value.month === month.month }), [details, month])
  const startMonth = startDate ? monthOf(startDate) : null
  const endMonth = endDate ? monthOf(endDate) : null
  const currentSnapshot = scheduleEditorSnapshot({
    scheduleId, scheduleName, startDate, endDate, excludeHolidays, basicDecided,
    permissions, categories, details, newCategory,
  })
  const dirty = initialSnapshot !== null && currentSnapshot !== initialSnapshot
  const { navigateWithoutPrompt } = useUnsavedChangesGuard({ dirty, busy: submitting })

  const validateBasic = () => {
    const errors: Record<string, string> = {}
    if (!scheduleId) errors.scheduleId = '必須項目です'
    else if (scheduleId.length > 10 || !/^[A-Za-z0-9_-]+$/.test(scheduleId)) errors.scheduleId = '半角英数字、_、-を10文字以内で入力してください'
    if (!scheduleName.trim()) errors.scheduleName = '必須項目です'
    else if (scheduleName.trim().length > 40) errors.scheduleName = '40文字以内で入力してください'
    if (!startDate) errors.startDate = '必須項目です'
    if (!endDate) errors.endDate = '必須項目です'
    if (startDate && endDate && startDate > endDate) errors.endDate = '終了日は開始日以降を入力してください'
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }
  const decideBasic = async () => {
    if (!validateBasic()) return
    setDeciding(true); setSubmitError('')
    try {
      const excludedDates = new Set(excludeHolidays ? await getNonWorkingDates(startDate, endDate) : [])
      const generated: Detail[] = []
      const current = parseDate(startDate)
      const end = parseDate(endDate)
      while (current <= end) {
        const scheduleDate = formatDate(current)
        if (!excludedDates.has(scheduleDate)) generated.push({ scheduleDateNo: generated.length + 1, scheduleDate, categoryNo: 0, instructorEmployeeId: '', content: '', taskFlg: false })
        current.setDate(current.getDate() + 1)
      }
      setDetails(generated); setMonth(monthOf(startDate)); setBasicDecided(true)
    } catch (failure) {
      setSubmitError(errorMessage(failure))
    } finally {
      setDeciding(false)
    }
  }
  const resetDownstream = () => { setPermissions(initialPermissions(members)); setCategories([]); setDetails([]); setPersonPick({ 1: '', 2: '' }); setPermissionPick({ 1: false, 2: true }); setNewCategory(''); setCategoryError(''); setMonth(null) }
  const releaseBasic = () => { resetDownstream(); setBasicDecided(false); setSubmitError(''); setReleaseConfirmation(false) }
  const clearAll = () => { setScheduleId(''); setScheduleName(''); setStartDate(''); setEndDate(''); setExcludeHolidays(true); setFieldErrors({}); setSubmitError(''); setBasicDecided(false); resetDownstream() }

  const addPermission = (kbn: 1 | 2) => { const member = members.find((row) => row.syainId === personPick[kbn] && row.syainKbn === kbn); if (!basicDecided || !member || permissions.some((row) => row.syainId === member.syainId)) return; setPermissions((rows) => [...rows, { ...member, canEdit: permissionPick[kbn] }]); setPersonPick((current) => ({ ...current, [kbn]: '' })); setSubmitError('') }
  const changePermission = (id: string, editable: boolean) => {
    const target = permissions.find((row) => row.syainId === id)
    if (id === user?.syainId || (target?.syainKbn === 2 && target.canEdit && !editable && editableInstructorCount <= 1)) return
    setPermissions((rows) => rows.map((row) => row.syainId === id ? { ...row, canEdit: editable } : row))
    setSubmitError('')
  }
  const removePermission = (id: string) => {
    const target = permissions.find((row) => row.syainId === id)
    if (id === user?.syainId
        || (target?.syainKbn === 1 && permissionsByRole(1).length <= 1)
        || (target?.syainKbn === 2 && target.canEdit && editableInstructorCount <= 1)) return
    setPermissions((rows) => rows.filter((row) => row.syainId !== id))
    setDetails((rows) => rows.map((row) => row.instructorEmployeeId === id ? { ...row, instructorEmployeeId: '' } : row))
    setSubmitError('')
  }
  const addCategory = () => { const name = newCategory.trim(); if (!basicDecided || !name) return; if (name.length > 30) { setCategoryError('30文字以内で入力してください'); return } if (categories.length >= 99) { setCategoryError('分類は99件まで登録できます'); return } if (categories.some((row) => row.categoryName === name)) { setCategoryError('同じ分類名は登録できません'); return } const used = new Set(categories.map((row) => row.categoryNo)); const no = Array.from({ length: 99 }, (_, index) => index + 1).find((value) => !used.has(value)); if (!no) return; setCategories((rows) => [...rows, { categoryNo: no, categoryName: name }]); setNewCategory(''); setCategoryError('') }
  const removeCategory = (category: Category, trigger: HTMLButtonElement) => { if (details.some((row) => row.categoryNo === category.categoryNo)) { dialogTrigger.current = trigger; setMessageDialog({ kind: 'error', title: '削除できません', message: `「${category.categoryName}」はスケジュール内容で使用されているため削除できません。` }); return } setCategories((rows) => rows.filter((row) => row.categoryNo !== category.categoryNo)) }
  const changeDetail = (no: number, changes: Partial<Detail>) => setDetails((rows) => rows.map((row) => row.scheduleDateNo === no ? { ...row, ...changes, taskFlg: (changes.content ?? row.content).trim() ? (changes.taskFlg ?? row.taskFlg) : false } : row))

  const categoryNames = categories.map((row) => row.categoryName.trim())
  const categoriesValid = categoryNames.every((name) => name.length > 0 && name.length <= 30) && new Set(categoryNames).size === categoryNames.length
  const ready = basicDecided && permissionsByRole(1).length > 0 && hasEditableInstructor && categories.length > 0 && categoriesValid && details.length > 0 && details.every((row) => row.categoryNo > 0 && row.instructorEmployeeId)
  const register = async () => {
    if (!ready || submitting || submitInFlight.current) return
    submitInFlight.current = true; setSubmitting(true); setSubmitError('')
    const request: ScheduleCreateRequest = { scheduleId, scheduleName: scheduleName.trim(), startDate, endDate, excludeHolidays, permissions: permissions.map((row) => ({ employeeId: row.syainId, canEdit: row.canEdit })), categories, details: details.map((row) => ({ ...row, content: row.content.trim() || null })) }
    try { const csrf = await ensureCsrf(); await createSchedule(request, csrf); window.sessionStorage.setItem('schedule-created-message', 'スケジュールを登録しました'); navigateWithoutPrompt(() => router.push('/schedule')) }
    catch (failure) { setSubmitError(errorMessage(failure)) }
    finally { submitInFlight.current = false; setSubmitting(false) }
  }

  return <div>
    <Link href="/schedule" className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />スケジュール一覧へ戻る</Link>
    <PageHeader id="SCD02" title="スケジュール新規登録" description="基本情報を決定後、編集権限と分類を設定して研修内容を登録します。" actions={<><button type="button" onClick={() => void register()} disabled={!ready || submitting} className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground disabled:opacity-50"><Check className="size-4" />{submitting ? '登録中…' : '登録'}</button><button type="button" onClick={clearAll} disabled={submitting || deciding} className="rounded-lg border border-border px-4 py-2.5 text-sm font-semibold disabled:opacity-50">クリア</button></>} />
    {submitError && <div role="alert" className="mb-4 rounded-lg border border-destructive/30 p-3 text-sm">{submitError}</div>}
    {membersLoading && <p role="status" className="mb-3 text-sm text-muted-foreground">メンバーを読み込んでいます…</p>}
    <div className="grid gap-5">
      <SectionCard icon={CalendarRange} title="基本情報" desc="スケジュールの識別情報と期間"><div className="grid gap-4 sm:grid-cols-2"><label className="space-y-1.5 text-sm font-medium">スケジュールID <span className="text-destructive">＊</span><input aria-label="スケジュールID" className={`${fieldClass} font-mono ${fieldErrors.scheduleId ? 'border-destructive' : ''}`} value={scheduleId} onChange={(e) => setScheduleId(e.target.value)} disabled={basicDecided || deciding} maxLength={10} />{fieldErrors.scheduleId && <span className="block text-xs text-destructive">{fieldErrors.scheduleId}</span>}</label><label className="space-y-1.5 text-sm font-medium">スケジュール名 <span className="text-destructive">＊</span><input aria-label="スケジュール名" className={`${fieldClass} ${fieldErrors.scheduleName ? 'border-destructive' : ''}`} value={scheduleName} onChange={(e) => setScheduleName(e.target.value)} disabled={basicDecided || deciding} maxLength={40} />{fieldErrors.scheduleName && <span className="block text-xs text-destructive">{fieldErrors.scheduleName}</span>}</label><div className="space-y-1.5"><span className="text-sm font-medium">登録期間 <span className="text-destructive">＊</span></span><div className="flex items-start gap-2"><label className="flex-1"><input aria-label="開始日" type="date" className={fieldClass} value={startDate} onChange={(e) => setStartDate(e.target.value)} disabled={basicDecided || deciding} />{fieldErrors.startDate && <span className="text-xs text-destructive">{fieldErrors.startDate}</span>}</label><span className="pt-2">〜</span><label className="flex-1"><input aria-label="終了日" type="date" className={fieldClass} value={endDate} onChange={(e) => setEndDate(e.target.value)} disabled={basicDecided || deciding} />{fieldErrors.endDate && <span className="text-xs text-destructive">{fieldErrors.endDate}</span>}</label></div></div><label className="space-y-1.5 text-sm font-medium">休日<span className="flex h-10 items-center gap-2.5 rounded-lg border px-3 font-normal"><input aria-label="土・日・祝日を含まない" type="checkbox" checked={excludeHolidays} onChange={(e) => setExcludeHolidays(e.target.checked)} disabled={basicDecided || deciding} className="size-4 accent-primary" />土・日・祝日を含まない</span></label></div><div className="mt-4 flex justify-end gap-2"><button type="button" disabled={!basicDecided} onClick={() => setReleaseConfirmation(true)} className="rounded-lg border px-4 py-2 text-sm disabled:opacity-50">解除</button><button type="button" disabled={basicDecided || membersLoading || deciding} onClick={() => void decideBasic()} className="rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50">{deciding ? '判定中…' : '決定'}</button></div></SectionCard>
      <SectionCard icon={Users2} title="編集権限" desc="講師・新人ごとに参照／編集権限を設定" disabled={!basicDecided}>
        <div className="grid gap-5 lg:grid-cols-2">{([{ label: '講師', kbn: 2 }, { label: '新人', kbn: 1 }] as const).map(({ label, kbn }) => {
          const rows = permissionsByRole(kbn)
          return <div key={label} className="space-y-3"><h3 className="font-semibold text-[#054A99]">{label}</h3><div className="grid grid-cols-[1fr_8rem_auto] gap-2"><select aria-label={`${label}を選択`} className={fieldClass} value={personPick[kbn]} onChange={(e) => setPersonPick((current) => ({ ...current, [kbn]: e.target.value }))}><option value="">{label}を選択</option>{candidates(kbn).map((member) => <option key={member.syainId} value={member.syainId}>{member.name}</option>)}</select><select aria-label={`${label}の権限`} className={fieldClass} value={permissionPick[kbn] ? '編集' : '参照のみ'} onChange={(e) => setPermissionPick((current) => ({ ...current, [kbn]: e.target.value === '編集' }))}><option>参照のみ</option><option>編集</option></select><button type="button" onClick={() => addPermission(kbn)} disabled={!personPick[kbn]} className="inline-flex items-center justify-center gap-1 rounded-lg bg-secondary px-3 text-sm disabled:opacity-50"><Plus className="size-4" />追加</button></div><div className="max-h-[17rem] overflow-y-auto rounded-lg border"><table className="w-full text-sm"><thead><tr className="bg-muted text-left text-xs text-muted-foreground"><th className="px-3 py-2">No</th><th className="px-3 py-2">社員名</th><th className="px-3 py-2">権限</th><th /></tr></thead><tbody>{rows.map((row, index) => {
            const self = row.syainId === user?.syainId
            const isLastTrainee = kbn === 1 && rows.length === 1
            const isLastEditableInstructor = kbn === 2 && row.canEdit && editableInstructorCount === 1
            const requiredMessage = isLastTrainee ? '新人は1人以上登録する必要があります。' : isLastEditableInstructor ? editableInstructorRequiredMessage : undefined
            return <tr key={row.syainId} className="border-t"><td className="px-3 py-2">{index + 1}</td><td className="px-3 py-2"><div className="text-xs text-muted-foreground">{row.nameKana ?? '—'}</div><div>{row.name}</div><div className="font-mono text-xs text-muted-foreground">{row.syainId}</div></td><td className="px-3 py-2"><select aria-label={`${row.syainId}の権限`} title={isLastEditableInstructor ? editableInstructorRequiredMessage : undefined} className={fieldClass} value={row.canEdit ? '編集' : '参照のみ'} disabled={self || isLastEditableInstructor} onChange={(e) => changePermission(row.syainId, e.target.value === '編集')}><option>参照のみ</option><option>編集</option></select></td><td className="px-3 py-2"><button type="button" aria-label={`${row.syainId}を削除`} title={requiredMessage} disabled={self || isLastTrainee || isLastEditableInstructor} onClick={() => removePermission(row.syainId)} className="text-xs text-destructive disabled:opacity-50"><Trash2 className="mr-1 inline size-3.5" />削除</button></td></tr>
          })}</tbody></table></div>{kbn === 2 && editableInstructorCount === 1 && <p className="text-xs text-muted-foreground">{editableInstructorRequiredMessage}</p>}</div>
        })}</div>
        {!hasEditableInstructor && <p className="mt-3 text-sm text-destructive">{editableInstructorRequiredMessage}</p>}
        {permissionsByRole(1).length === 0 && <p className="mt-3 text-sm text-destructive">新人は1人以上登録する必要があります。</p>}
      </SectionCard>
      <SectionCard icon={Tags} title="分類登録" desc={`研修の分類（最大99個 ・ 現在 ${categories.length}件）`} disabled={!basicDecided}><div className="mb-4 flex gap-2"><input aria-label="分類名" value={newCategory} maxLength={30} onChange={(e) => { setNewCategory(e.target.value); setCategoryError('') }} className={fieldClass} /><button type="button" onClick={addCategory} disabled={!newCategory.trim()} className="inline-flex min-w-[5.5rem] whitespace-nowrap items-center justify-center gap-1 rounded-lg bg-secondary px-4 text-sm disabled:opacity-50"><Plus className="size-4" />追加</button></div>{categoryError && <p className="mb-3 text-sm text-destructive">{categoryError}</p>}<div className="flex flex-wrap gap-2">{categories.map((category) => <span key={category.categoryNo} className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-sm"><input aria-label={`${category.categoryNo}の分類名`} className="w-32 bg-transparent outline-none" value={category.categoryName} maxLength={30} onChange={(e) => setCategories((rows) => rows.map((row) => row.categoryNo === category.categoryNo ? { ...row, categoryName: e.target.value } : row))} /><button type="button" aria-label={`${category.categoryNo}の分類を削除`} onClick={(e) => removeCategory(category, e.currentTarget)}><Trash2 className="size-3.5" /></button></span>)}</div>{!categoriesValid && categories.length > 0 && <p className="mt-3 text-sm text-destructive">分類名は必須で、重複できません。</p>}</SectionCard>
      <SectionCard icon={ListChecks} title="スケジュール内容" desc="月ごとに研修内容を設定" disabled={!basicDecided}><div className="mb-3 flex items-center justify-center gap-4"><button type="button" aria-label="前月" disabled={!month || !startMonth || monthValue(month) <= monthValue(startMonth)} onClick={() => setMonth((value) => value && shiftMonth(value, -1))} className="rounded-lg border p-2 disabled:opacity-40"><ChevronLeft className="size-4" /></button><span className="font-semibold text-[#054A99]">{month?.year}年{month?.month}月</span><button type="button" aria-label="次月" disabled={!month || !endMonth || monthValue(month) >= monthValue(endMonth)} onClick={() => setMonth((value) => value && shiftMonth(value, 1))} className="rounded-lg border p-2 disabled:opacity-40"><ChevronRight className="size-4" /></button></div><div className="overflow-x-auto rounded-lg border"><table className="w-full min-w-[720px] text-sm"><thead><tr className="bg-muted text-left text-xs text-muted-foreground"><th className="px-4 py-2.5">日付</th><th className="px-4 py-2.5">分類<span className="ml-1 text-destructive" aria-label="必須">*</span></th><th className="px-4 py-2.5">講師<span className="ml-1 text-destructive" aria-label="必須">*</span></th><th className="px-4 py-2.5">内容</th><th className="px-4 py-2.5">課題</th></tr></thead><tbody>{monthRows.map((row) => <tr key={row.scheduleDateNo} className="border-t"><td className="px-4 py-2.5">{dateLabel(row.scheduleDate)}</td><td className="px-4 py-2.5"><select aria-label={`${row.scheduleDateNo}の分類`} className={fieldClass} value={row.categoryNo || ''} onChange={(e) => changeDetail(row.scheduleDateNo, { categoryNo: Number(e.target.value) })}><option value="">選択してください</option>{categories.map((category) => <option key={category.categoryNo} value={category.categoryNo}>{category.categoryName}</option>)}</select></td><td className="px-4 py-2.5"><select aria-label={`${row.scheduleDateNo}の講師`} className={fieldClass} value={row.instructorEmployeeId} onChange={(e) => changeDetail(row.scheduleDateNo, { instructorEmployeeId: e.target.value })}><option value="">選択してください</option>{instructorPermissions.map((instructor) => <option key={instructor.syainId} value={instructor.syainId}>{instructor.name}</option>)}</select></td><td className="px-4 py-2.5"><input aria-label={`${row.scheduleDateNo}の内容`} className={fieldClass} maxLength={50} value={row.content ?? ''} onChange={(e) => changeDetail(row.scheduleDateNo, { content: e.target.value })} /></td><td className="px-4 py-2.5 text-center"><input aria-label={`${row.scheduleDateNo}の課題`} type="checkbox" disabled={!row.content.trim()} checked={row.taskFlg} onChange={(e) => changeDetail(row.scheduleDateNo, { taskFlg: e.target.checked })} className="size-4 accent-primary" /></td></tr>)}</tbody></table></div></SectionCard>
    </div>
    {messageDialog && <MessageDialog kind={messageDialog.kind} title={messageDialog.title} message={messageDialog.message} onClose={closeMessageDialog} />}
    {releaseConfirmation && <ConfirmDialog title="基本情報の決定を解除しますか？" message="編集権限・分類・スケジュール内容の入力はクリアされます。" confirmLabel="解除する" onConfirm={releaseBasic} onCancel={() => setReleaseConfirmation(false)} />}
  </div>
}
