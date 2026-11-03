import type { TaskStatus } from '@/lib/task-status'

export type { TaskStatus } from '@/lib/task-status'

export type ScheduleItem = {
  date: string
  day: string
  category: string
  content: string
  instructor: string
  taskFlg: boolean
  taskStatus?: TaskStatus
}

export type Member = {
  no: number
  employeeId: string
  name: string
  furigana: string
  role: '講師' | '新人'
}

export type ScheduleSummary = {
  no: number
  scheduleId: string
  name: string
  period: string
  permission: '参照のみ' | '編集'
}

export const currentUser = {
  name: '荒井 美波',
  role: '講師' as '講師' | '新人',
  employeeId: '00214',
}

export const homeSchedule: ScheduleItem[] = [
  { date: '4/1', day: '月', category: 'Java研修', content: 'チャプター１', instructor: '坂本 太郎', taskFlg: true, taskStatus: '完了' },
  { date: '4/2', day: '火', category: 'ビジネスマナー', content: '名刺交換', instructor: '坂本 太郎', taskFlg: true, taskStatus: '提出済' },
  { date: '4/3', day: '水', category: 'Java研修', content: 'チャプター２', instructor: '吉田 浩太', taskFlg: true, taskStatus: '未提出' },
  { date: '4/4', day: '木', category: 'ビジネスマナー', content: 'ジョハリの窓', instructor: '坂本 太郎', taskFlg: true, taskStatus: '修正待' },
  { date: '4/5', day: '金', category: 'Java研修', content: 'チャプター２', instructor: '坂本 太郎', taskFlg: true, taskStatus: '講師レビュー中' },
  { date: '4/8', day: '月', category: 'Java研修', content: 'チャプター３', instructor: '坂本 太郎', taskFlg: true, taskStatus: '提出済' },
  { date: '4/9', day: '火', category: 'Java研修', content: 'チャプター３', instructor: '坂本 太郎', taskFlg: true, taskStatus: '修正待' },
  { date: '4/10', day: '水', category: 'Java研修', content: 'チャプター４', instructor: '坂本 太郎', taskFlg: true, taskStatus: '未提出' },
  { date: '4/11', day: '木', category: 'Java研修', content: 'チャプター４', instructor: '坂本 太郎', taskFlg: false },
  { date: '4/12', day: '金', category: 'Java研修', content: 'チャプター５', instructor: '坂本 太郎', taskFlg: true, taskStatus: '完了（コメント有）' },
  { date: '4/15', day: '月', category: 'Java研修', content: 'チャプター６', instructor: '坂本 太郎', taskFlg: false },
  { date: '4/16', day: '火', category: 'Java研修', content: 'チャプター６', instructor: '坂本 太郎', taskFlg: false },
  { date: '4/17', day: '水', category: 'Java研修', content: 'チャプター７', instructor: '坂本 太郎', taskFlg: false },
]

export const members: Member[] = [
  { no: 1, employeeId: '00214', name: '荒井 美波', furigana: 'あらい みなみ', role: '講師' },
  { no: 2, employeeId: '00312', name: '田中 太郎', furigana: 'たなか たろう', role: '新人' },
  { no: 3, employeeId: '00318', name: '坂本 太郎', furigana: 'さかもと たろう', role: '講師' },
  { no: 4, employeeId: '00401', name: '山本 翔', furigana: 'やまもと しょう', role: '新人' },
  { no: 5, employeeId: '00402', name: '斉藤 空', furigana: 'さいとう そら', role: '新人' },
  { no: 6, employeeId: '00403', name: '飯島 寛太', furigana: 'いいじま かんた', role: '新人' },
  { no: 7, employeeId: '00320', name: '吉田 浩太', furigana: 'よしだ こうた', role: '講師' },
]


export const scheduleTraineeIds: Record<string, string[]> = {
  '001': ['00312', '00401', '00402', '00403'],
  '002': ['00401', '00402', '00403'],
  '003': ['00312', '00401'],
}

export const schedules: ScheduleSummary[] = [
  { no: 1, scheduleId: '001', name: '全体スケジュール', period: '2026/04/01〜2026/05/22', permission: '編集' },
  { no: 2, scheduleId: '002', name: 'Java研修 集中コース', period: '2026/04/01〜2026/04/26', permission: '編集' },
  { no: 3, scheduleId: '003', name: 'ビジネスマナー研修', period: '2026/04/02〜2026/04/18', permission: '参照のみ' },
]

export const editPermissions = [
  { no: 1, name: '坂本 太郎', furigana: 'さかもと たろう', role: '講師' as const, permission: '編集' },
  { no: 2, name: '山本 翔', furigana: 'やまもと しょう', role: '新人' as const, permission: '参照のみ' },
  { no: 3, name: '斉藤 空', furigana: 'さいとう そら', role: '新人' as const, permission: '参照のみ' },
]

export const categories = [
  { no: 1, name: 'Java研修' },
  { no: 2, name: 'ビジネスマナー' },
]

export const scheduleContent: ScheduleItem[] = [
  { date: '4/1', day: '月', category: 'Java研修', content: 'チャプター１', instructor: '坂本 太郎', taskFlg: true, taskStatus: '提出済' },
  { date: '4/2', day: '火', category: 'ビジネスマナー', content: '名刺交換', instructor: '坂本 太郎', taskFlg: false },
  { date: '4/3', day: '水', category: 'Java研修', content: 'チャプター２', instructor: '吉田 浩太', taskFlg: false },
  { date: '4/4', day: '木', category: 'ビジネスマナー', content: 'ジョハリの窓', instructor: '坂本 太郎', taskFlg: false },
  { date: '4/5', day: '金', category: 'Java研修', content: 'チャプター２', instructor: '坂本 太郎', taskFlg: false },
]
