import { ApiClientError, requestApi, type CsrfResponse } from './auth-api'

export type ProgressFilters = {
  scheduleId?: string
  categoryNo?: number
  submitter?: string
  from?: string
  to?: string
  reviewerEmployeeId?: string
  statusCode?: number
  scheduleDateNo?: number
  traineeEmployeeId?: string
}

export type ProgressCategory = { categoryNo: number; categoryName: string }
export type ProgressSchedule = {
  scheduleId: string
  scheduleName: string
  canEdit: boolean
  categories: ProgressCategory[]
  reviewers: ProgressMember[]
}
export type ProgressMember = { employeeId: string; name: string }
export type ProgressStatus = { statusCode: number; statusName: string }
export type ProgressComment = {
  commentNo: number
  authorEmployeeId: string | null
  authorName: string | null
  content: string
  updatedAt: string
  updateCount: number
}
export type ProgressItem = {
  scheduleId: string
  scheduleName: string
  scheduleDateNo: number
  scheduleDate: string
  categoryNo: number
  categoryName: string
  content: string
  assignedInstructorEmployeeId: string
  assignedInstructorName: string
  traineeEmployeeId: string
  traineeName: string
  reviewerEmployeeId: string | null
  reviewerName: string | null
  statusCode: number
  statusName: string
  completedDate: string | null
  canEdit: boolean
  canComment: boolean
  submissionUpdateCount: number | null
  comments: ProgressComment[]
}
export type ProgressData = {
  businessDate: string
  viewerRole: 'TRAINEE' | 'INSTRUCTOR'
  schedules: ProgressSchedule[]
  statuses: ProgressStatus[]
  items: ProgressItem[]
}
export type SubmissionUpdate = {
  reviewerEmployeeId: string | null
  reviewerName: string | null
  statusCode: number
  statusName: string
  completedDate: string | null
  updateCount: number
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

function isNullableNumber(value: unknown): value is number | null {
  return value === null || typeof value === 'number'
}

function isCategory(value: unknown): value is ProgressCategory {
  return isRecord(value) && typeof value.categoryNo === 'number' && typeof value.categoryName === 'string'
}

function isMember(value: unknown): value is ProgressMember {
  return isRecord(value) && typeof value.employeeId === 'string' && typeof value.name === 'string'
}

function isSchedule(value: unknown): value is ProgressSchedule {
  return isRecord(value)
    && typeof value.scheduleId === 'string'
    && typeof value.scheduleName === 'string'
    && typeof value.canEdit === 'boolean'
    && Array.isArray(value.categories) && value.categories.every(isCategory)
    && Array.isArray(value.reviewers) && value.reviewers.every(isMember)
}

function isStatus(value: unknown): value is ProgressStatus {
  return isRecord(value) && typeof value.statusCode === 'number'
    && typeof value.statusName === 'string'
}

function isComment(value: unknown): value is ProgressComment {
  return isRecord(value)
    && typeof value.commentNo === 'number'
    && isNullableString(value.authorEmployeeId)
    && isNullableString(value.authorName)
    && typeof value.content === 'string'
    && typeof value.updatedAt === 'string'
    && typeof value.updateCount === 'number'
}

function isItem(value: unknown): value is ProgressItem {
  return isRecord(value)
    && typeof value.scheduleId === 'string'
    && typeof value.scheduleName === 'string'
    && typeof value.scheduleDateNo === 'number'
    && typeof value.scheduleDate === 'string'
    && typeof value.categoryNo === 'number'
    && typeof value.categoryName === 'string'
    && typeof value.content === 'string'
    && typeof value.assignedInstructorEmployeeId === 'string'
    && typeof value.assignedInstructorName === 'string'
    && typeof value.traineeEmployeeId === 'string'
    && typeof value.traineeName === 'string'
    && isNullableString(value.reviewerEmployeeId)
    && isNullableString(value.reviewerName)
    && typeof value.statusCode === 'number'
    && typeof value.statusName === 'string'
    && isNullableString(value.completedDate)
    && typeof value.canEdit === 'boolean'
    && typeof value.canComment === 'boolean'
    && isNullableNumber(value.submissionUpdateCount)
    && Array.isArray(value.comments) && value.comments.every(isComment)
}

function parseProgressData(value: unknown): ProgressData {
  if (!isRecord(value)
      || typeof value.businessDate !== 'string'
      || (value.viewerRole !== 'TRAINEE' && value.viewerRole !== 'INSTRUCTOR')
      || !Array.isArray(value.schedules) || !value.schedules.every(isSchedule)
      || !Array.isArray(value.statuses) || !value.statuses.every(isStatus)
      || !Array.isArray(value.items) || !value.items.every(isItem)) {
    throw new ApiClientError(0, 'INVALID_RESPONSE', 'サーバーからの応答を確認できませんでした')
  }
  const scheduleIds = new Set(value.schedules.map((schedule) => schedule.scheduleId))
  if (value.items.some((item) => !scheduleIds.has(item.scheduleId))) {
    throw new ApiClientError(0, 'INVALID_RESPONSE', 'サーバーからの応答を確認できませんでした')
  }
  return value as ProgressData
}

function queryString(filters: ProgressFilters) {
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  return params.toString()
}

function itemPath(item: Pick<ProgressItem, 'scheduleId' | 'scheduleDateNo' | 'traineeEmployeeId'>) {
  return `/api/progress/${encodeURIComponent(item.scheduleId)}/${item.scheduleDateNo}/trainees/${encodeURIComponent(item.traineeEmployeeId)}`
}

export async function getProgress(filters: ProgressFilters = {}): Promise<ProgressData> {
  const query = queryString(filters)
  const response = await requestApi<unknown>(`/api/progress${query ? `?${query}` : ''}`)
  return parseProgressData(response)
}

export function updateProgressSubmission(item: ProgressItem, reviewerEmployeeId: string | null,
  statusCode: number, csrf: CsrfResponse): Promise<SubmissionUpdate> {
  return requestApi<SubmissionUpdate>(itemPath(item), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token },
    body: JSON.stringify({ reviewerEmployeeId, statusCode, updateCount: item.submissionUpdateCount }),
  })
}

export function addProgressComment(item: ProgressItem, content: string,
  csrf: CsrfResponse): Promise<ProgressComment> {
  return requestApi<ProgressComment>(`${itemPath(item)}/comments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token },
    body: JSON.stringify({ content }),
  })
}

export function updateProgressComment(item: ProgressItem, comment: ProgressComment, content: string,
  csrf: CsrfResponse): Promise<ProgressComment> {
  return requestApi<ProgressComment>(`${itemPath(item)}/comments/${comment.commentNo}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token },
    body: JSON.stringify({ content, updateCount: comment.updateCount }),
  })
}

export function deleteProgressComment(item: ProgressItem, comment: ProgressComment,
  csrf: CsrfResponse): Promise<void> {
  const query = new URLSearchParams({ updateCount: String(comment.updateCount) })
  return requestApi<void>(`${itemPath(item)}/comments/${comment.commentNo}?${query}`, {
    method: 'DELETE',
    headers: { 'X-CSRF-TOKEN': csrf.token },
  })
}
