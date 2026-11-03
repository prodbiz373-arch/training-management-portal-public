import { cn } from '@/lib/utils'

type Tone = 'success' | 'warning' | 'info' | 'danger' | 'neutral'

const statusTone: Record<string, Tone> = {
  完了: 'success',
  '完了（コメント有）': 'success',
  提出済: 'info',
  GPレビュー中: 'warning',
  講師レビュー中: 'warning',
  修正待: 'danger',
  未提出: 'neutral',
}

const toneClass: Record<Tone, string> = {
  success: 'bg-success text-success-foreground ring-success-foreground/15',
  warning: 'bg-warning text-warning-foreground ring-warning-foreground/15',
  info: 'bg-info text-info-foreground ring-info-foreground/15',
  danger: 'bg-destructive/12 text-destructive ring-destructive/20',
  neutral: 'bg-muted text-muted-foreground ring-border',
}

const dotClass: Record<Tone, string> = {
  success: 'bg-success-foreground',
  warning: 'bg-warning-foreground',
  info: 'bg-info-foreground',
  danger: 'bg-destructive',
  neutral: 'bg-muted-foreground/50',
}

export function StatusBadge({ status, className }: { status: string; className?: string }) {
  const tone = statusTone[status] ?? 'neutral'
  const label = status
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset',
        toneClass[tone],
        className,
      )}
    >
      <span className={cn('size-1.5 rounded-full', dotClass[tone])} aria-hidden />
      {label}
    </span>
  )
}

export function RoleBadge({ role }: { role: '講師' | '新人' }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-md px-3 py-1 text-sm font-semibold ring-1 ring-inset',
        role === '講師'
          ? 'bg-accent text-accent-foreground ring-primary/20'
          : 'bg-muted text-muted-foreground ring-border',
      )}
    >
      {role}
    </span>
  )
}
