'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { ArrowLeft, CalendarRange, Check, ChevronLeft, ChevronRight, ListChecks, Tags, Trash2, Users2 } from 'lucide-react'
import { PageHeader } from '@/components/app-shell'
import { ConfirmDialog, MessageDialog, type MessageKind } from '@/components/message-dialog'
import { useUnsavedChangesGuard } from '@/components/unsaved-changes-guard'
import { useAuth } from '@/components/auth/auth-provider'
import { ApiClientError, errorMessage } from '@/lib/auth-api'
import { deleteSchedule, getSchedule, getScheduleMemberOptions, updateSchedule, type ScheduleCategory, type ScheduleContentDetail, type ScheduleDetail, type ScheduleMemberOption, type SchedulePermission, type ScheduleUpdateRequest } from '@/lib/schedule-api'

type Month = { year: number; month: number }

const fieldClass = 'h-10 w-full rounded-lg border border-border bg-background px-3 text-sm outline-none focus:border-ring focus:ring-2 focus:ring-ring/20 disabled:bg-muted disabled:opacity-70 read-only:bg-muted read-only:focus:border-border read-only:focus:ring-0'
const editableInstructorRequiredMessage = '編集権限を持つ講師を1人以上登録する必要があります。'
const progressMemberInUseMessage = '進捗・課題管理でレビュー者として登録、またはコメントを記載しているため削除できません'
const reviewerPermissionRequiredMessage = '進捗・課題管理でレビュー者として登録されているため参照のみに変更できません'

function monthOf(date: string): Month {
  return { year: Number(date.slice(0, 4)), month: Number(date.slice(5, 7)) }
}

function shiftMonth(value: Month, offset: number): Month {
  const base = value.year * 12 + value.month - 1 + offset
  return { year: Math.floor(base / 12), month: base % 12 + 1 }
}

function dateLabel(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  const weekday = ['日', '月', '火', '水', '木', '金', '土'][new Date(year, month - 1, day).getDay()]
  return { date: `${month}/${day}`, weekday }
}

function mergeMemberOptions(options: ScheduleMemberOption[], permissions: SchedulePermission[]) {
  const byEmployeeId = new Map(options.map((member) => [member.syainId, member]))
  permissions.forEach((permission) => {
    if (!byEmployeeId.has(permission.employeeId)) {
      byEmployeeId.set(permission.employeeId, {
        syainId: permission.employeeId,
        syainKbn: permission.syainKbn,
        name: permission.name,
        nameKana: permission.nameKana,
      })
    }
  })
  return Array.from(byEmployeeId.values())
}

function editableState(sourcePermissions: SchedulePermission[], sourceCategories: ScheduleCategory[], sourceDetails: ScheduleContentDetail[]) {
  return {
    permissions: sourcePermissions.map(({ employeeId, canEdit }) => ({ employeeId, canEdit }))
      .sort((left, right) => left.employeeId.localeCompare(right.employeeId)),
    categories: sourceCategories.map(({ categoryNo, categoryName }) => ({ categoryNo, categoryName }))
      .sort((left, right) => left.categoryNo - right.categoryNo),
    details: sourceDetails.map(({ scheduleDateNo, categoryNo, instructorEmployeeId, content, taskFlg }) => ({ scheduleDateNo, categoryNo, instructorEmployeeId, content, taskFlg }))
      .sort((left, right) => left.scheduleDateNo - right.scheduleDateNo),
  }
}

function SectionCard({ icon: Icon, title, desc, children }: {
  icon: React.ComponentType<{ className?: string }>
  title: string
  desc?: string
  children: React.ReactNode
}) {
  return <section className="overflow-hidden rounded-xl border border-border bg-card">
    <div className="flex items-center gap-3 border-b border-border bg-[#86D2FD] px-5 py-4 text-[#054A99]">
      <span className="flex size-9 items-center justify-center rounded-lg bg-white/50 text-[#054A99]"><Icon className="size-[18px]" /></span>
      <div><h2 className="text-sm font-bold text-[#054A99]">{title}</h2>{desc && <p className="text-xs text-[#054A99]/80">{desc}</p>}</div>
    </div>
    <div className="p-5">{children}</div>
  </section>
}

