'use client'

import { useEffect, useRef } from 'react'

export type MessageKind = 'error' | 'warning' | 'confirmation' | 'information' | 'success'

const titleColors: Partial<Record<MessageKind, string>> = {
  error: 'text-[#DC2626]',
  warning: 'text-[#D97706]',
  confirmation: 'text-[#054A99]',
  information: 'text-[#054A99]',
}

function titleClassName(kind?: MessageKind) {
  return `text-base font-bold ${kind ? titleColors[kind] ?? 'text-[#054A99]' : 'text-[#054A99]'}`
}

export function MessageDialog({ title, message, kind, onClose }: {
  title: string
  message: string
  kind?: MessageKind
  onClose: () => void
}) {
  const okButton = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    okButton.current?.focus()
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
      } else if (event.key === 'Tab') {
        event.preventDefault()
        okButton.current?.focus()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div role="dialog" aria-modal="true" aria-labelledby="message-dialog-title" aria-describedby="message-dialog-description" className="w-full max-w-md rounded-xl border border-border bg-card p-5 shadow-xl">
      <h2 id="message-dialog-title" className={titleClassName(kind)}>{title}</h2>
      <p id="message-dialog-description" className="mt-3 whitespace-pre-line text-sm leading-6 text-foreground">{message}</p>
      <div className="mt-5 flex justify-end"><button ref={okButton} type="button" onClick={onClose} className="rounded-lg bg-primary px-5 py-2 text-sm font-semibold text-primary-foreground">OK</button></div>
    </div>
  </div>
}

export function ConfirmDialog({ title, message, kind = 'confirmation', confirmLabel, cancelLabel = 'キャンセル', pending = false, destructive = false, onConfirm, onCancel }: {
  title: string
  message: string
  kind?: MessageKind
  confirmLabel: string
  cancelLabel?: string
  pending?: boolean
  destructive?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  const cancelButton = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    cancelButton.current?.focus()
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !pending) { event.preventDefault(); onCancel() }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onCancel, pending])
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"><div role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title" aria-describedby="confirm-dialog-description" className="w-full max-w-md rounded-xl border border-border bg-card p-5 shadow-xl"><h2 id="confirm-dialog-title" className={titleClassName(kind)}>{title}</h2><p id="confirm-dialog-description" className="mt-3 whitespace-pre-line text-sm leading-6">{message}</p><div className="mt-5 flex justify-end gap-2"><button ref={cancelButton} type="button" onClick={onCancel} disabled={pending} className="rounded-lg border border-border px-5 py-2 text-sm font-semibold disabled:opacity-50">{cancelLabel}</button><button type="button" onClick={onConfirm} disabled={pending} className={`rounded-lg px-5 py-2 text-sm font-semibold text-white disabled:opacity-50 ${destructive ? 'bg-destructive' : 'bg-primary'}`}>{confirmLabel}</button></div></div></div>
}
