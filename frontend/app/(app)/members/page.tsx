'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { UserPlus, Save, Trash2 } from 'lucide-react'
import { PageHeader } from '@/components/app-shell'
import { RoleBadge } from '@/components/status-badge'
import { useAuth } from '@/components/auth/auth-provider'
import { InstructorOnly } from '@/components/auth/instructor-only'
import { ApiClientError, errorMessage, type FieldError } from '@/lib/auth-api'
import { getMembers, getMember, createMember, updateMember, deleteMember, type Member } from '@/lib/member-api'

const fieldClass =
  'h-10 w-full rounded-lg border border-border bg-background px-3 text-sm outline-none focus:border-ring focus:ring-2 focus:ring-ring/20 aria-invalid:border-[#DC2626] aria-invalid:focus:border-[#DC2626]'
type Draft = { syainId: string; syainKbn: 1 | 2; name: string; nameKana: string }
const emptyDraft: Draft = { syainId: '', syainKbn: 1, name: '', nameKana: '' }
function initials(name: string) { return name.replace(/\s/g, '').slice(0, 1) }

export default function MembersPage() {
  return <InstructorOnly><MemberManagement /></InstructorOnly>
}

function MemberManagement() {
  const { ensureCsrf, user, refreshUser } = useAuth()
  const [members, setMembers] = useState<Member[]>([])
  const [selected, setSelected] = useState<Member | null>(null)
  const [draft, setDraft] = useState<Draft>(emptyDraft)
  const password = useRef<HTMLInputElement>(null)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([])
  const [success, setSuccess] = useState('')
  const [listError, setListError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [forbidden, setForbidden] = useState(false)
  const pending = useRef(false)
  const listVersion = useRef(0)
  const editing = selected !== null
  const selectedId = selected?.syainId

  const loadList = useCallback(async () => {
    const version = ++listVersion.current
    setLoading(true)
    setListError('')
    try {
      const rows = await getMembers()
      if (version === listVersion.current) setMembers(rows)
    } catch (failure) {
      if (version !== listVersion.current) return
      setMembers([])
      setListError(errorMessage(failure))
      if (failure instanceof ApiClientError && failure.status === 403) setForbidden(true)
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])
  useEffect(() => {
    void loadList()
    return () => { listVersion.current += 1 }
  }, [loadList])

  function clearForm() {
    setSelected(null)
    setDraft(emptyDraft)
    setFieldErrors([])
    if (password.current) password.current.value = ''
  }
  function beginNew() {
    if (pending.current || loading) return
    clearForm()
    setError('')
    setSuccess('')
  }
  async function handleFailure(failure: unknown) {
    setError(errorMessage(failure))
    if (failure instanceof ApiClientError) {
      setFieldErrors(failure.errors)
      if (failure.status === 403) { setForbidden(true); clearForm() }
      if (failure.code === 'MEMBER_UPDATE_CONFLICT' || failure.code === 'MEMBER_NOT_FOUND') {
        clearForm()
        await loadList()
      }
    }
  }
  async function selectMember(member: Member) {
    if (pending.current || loading) return
    pending.current = true
    setBusy(true)
    clearForm()
    setError('')
    setSuccess('')
    try {
      const latest = await getMember(member.syainId)
      setSelected(latest)
      setDraft({ syainId: latest.syainId, syainKbn: latest.syainKbn, name: latest.name, nameKana: latest.nameKana ?? '' })
    } catch (failure) { await handleFailure(failure) }
    finally { pending.current = false; setBusy(false) }
  }
  async function submit() {
    if (pending.current || loading) return
    pending.current = true
    setBusy(true)
    setError('')
    setSuccess('')
    setFieldErrors([])
    // 入力値は送信用の一時変数だけ。待機中・失敗後も入力欄に残さない。
    const transient = { password: password.current?.value ?? '' }
    const successMessage = selected
      ? `${selected.name}(${selected.syainId})を更新しました`
      : `${draft.name}(${draft.syainId})を登録しました`
    const reauthenticate = selected?.syainId === user?.syainId && (selected?.syainKbn !== draft.syainKbn || transient.password.trim().length > 0)
    if (password.current) password.current.value = ''
    try {
      const csrf = await ensureCsrf()
      const profile = { syainKbn: draft.syainKbn, name: draft.name, nameKana: draft.nameKana || null, password: transient.password }
      const result = selected
        ? updateMember(selected.syainId, { ...profile, updateCount: selected.updateCount }, csrf)
        : createMember({ ...profile, syainId: draft.syainId }, csrf)
      transient.password = ''
      profile.password = ''
      await result
      if (reauthenticate) {
        await refreshUser()
        return
      }
      setSuccess(successMessage)
      clearForm()
      await loadList()
    } catch (failure) { await handleFailure(failure) }
    finally { transient.password = ''; pending.current = false; setBusy(false) }
  }
  async function remove() {
    if (!selected || pending.current || loading) return
    if (!window.confirm(`社員ID: ${selected.syainId}\n氏名: ${selected.name}\nこのメンバーを削除しますか？`)) return
    pending.current = true
    setBusy(true)
    setError('')
    setSuccess('')
    setFieldErrors([])
    const successMessage = `${selected.name}(${selected.syainId})を削除しました`
    if (password.current) password.current.value = ''
    try {
      await deleteMember(selected.syainId, await ensureCsrf())
      setSuccess(successMessage)
      clearForm()
      await loadList()
    } catch (failure) { await handleFailure(failure) }
    finally { pending.current = false; setBusy(false) }
  }
  function fieldError(field: string) {
    const messages = fieldErrors.filter((issue) => issue.field === field)
    return messages.length ? <span id={`${field}-error`} className="block text-xs text-destructive">{messages.map((issue) => issue.message).join('。')}</span> : null
  }
  function validationProps(field: string) {
    return { 'aria-invalid': fieldErrors.some((issue) => issue.field === field), 'aria-describedby': `${field}-error` }
  }
  if (forbidden) return <p role="alert" className="rounded-xl border border-destructive/20 bg-card p-6 text-center">この操作を行う権限がありません</p>

  return (
    <div>
      <PageHeader
        id="MMB01"
        title="メンバ登録"
        description="研修メンバの一覧です。行をクリックすると、そのメンバの情報が登録エリアに反映されます。"
      />

      {success && <p role="status" className="mb-4 rounded-lg border border-primary/20 bg-primary/5 p-3 text-sm">{success}</p>}
      {listError && <div role="alert" className="mb-4 rounded-lg border border-destructive/30 p-3">{listError}<button disabled={busy || loading} onClick={() => void loadList()} className="ml-3 text-primary underline">一覧を再取得</button></div>}
      {loading && <p role="status" className="mb-3 text-sm text-muted-foreground">メンバーを読み込んでいます…</p>}
      {busy && <p role="status" className="mb-3 text-sm text-muted-foreground">処理中…</p>}
      <div className="grid gap-5 lg:grid-cols-[1fr_20rem]">
        <section className="overflow-hidden rounded-xl border border-border bg-card lg:max-h-[34rem]">
          <div className="flex items-center justify-between border-b border-border bg-[#86D2FD] px-5 py-3.5">
            <h2 className="text-sm font-bold text-[#054A99]">研修メンバ一覧</h2>
            <div className="flex items-center gap-3">
              <button type="button" disabled={busy || loading} onClick={beginNew} className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-primary px-3 text-xs font-semibold text-primary-foreground hover:bg-primary/90">
                <UserPlus className="size-3.5" />新規登録
              </button>
              <span className="font-mono text-xs text-muted-foreground">{members.length} 名</span>
            </div>
          </div>
          <div className="max-h-[29rem] overflow-auto">
            <table className="w-full min-w-[560px] text-sm">
              <thead><tr className="border-b border-border text-left text-xs text-muted-foreground"><th className="w-12 px-5 py-3 font-medium">No</th><th className="px-4 py-3 font-medium">社員ID</th><th className="px-4 py-3 font-medium">社員名（ふりがな）</th><th className="px-4 py-3 font-medium">区分</th></tr></thead>
              <tbody>
                {!loading && !listError && members.length === 0 && <tr><td colSpan={4} className="p-6 text-center text-muted-foreground">メンバーは登録されていません</td></tr>}
                {members.map((m, index) => {
                  const active = selectedId === m.syainId
                  return (
                    <tr key={m.syainId} tabIndex={busy || loading ? -1 : 0} aria-selected={active} onClick={() => void selectMember(m)} onKeyDown={(event) => { if (event.key === 'Enter') void selectMember(m) }} className={`cursor-pointer border-b border-border/70 last:border-0 transition-colors hover:bg-muted/50 ${active ? 'bg-primary/[0.08] ring-1 ring-inset ring-primary/20' : ''}`}>
                      <td className="px-5 py-3 font-mono text-muted-foreground">{index + 1}</td>
                      <td className="px-4 py-3 font-mono">{m.syainId}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2.5">
                          <span className="flex size-7 items-center justify-center rounded-full bg-accent text-xs font-bold text-primary">{initials(m.name)}</span>
                          <span><span className="block text-xs text-muted-foreground">{m.nameKana}</span><span className="block font-medium">{m.name}</span></span>
                        </div>
                      </td>
                      <td className="px-4 py-3"><RoleBadge role={m.syainKbn === 2 ? '講師' : '新人'} /></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </section>

        <aside className="lg:sticky lg:top-20 lg:self-start">
          <div className="overflow-hidden rounded-xl border border-border bg-card">
            <div className="flex items-center gap-2 border-b border-border bg-[#86D2FD] px-5 py-4"><span className="flex size-8 items-center justify-center rounded-lg bg-accent text-primary"><UserPlus className="size-4" /></span><h2 className="text-sm font-bold text-[#054A99]">登録エリア</h2></div>
            <form className="space-y-4 p-5" onSubmit={(e) => { e.preventDefault(); void submit() }}>
              {error && <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-xs text-destructive">{error}</div>}
              <fieldset disabled={busy || loading} className="space-y-4">
              <label className="block space-y-1.5 text-sm font-medium">社員ID <span className="text-destructive">＊</span><input name="syainId" {...validationProps('syainId')} className={`${fieldClass} font-mono disabled:bg-muted`} value={draft.syainId} onChange={(e)=>setDraft((d)=>({...d,syainId:e.target.value}))} disabled={editing} maxLength={10} placeholder="00000" />{fieldError('syainId')}</label>
              <label className="block space-y-1.5 text-sm font-medium">社員名 <span className="text-destructive">＊</span><input name="name" {...validationProps('name')} className={fieldClass} value={draft.name} onChange={(e)=>setDraft((d)=>({...d,name:e.target.value}))} maxLength={50} placeholder="山田 太郎" />{fieldError('name')}</label>
              <label className="block space-y-1.5 text-sm font-medium">社員名ふりがな<input name="nameKana" {...validationProps('nameKana')} className={fieldClass} value={draft.nameKana} onChange={(e)=>setDraft((d)=>({...d,nameKana:e.target.value}))} maxLength={100} placeholder="やまだ たろう" />{fieldError('nameKana')}</label>
              <label className="block space-y-1.5 text-sm font-medium">区分 <span className="text-destructive">＊</span><select name="syainKbn" {...validationProps('syainKbn')} className={fieldClass} value={draft.syainKbn} onChange={(e)=>setDraft((d)=>({...d,syainKbn:Number(e.target.value) as 1 | 2}))}><option value={1}>新人</option><option value={2}>講師</option></select>{fieldError('syainKbn')}</label>
              <label className="block space-y-1.5 text-sm font-medium">パスワード {editing ? <span className="text-xs font-normal text-muted-foreground">（変更する場合のみ入力）</span> : <span className="text-destructive">＊</span>}<input name="password" {...validationProps('password')} ref={password} type="password" className={fieldClass} autoComplete="new-password" placeholder={editing ? '変更する場合のみ入力' : 'パスワードを入力'} />{fieldError('password')}</label>
              {fieldError('updateCount')}
              </fieldset>
              <div className="grid grid-cols-2 gap-2">
                <button type="submit" disabled={busy || loading} className="flex h-10 items-center justify-center gap-1.5 rounded-lg bg-primary text-sm font-semibold text-primary-foreground hover:bg-primary/90"><Save className="size-4" />{editing ? '更新' : '登録'}</button>
                <button type="button" disabled={!editing || busy || loading} onClick={remove} className="flex h-10 items-center justify-center gap-1.5 rounded-lg border border-destructive/40 bg-background text-sm font-semibold text-destructive transition-colors hover:bg-destructive/5 disabled:cursor-not-allowed disabled:border-border disabled:text-muted-foreground disabled:opacity-50"><Trash2 className="size-4" />削除</button>
              </div>
            </form>
          </div>
        </aside>
      </div>
    </div>
  )
}