export function ScheduleDetail({ scheduleId }: { scheduleId: string }) {
  const router = useRouter()
  const { user, ensureCsrf } = useAuth()
  const [schedule, setSchedule] = useState<ScheduleDetail | null>(null)
  const [scheduleName, setScheduleName] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [month, setMonth] = useState<Month | null>(null)
  const [permissions, setPermissions] = useState<SchedulePermission[]>([])
  const [categories, setCategories] = useState<ScheduleCategory[]>([])
  const [details, setDetails] = useState<ScheduleContentDetail[]>([])
  const [members, setMembers] = useState<ScheduleMemberOption[]>([])
  const [memberOptionsError, setMemberOptionsError] = useState('')
  const [personPick, setPersonPick] = useState<{ 1: string; 2: string }>({ 1: '', 2: '' })
  const [permissionPick, setPermissionPick] = useState<{ 1: boolean; 2: boolean }>({ 1: false, 2: true })
  const [newCategory, setNewCategory] = useState('')
  const [updating, setUpdating] = useState(false)
  const [updateError, setUpdateError] = useState('')
  const [scheduleNameError, setScheduleNameError] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')
  const [messageDialog, setMessageDialog] = useState<{ title: string; message: string; kind: MessageKind } | null>(null)
  const [deleteConfirmation, setDeleteConfirmation] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const version = useRef(0)
  const updateInFlight = useRef(false)
  const deleteInFlight = useRef(false)
  const dialogTrigger = useRef<HTMLButtonElement | null>(null)
  const deleteButton = useRef<HTMLButtonElement | null>(null)
  const canEdit = schedule?.canEdit ?? false

  const closeMessageDialog = useCallback(() => {
    setMessageDialog(null)
    setDeleteConfirmation(false)
    const trigger = dialogTrigger.current
    dialogTrigger.current = null
    window.setTimeout(() => trigger?.focus(), 0)
  }, [])

  const loadSchedule = useCallback(async () => {
    const current = ++version.current
    setLoading(true)
    setLoadError('')
    setUpdateError(''); setScheduleNameError(false)
    setSuccessMessage('')
    setMessageDialog(null)
    setMemberOptionsError('')
    dialogTrigger.current = null
    try {
      const next = await getSchedule(scheduleId)
      if (current !== version.current) return
      setSchedule(next)
      setScheduleName(next.scheduleName)
      setMonth(monthOf(next.startDate))
      setPermissions(next.permissions)
      setCategories(next.categories)
      setDetails(next.details)
      if (next.canEdit) {
        // 詳細に含まれるメンバーは、保存前に削除しても再追加できるよう候補カタログへ保持する。
        setMembers(mergeMemberOptions([], next.permissions))
        try {
          const allMembers = await getScheduleMemberOptions()
          if (current === version.current) setMembers(mergeMemberOptions(allMembers, next.permissions))
        } catch (failure) {
          // 詳細表示と保存前の再追加は継続し、未登録メンバー候補を取得できなかったことは明示する。
          if (current === version.current) setMemberOptionsError(errorMessage(failure))
        }
      } else {
        setMembers([])
      }
    } catch (failure) {
      if (current !== version.current) return
      setSchedule(null)
      setLoadError(errorMessage(failure))
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [scheduleId])

  useEffect(() => {
    void loadSchedule()
    return () => { version.current += 1 }
  }, [loadSchedule])

  const rows = useMemo(() => {
    if (!schedule || !month) return []
    return details.filter((detail) => {
      const detailMonth = monthOf(detail.scheduleDate)
      return detailMonth.year === month.year && detailMonth.month === month.month
    })
  }, [details, month, schedule])

  const persistedDirty = Boolean(schedule?.canEdit) && (scheduleName !== schedule?.scheduleName || JSON.stringify(editableState(permissions, categories, details))
    !== JSON.stringify(editableState(schedule?.permissions ?? [], schedule?.categories ?? [], schedule?.details ?? [])))
  const dirty = Boolean(schedule?.canEdit) && (persistedDirty || newCategory !== '')
  const { navigateWithoutPrompt } = useUnsavedChangesGuard({ dirty, busy: updating || deleting })

  if (loading) return <p role="status" className="text-sm text-muted-foreground">スケジュールを読み込んでいます…</p>

  if (!schedule) {
    return <div>
      <Link href="/schedule" className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />スケジュール一覧へ戻る</Link>
      <div role="alert" className="rounded-lg border border-destructive/30 p-3">
        {loadError || 'スケジュールを読み込めませんでした'}
        <button type="button" disabled={loading} onClick={() => void loadSchedule()} className="ml-3 text-primary underline">再試行</button>
      </div>
    </div>
  }

  const instructors = permissions.filter((permission) => permission.syainKbn === 2)
  const permissionsByRole = (syainKbn: 1 | 2) => permissions.filter((permission) => permission.syainKbn === syainKbn)
  const editableInstructorCount = permissions.filter((permission) => permission.syainKbn === 2 && permission.canEdit).length
  const hasEditableInstructor = editableInstructorCount > 0
  const permissionsValid = permissionsByRole(1).length > 0 && hasEditableInstructor
  const assignedEmployeeIds = new Set(permissions.map((permission) => permission.employeeId))
  const candidates = (syainKbn: 1 | 2) => members.filter((member) =>
    member.syainKbn === syainKbn && !assignedEmployeeIds.has(member.syainId))
  const changePermission = (employeeId: string, nextCanEdit: boolean) => {
    if (!canEdit) return
    const target = permissions.find((permission) => permission.employeeId === employeeId)
    if (target?.syainKbn === 2 && target.canEdit && !nextCanEdit && editableInstructorCount <= 1) {
      setUpdateError(editableInstructorRequiredMessage)
      setSuccessMessage('')
      return
    }
    setUpdateError(''); setScheduleNameError(false); setSuccessMessage('')
    setPermissions((current) => current.map((permission) => permission.employeeId === employeeId
      ? { ...permission, canEdit: nextCanEdit }
      : permission))
  }
  const removePermission = (employeeId: string, trigger: HTMLButtonElement) => {
    if (!canEdit) return
    const target = permissions.find((permission) => permission.employeeId === employeeId)
    if (target?.usedAsReviewer || target?.hasComments) {
      dialogTrigger.current = trigger
      setMessageDialog({ kind: 'error', title: '削除できません', message: progressMemberInUseMessage })
      setSuccessMessage('')
      return
    }
    if (target?.syainKbn === 1 && permissionsByRole(1).length <= 1) {
      setUpdateError('新人は1人以上登録する必要があります。')
      setSuccessMessage('')
      return
    }
    if (target?.syainKbn === 2 && target.canEdit && editableInstructorCount <= 1) {
      setUpdateError(editableInstructorRequiredMessage)
      setSuccessMessage('')
      return
    }
    setUpdateError(''); setScheduleNameError(false); setSuccessMessage('')
    setPermissions((current) => current.filter((permission) => permission.employeeId !== employeeId))
  }
  const addPermission = (syainKbn: 1 | 2) => {
    if (!canEdit) return
    const picked = members.find((member) => member.syainId === personPick[syainKbn] && member.syainKbn === syainKbn)
    if (!picked) return
    setUpdateError(''); setScheduleNameError(false); setSuccessMessage('')
    const baseline = schedule.permissions.find((permission) => permission.employeeId === picked.syainId)
    setPermissions((current) => {
      if (current.some((permission) => permission.employeeId === picked.syainId)) return current
      return [...current, baseline
        ? { ...baseline, canEdit: permissionPick[syainKbn] }
        : {
            employeeId: picked.syainId,
            name: picked.name,
            nameKana: picked.nameKana,
            syainKbn,
            canEdit: permissionPick[syainKbn],
            pinned: false,
            updateCount: 0,
            usedAsReviewer: false,
            hasComments: false,
          }]
    })
    setPersonPick((current) => ({ ...current, [syainKbn]: '' }))
  }
  const addCategory = () => {
    const name = newCategory.trim()
    if (!canEdit || !name || categories.length >= 99) return
    const used = new Set([...categories, ...schedule.categories].map((category) => category.categoryNo))
    const nextNo = Array.from({ length: 99 }, (_, index) => index + 1).find((no) => !used.has(no))
    if (!nextNo) return
    setUpdateError(''); setScheduleNameError(false); setSuccessMessage('')
    setCategories((current) => [...current, { categoryNo: nextNo, categoryName: name, updateCount: 0 }])
    setNewCategory('')
  }
  const renameCategory = (categoryNo: number, categoryName: string) => {
    if (!canEdit) return
    setUpdateError(''); setScheduleNameError(false); setSuccessMessage('')
    setCategories((current) => current.map((category) => category.categoryNo === categoryNo ? { ...category, categoryName } : category))
  }
  const removeCategory = (categoryNo: number, trigger: HTMLButtonElement) => {
    if (!canEdit) return
    const category = categories.find((value) => value.categoryNo === categoryNo)
    if (details.some((detail) => detail.categoryNo === categoryNo)) {
      dialogTrigger.current = trigger
      setMessageDialog({ kind: 'error',
        title: '削除できません',
        message: category
          ? `「${category.categoryName}」はスケジュール内容で使用されているため削除できません。`
          : 'この分類はスケジュール内容で使用されているため削除できません。',
      })
      setSuccessMessage('')
      return
    }
    setUpdateError(''); setScheduleNameError(false); setSuccessMessage('')
    setCategories((current) => current.filter((category) => category.categoryNo !== categoryNo))
  }
  const changeDetail = (scheduleDateNo: number, changes: Partial<ScheduleContentDetail>) => {
    if (!canEdit) return
    setUpdateError(''); setScheduleNameError(false); setSuccessMessage('')
    const previous = details.find((detail) => detail.scheduleDateNo === scheduleDateNo)
    if (previous?.taskFlg && previous.taskRemovalBlocked
        && (changes.taskFlg === false || (changes.content !== undefined && !changes.content?.trim()))) {
      setMessageDialog({ kind: 'error', title: '課題を解除できません', message: '未提出以外の提出状況、またはコメントが登録されているため、課題を解除できません。' })
      return
    }
    setDetails((current) => current.map((detail) => detail.scheduleDateNo === scheduleDateNo ? { ...detail, ...changes, taskFlg: (changes.content !== undefined ? changes.content : detail.content)?.trim() ? (changes.taskFlg ?? detail.taskFlg) : false } : detail))
  }

  const updateRequest = (): ScheduleUpdateRequest => ({
    scheduleName,
    scheduleUpdateCount: schedule.scheduleUpdateCount,
    permissions: permissions.map(({ employeeId, canEdit, updateCount }) => ({ employeeId, canEdit, updateCount })),
    deletedPermissions: schedule.permissions
      .filter((baseline) => !permissions.some((current) => current.employeeId === baseline.employeeId))
      .map(({ employeeId, updateCount }) => ({ employeeId, updateCount })),
    categories: categories.map(({ categoryNo, categoryName, updateCount }) => ({ categoryNo, categoryName, updateCount })),
    deletedCategories: schedule.categories
      .filter((baseline) => !categories.some((current) => current.categoryNo === baseline.categoryNo))
      .map(({ categoryNo, updateCount }) => ({ categoryNo, updateCount })),
    details: details.map(({ scheduleDateNo, categoryNo, instructorEmployeeId, content, taskFlg, updateCount }) => ({
      scheduleDateNo, categoryNo, instructorEmployeeId, content, taskFlg, updateCount,
    })),
  })
  const clearChanges = () => {
    if (!canEdit || updating) return
    setScheduleName(schedule.scheduleName)
    setPermissions(schedule.permissions)
    setCategories(schedule.categories)
    setDetails(schedule.details)
    setPersonPick({ 1: '', 2: '' })
    setPermissionPick({ 1: false, 2: true })
    setNewCategory('')
    setUpdateError(''); setScheduleNameError(false)
    setSuccessMessage('')
  }
  const saveChanges = async () => {
    if (!canEdit || !persistedDirty || !permissionsValid || updating || updateInFlight.current) return
    if (!scheduleName.trim() || scheduleName.length > 40) {
      setScheduleNameError(true)
      setUpdateError('スケジュール名は必須、40文字以内で入力してください。')
      return
    }
    const categoryNames = categories.map((category) => category.categoryName.trim())
    if (new Set(categoryNames).size !== categoryNames.length) {
      setUpdateError('同じ分類名は登録できません')
      return
    }
    updateInFlight.current = true
    setUpdating(true)
    setUpdateError(''); setScheduleNameError(false)
    setSuccessMessage('')
    try {
      const csrf = await ensureCsrf()
      await updateSchedule(scheduleId, updateRequest(), csrf)
      const next = await getSchedule(scheduleId)
      setSchedule(next)
      setScheduleName(next.scheduleName)
      setPermissions(next.permissions)
      setCategories(next.categories)
      setDetails(next.details)
      setMonth((current) => current ?? monthOf(next.startDate))
      setPersonPick({ 1: '', 2: '' })
      setNewCategory('')
      setSuccessMessage('スケジュールを更新しました')
    } catch (failure) {
      const message = errorMessage(failure)
      if (message === '未提出以外の提出状況、またはコメントが登録されているため、課題を解除できません。') {
        setMessageDialog({ kind: 'error', title: '課題を解除できません', message })
      } else setUpdateError(message)
    } finally {
      updateInFlight.current = false
      setUpdating(false)
    }
  }

  const closeDeleteConfirmation = () => {
    if (deleting) return
    setDeleteConfirmation(false)
    window.setTimeout(() => deleteButton.current?.focus(), 0)
  }
  const confirmDelete = async () => {
    if (!canEdit || deleting || deleteInFlight.current) return
    deleteInFlight.current = true
    setDeleting(true)
    setUpdateError(''); setScheduleNameError(false)
    setSuccessMessage('')
    try {
      const csrf = await ensureCsrf()
      await deleteSchedule(scheduleId, schedule.scheduleUpdateCount, csrf)
      try {
        window.sessionStorage.setItem('schedule-deleted-message', 'スケジュールを削除しました')
      } catch { /* 保存領域が利用できなくても、削除済み画面から一覧へ戻す */ }
      navigateWithoutPrompt(() => router.push('/schedule'))
    } catch (failure) {
      setDeleteConfirmation(false)
      if (failure instanceof ApiClientError
          && failure.status === 400
          && failure.message === '課題提出状況が登録されているため、このスケジュールは削除できません。') {
        dialogTrigger.current = deleteButton.current
        setMessageDialog({ kind: 'error', title: '削除できません', message: failure.message })
      } else {
        setUpdateError(errorMessage(failure))
      }
    } finally {
      deleteInFlight.current = false
      setDeleting(false)
    }
  }

  return <div>
    <Link href="/schedule" className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />スケジュール一覧へ戻る</Link>
    <PageHeader
      id="SCD02"
      title={canEdit ? 'スケジュール編集' : 'スケジュール詳細'}
      description={canEdit ? '登録済みスケジュールの権限・分類・研修内容を編集します。' : '登録済みスケジュールの権限・分類・研修内容を参照します。'}
      actions={<>
        {canEdit && <>
          <button type="button" onClick={() => void saveChanges()} disabled={!persistedDirty || !permissionsValid || updating || deleting} className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground disabled:opacity-50"><Check className="size-4" />{updating ? '更新中…' : '更新'}</button>
          <button type="button" onClick={clearChanges} disabled={!dirty || updating || deleting} className="rounded-lg border border-border px-4 py-2.5 text-sm font-semibold disabled:opacity-50">クリア</button>
        </>}
        <button ref={deleteButton} type="button" onClick={() => setDeleteConfirmation(true)} disabled={!canEdit || updating || deleting} className="rounded-lg border border-destructive/40 px-4 py-2.5 text-sm font-semibold text-destructive disabled:opacity-50">削除</button>
      </>}
    />

    {successMessage && <div role="status" className="mb-4 rounded-lg border border-primary/30 bg-primary/[0.04] p-3 text-sm text-primary">{successMessage}</div>}
    {updateError && <div id="schedule-update-error" role="alert" className={`mb-4 rounded-lg border border-destructive/30 p-3 text-sm${scheduleNameError ? ' text-[#DC2626]' : ''}`}>{updateError}</div>}

    <div className="grid gap-5">
      <SectionCard icon={CalendarRange} title="基本情報" desc="スケジュールの識別情報と期間">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="space-y-1.5 text-sm font-medium">スケジュールID <span className="text-destructive">＊</span><input aria-label="スケジュールID" className={`${fieldClass} font-mono`} value={schedule.scheduleId} readOnly disabled /></label>
          <label className="space-y-1.5 text-sm font-medium">スケジュール名 <span className="text-destructive">＊</span><input aria-label="スケジュール名" aria-invalid={scheduleNameError} aria-describedby={scheduleNameError ? 'schedule-update-error' : undefined} className={`${fieldClass} aria-invalid:border-[#DC2626] aria-invalid:focus:border-[#DC2626]`} value={scheduleName} maxLength={40} readOnly={!canEdit} disabled={!canEdit || updating} onChange={(event) => { setScheduleName(event.target.value); setUpdateError(''); setScheduleNameError(false); setSuccessMessage('') }} /></label>
          <div className="space-y-1.5"><span className="text-sm font-medium">登録期間 <span className="text-destructive">＊</span></span><div className="flex items-center gap-2"><input aria-label="開始日" type="date" className={fieldClass} value={schedule.startDate} readOnly disabled /><span>〜</span><input aria-label="終了日" type="date" className={fieldClass} value={schedule.endDate} readOnly disabled /></div></div>
          <label className="space-y-1.5 text-sm font-medium">休日<span className="flex h-10 items-center gap-2.5 rounded-lg border border-border bg-background px-3 font-normal"><input aria-label="土・日・祝日を含まない" type="checkbox" checked={schedule.excludeHolidays} disabled className="size-4 accent-primary" />土・日・祝日を含まない</span></label>
        </div>
      </SectionCard>

      <SectionCard icon={Users2} title="編集権限" desc="講師・新人ごとに参照／編集権限を設定">
        {memberOptionsError && <div role="alert" className="mb-4 rounded-lg border border-destructive/30 p-3 text-sm">メンバー候補を取得できませんでした。画面を再読み込みしてください。</div>}
        <div className="grid gap-5 lg:grid-cols-2">{([{ label: '講師', syainKbn: 2 }, { label: '新人', syainKbn: 1 }] as const).map(({ label, syainKbn }) => {
          const permissions = permissionsByRole(syainKbn)
          return <div key={label} className="space-y-3"><h3 className="font-semibold text-[#054A99]">{label}</h3>
            <div className="grid grid-cols-[1fr_8rem_auto] gap-2"><select aria-label={`${label}を選択`} className={fieldClass} value={personPick[syainKbn]} disabled={!canEdit} onChange={(event) => setPersonPick((current) => ({ ...current, [syainKbn]: event.target.value }))}><option value="">{label}を選択</option>{candidates(syainKbn).map((member) => <option key={member.syainId} value={member.syainId}>{member.name}</option>)}</select><select aria-label={`${label}の権限`} className={fieldClass} value={permissionPick[syainKbn] ? '編集' : '参照のみ'} disabled={!canEdit} onChange={(event) => setPermissionPick((current) => ({ ...current, [syainKbn]: event.target.value === '編集' }))}><option>参照のみ</option><option>編集</option></select><button type="button" onClick={() => addPermission(syainKbn)} disabled={!canEdit || !personPick[syainKbn]} className="inline-flex min-w-[5.5rem] whitespace-nowrap items-center justify-center gap-1 rounded-lg bg-secondary px-3 text-sm disabled:opacity-50">追加</button></div>
            <div className="max-h-[17rem] overflow-y-auto rounded-lg border border-border"><table className="w-full text-sm"><thead><tr className="sticky top-0 bg-muted text-left text-xs text-muted-foreground"><th className="px-3 py-2">No</th><th className="px-3 py-2">社員名</th><th className="px-3 py-2">権限</th><th className="px-3 py-2" /></tr></thead><tbody>{permissions.map((permission, index) => {
              const isSelf = permission.employeeId === user?.syainId
              const isLastTrainee = permission.syainKbn === 1 && permissions.length === 1
              const isLastEditableInstructor = permission.syainKbn === 2 && permission.canEdit && editableInstructorCount === 1
              const progressInUse = permission.usedAsReviewer || permission.hasComments
              const reviewerLocked = permission.canEdit && permission.usedAsReviewer
              const requiredMessage = progressInUse ? undefined : isLastTrainee ? '新人は1人以上登録する必要があります。' : isLastEditableInstructor ? editableInstructorRequiredMessage : undefined
              const permissionMessage = reviewerLocked ? reviewerPermissionRequiredMessage : isLastEditableInstructor ? editableInstructorRequiredMessage : undefined
              return <tr key={permission.employeeId} className="border-t border-border"><td className="px-3 py-2">{index + 1}</td><td className="px-3 py-2"><div className="text-xs text-muted-foreground">{permission.nameKana ?? '—'}</div><div className="font-medium">{permission.name}</div><div className="font-mono text-xs text-muted-foreground">{permission.employeeId}</div></td><td className="px-3 py-2"><select aria-label={`${permission.employeeId}の権限`} title={permissionMessage} className={fieldClass} value={permission.canEdit ? '編集' : '参照のみ'} disabled={!canEdit || isSelf || reviewerLocked || isLastEditableInstructor} onChange={(event) => changePermission(permission.employeeId, event.target.value === '編集')}><option>参照のみ</option><option>編集</option></select></td><td className="px-3 py-2 text-right"><button type="button" aria-label={`${permission.employeeId}を削除`} title={requiredMessage} onClick={(event) => removePermission(permission.employeeId, event.currentTarget)} disabled={!canEdit || isSelf || (!progressInUse && (isLastTrainee || isLastEditableInstructor))} className="inline-flex whitespace-nowrap items-center gap-1 text-xs text-destructive disabled:opacity-50"><Trash2 className="size-3.5" />削除</button></td></tr>
            })}</tbody></table></div>
            {syainKbn === 2 && editableInstructorCount === 1 && <p className="text-xs text-muted-foreground">{editableInstructorRequiredMessage}</p>}
            {syainKbn === 1 && permissions.length === 1 && <p className="text-xs text-muted-foreground">新人は1人以上登録する必要があります。</p>}
          </div>
        })}</div>
        {!hasEditableInstructor && <p className="mt-3 text-sm text-destructive">{editableInstructorRequiredMessage}</p>}
      </SectionCard>

      <SectionCard icon={Tags} title="分類登録" desc={`研修の分類（最大99個 ・ 現在 ${categories.length}件）`}>
        <div className="mb-4 flex gap-2"><input aria-label="分類名" value={newCategory} readOnly={!canEdit} onChange={(event) => setNewCategory(event.target.value)} className={fieldClass} placeholder="分類名を入力（例：Java研修）" /><button type="button" onClick={addCategory} disabled={!canEdit || !newCategory.trim()} className="inline-flex min-w-[5.5rem] whitespace-nowrap items-center justify-center gap-1 rounded-lg bg-secondary px-4 text-sm disabled:opacity-50">追加</button></div>
        <div className="flex flex-wrap gap-2">{categories.map((category) => <span key={category.categoryNo} className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-sm"><input aria-label={`${category.categoryNo}の分類名`} className="w-32 bg-transparent outline-none read-only:cursor-default" value={category.categoryName} readOnly={!canEdit} onChange={(event) => renameCategory(category.categoryNo, event.target.value)} /><button type="button" aria-label={`${category.categoryNo}の分類を削除`} onClick={(event) => removeCategory(category.categoryNo, event.currentTarget)} disabled={!canEdit} className="disabled:opacity-50"><Trash2 className="size-3.5" /></button></span>)}</div>
      </SectionCard>

      <SectionCard icon={ListChecks} title="スケジュール内容" desc="月ごとに研修内容を設定">
        <div className="mb-3 flex items-center justify-center gap-4">
          <button type="button" aria-label="前月" onClick={() => setMonth((value) => value && shiftMonth(value, -1))} className="rounded-lg border p-2"><ChevronLeft className="size-4" /></button>
          <span className="font-semibold text-[#054A99]">{month?.year}年{month?.month}月</span>
          <button type="button" aria-label="次月" onClick={() => setMonth((value) => value && shiftMonth(value, 1))} className="rounded-lg border p-2"><ChevronRight className="size-4" /></button>
        </div>
        <div className="overflow-x-auto rounded-lg border"><table className="w-full min-w-[720px] text-sm"><thead><tr className="bg-muted text-left text-xs text-muted-foreground"><th className="px-4 py-2.5">日付</th><th className="px-4 py-2.5">分類<span className="ml-1 text-destructive" aria-label="必須">*</span></th><th className="px-4 py-2.5">講師<span className="ml-1 text-destructive" aria-label="必須">*</span></th><th className="px-4 py-2.5">内容</th><th className="px-4 py-2.5">課題</th></tr></thead><tbody>{rows.length === 0 ? <tr className="border-t"><td colSpan={5} className="px-4 py-5 text-center text-sm text-muted-foreground">この月のスケジュール内容はありません</td></tr> : rows.map((row) => {
          const date = dateLabel(row.scheduleDate)
          return <tr key={row.scheduleDateNo} className="border-t"><td className="px-4 py-2.5">{date.date} ({date.weekday})</td><td className="px-4 py-2.5"><select aria-label={`${row.scheduleDateNo}の分類`} className={fieldClass} value={String(row.categoryNo)} disabled={!canEdit} onChange={(event) => { const category = categories.find((value) => value.categoryNo === Number(event.target.value)); if (category) changeDetail(row.scheduleDateNo, { categoryNo: category.categoryNo, categoryName: category.categoryName }) }}>{categories.map((category) => <option key={category.categoryNo} value={category.categoryNo}>{category.categoryName}</option>)}</select></td><td className="px-4 py-2.5"><select aria-label={`${row.scheduleDateNo}の講師`} className={fieldClass} value={row.instructorEmployeeId} disabled={!canEdit} onChange={(event) => { const instructor = instructors.find((value) => value.employeeId === event.target.value); if (instructor) changeDetail(row.scheduleDateNo, { instructorEmployeeId: instructor.employeeId, instructorName: instructor.name }) }}>{instructors.map((instructor) => <option key={instructor.employeeId} value={instructor.employeeId}>{instructor.name}</option>)}</select></td><td className="px-4 py-2.5"><input aria-label={`${row.scheduleDateNo}の内容`} className={fieldClass} value={row.content ?? ''} readOnly={!canEdit} onChange={(event) => changeDetail(row.scheduleDateNo, { content: event.target.value })} /></td><td className="px-4 py-2.5 text-center"><input aria-label={`${row.scheduleDateNo}の課題`} type="checkbox" checked={row.taskFlg} disabled={!canEdit || !row.content?.trim()} onChange={(event) => changeDetail(row.scheduleDateNo, { taskFlg: event.target.checked })} className="size-4 accent-primary" /></td></tr>
        })}</tbody></table></div>
      </SectionCard>
    </div>
    {deleteConfirmation && <ConfirmDialog
      title="スケジュールを削除しますか？"
      message={`「${schedule.scheduleName}」を削除します。\nこの操作は取り消せません。`}
      confirmLabel={deleting ? '削除中…' : '削除'}
      pending={deleting}
      destructive
      onCancel={closeDeleteConfirmation}
      onConfirm={() => void confirmDelete()}
    />}
    {messageDialog && <MessageDialog kind={messageDialog.kind} title={messageDialog.title} message={messageDialog.message} onClose={closeMessageDialog} />}
  </div>
}
