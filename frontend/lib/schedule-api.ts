import { ApiClientError, requestApi, type CsrfResponse } from './auth-api'

export type ScheduleListItem = {
  scheduleId: string
  scheduleName: string
  startDate: string
  endDate: string
  canEdit: boolean
  pinned: boolean
  scheduleUpdateCount: number
  permissionUpdateCount: number
}

export type SchedulePermission = {
  employeeId: string
  name: string
  nameKana: string | null
  syainKbn: 1 | 2
  canEdit: boolean
  pinned: boolean
  updateCount: number
  usedAsReviewer: boolean
  hasComments: boolean
}

export type ScheduleMemberOption = {
  syainId: string
  syainKbn: 1 | 2
  name: string
  nameKana: string | null
}

export type ScheduleCategory = {
  categoryNo: number
  categoryName: string
  updateCount: number
}

export type ScheduleContentDetail = {
  scheduleDateNo: number
  scheduleDate: string
  categoryNo: number
  categoryName: string
  instructorEmployeeId: string
  instructorName: string
  content: string | null
  taskFlg: boolean
  taskRemovalBlocked: boolean
  updateCount: number
}

export type ScheduleDetail = {
  scheduleId: string
  scheduleName: string
  startDate: string
  endDate: string
  excludeHolidays: boolean
  canEdit: boolean
  pinned: boolean
  scheduleUpdateCount: number
  permissionUpdateCount: number
  permissions: SchedulePermission[]
  categories: ScheduleCategory[]
  details: ScheduleContentDetail[]
}

export type ScheduleUpdateRequest = {
  scheduleName: string
  scheduleUpdateCount: number
  permissions: Array<{ employeeId: string; canEdit: boolean; updateCount: number }>
  deletedPermissions: Array<{ employeeId: string; updateCount: number }>
  categories: Array<{ categoryNo: number; categoryName: string; updateCount: number }>
  deletedCategories: Array<{ categoryNo: number; updateCount: number }>
  details: Array<{
    scheduleDateNo: number
    categoryNo: number
    instructorEmployeeId: string
    content: string | null
    taskFlg: boolean
    updateCount: number
  }>
}

export type ScheduleCreateRequest = {
  scheduleId: string
  scheduleName: string
  startDate: string
  endDate: string
  excludeHolidays: boolean
  permissions: Array<{ employeeId: string; canEdit: boolean }>
  categories: Array<{ categoryNo: number; categoryName: string }>
  details: Array<{
    scheduleDateNo: number
    scheduleDate: string
    categoryNo: number
    instructorEmployeeId: string
    content: string | null
    taskFlg: boolean
  }>
}

export type SchedulePinUpdateResponse = {
  scheduleId: string
  pinned: boolean
  permissionUpdateCount: number
}

function scheduleDto(schedule: ScheduleListItem): ScheduleListItem {
  return {
    scheduleId: schedule.scheduleId,
    scheduleName: schedule.scheduleName,
    startDate: schedule.startDate,
    endDate: schedule.endDate,
    canEdit: schedule.canEdit,
    pinned: schedule.pinned,
    scheduleUpdateCount: schedule.scheduleUpdateCount,
    permissionUpdateCount: schedule.permissionUpdateCount,
  }
}

export async function getSchedules(): Promise<ScheduleListItem[]> {
  return (await requestApi<ScheduleListItem[]>('/api/schedules')).map(scheduleDto)
}

export async function getSchedule(scheduleId: string): Promise<ScheduleDetail> {
  return scheduleDetailDto(await requestApi<ScheduleDetail>(`/api/schedules/${encodeURIComponent(scheduleId)}`))
}

function scheduleDetailDto(schedule: ScheduleDetail): ScheduleDetail {
  if (!schedule || !Array.isArray(schedule.details)
      || schedule.details.some((detail) => typeof detail.taskRemovalBlocked !== 'boolean')
      || !Array.isArray(schedule.permissions)
      || schedule.permissions.some((permission) => typeof permission.usedAsReviewer !== 'boolean'
        || typeof permission.hasComments !== 'boolean')) {
    throw new ApiClientError(0, 'INVALID_RESPONSE', 'サーバーからの応答を確認できませんでした')
  }
  return schedule
}

export function getScheduleMemberOptions(): Promise<ScheduleMemberOption[]> {
  return requestApi<ScheduleMemberOption[]>('/api/schedules/member-options')
}

export async function getNonWorkingDates(startDate: string, endDate: string): Promise<string[]> {
  const query = new URLSearchParams({ startDate, endDate })
  const response = await requestApi<{ dates: string[] }>(`/api/schedules/non-working-dates?${query}`)
  return response.dates
}

export async function updateSchedule(
  scheduleId: string,
  request: ScheduleUpdateRequest,
  csrf: CsrfResponse,
): Promise<ScheduleDetail> {
  return scheduleDetailDto(await requestApi<ScheduleDetail>(`/api/schedules/${encodeURIComponent(scheduleId)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token },
    body: JSON.stringify(request),
  }))
}

export async function updateSchedulePin(
  scheduleId: string,
  pinned: boolean,
  permissionUpdateCount: number,
  csrf: CsrfResponse,
): Promise<SchedulePinUpdateResponse> {
  return requestApi<SchedulePinUpdateResponse>(`/api/schedules/${encodeURIComponent(scheduleId)}/pin`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token },
    body: JSON.stringify({ pinned, permissionUpdateCount }),
  })
}

export async function deleteSchedule(
  scheduleId: string,
  scheduleUpdateCount: number,
  csrf: CsrfResponse,
): Promise<void> {
  const query = new URLSearchParams({ scheduleUpdateCount: String(scheduleUpdateCount) })
  await requestApi<void>(`/api/schedules/${encodeURIComponent(scheduleId)}?${query}`, {
    method: 'DELETE',
    headers: { 'X-CSRF-TOKEN': csrf.token },
  })
}

export async function createSchedule(request: ScheduleCreateRequest, csrf: CsrfResponse): Promise<ScheduleDetail> {
  return scheduleDetailDto(await requestApi<ScheduleDetail>('/api/schedules', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token },
    body: JSON.stringify(request),
  }))
}
