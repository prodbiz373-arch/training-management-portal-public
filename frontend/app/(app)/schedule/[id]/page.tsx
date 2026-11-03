import { ScheduleEditor } from '@/components/schedule-editor'
import { ScheduleDetail } from '@/components/schedule-detail'

export default async function ScheduleEditPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  const isNew = id === 'new'
  return isNew ? <ScheduleEditor /> : <ScheduleDetail scheduleId={id} />
}
