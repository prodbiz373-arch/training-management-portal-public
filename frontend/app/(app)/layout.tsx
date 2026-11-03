import { RequireAuth } from '@/components/auth/require-auth'
import { AppShell } from '@/components/app-shell'
import { UnsavedChangesProvider } from '@/components/unsaved-changes-guard'

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return <RequireAuth><UnsavedChangesProvider><AppShell>{children}</AppShell></UnsavedChangesProvider></RequireAuth>
}
