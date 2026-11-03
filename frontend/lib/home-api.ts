import { requestApi } from './auth-api'
import type { TaskStatus } from './task-status'

export type HomeTaskSummary = {
  unsubmitted: number
  correctionRequired: number
  inReview: number
  completed: number
}

export type HomeScheduleRow = {
  scheduleDateNo: number
  scheduleDate: string
  categoryNo: number
  categoryName: string
  content: string | null
  instructorName: string
  taskFlg: boolean
  taskStatusCode: number | null
  taskStatusName: TaskStatus | null
  completedCount: number | null
  targetTraineeCount: number | null
}

export type HomeSchedule = {
  scheduleId: string
  scheduleName: string
  startDate: string
  endDate: string
  canEdit: boolean
  pinned: boolean
  details: HomeScheduleRow[]
}

export type HomeDashboard = {
  businessDate: string
  viewerRole: 'TRAINEE' | 'INSTRUCTOR'
  taskSummary: HomeTaskSummary
  schedules: HomeSchedule[]
}

export async function getHomeDashboard(): Promise<HomeDashboard> {
  return requestApi<HomeDashboard>('/api/home')
}
