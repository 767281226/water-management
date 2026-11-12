import { get, post, put, type PageResult } from './http'

export interface WeekInfo {
  weekStart: string
  weekEnd: string
  year: number
  week: number
  label: string
  planDeadline: string
  reportDeadline: string
  /** 相对本周的偏移：-1 上周，0 本周，1 下周 */
  offset: number
}

export interface WeeklyItem {
  id: number
  projectId: number | null
  projectCode: string | null
  projectName: string | null
  content: string
  target: string | null
  planDays: number | null
  remark: string | null
  extra: boolean
  completion: number | null
  actualContent: string | null
  actualDays: number | null
  reason: string | null
}

export type PlanStatus = 'SUBMITTED' | 'RETURNED'
export type ReportStatus = 'NOT_SUBMITTED' | 'SUBMITTED'

export interface WeeklyPlan {
  id: number
  userId: number
  userName: string
  deptName: string | null
  week: WeekInfo
  planStatus: PlanStatus
  planSubmittedAt: string
  planOverdue: boolean
  reportStatus: ReportStatus
  reportSubmittedAt: string | null
  reportOverdue: boolean
  completionRate: number | null
  planDays: number
  comment: string | null
  commentByName: string | null
  commentAt: string | null
  returnReason: string | null
  items: WeeklyItem[]
}

export interface MineResponse {
  week: WeekInfo
  plan: WeeklyPlan | null
  canEditPlan: boolean
  canSubmitReport: boolean
  lockedReason: string | null
  lastWeekUnfinished: WeeklyItem[]
}

export interface PlanItemForm {
  projectId: number | null
  content: string
  target: string
  planDays: number
  remark: string
}

export interface ReportItemForm {
  id: number
  completion: number
  actualContent: string
  actualDays: number | null
  reason: string
}

export interface ExtraItemForm {
  projectId: number | null
  content: string
  actualDays: number | null
}

export interface HistoryRow {
  id: number
  week: WeekInfo
  itemCount: number
  planDays: number
  planOverdue: boolean
  planStatus: PlanStatus
  reportStatus: ReportStatus
  reportOverdue: boolean
  completionRate: number | null
  comment: string | null
}

export interface StatRow {
  userId: number
  userName: string
  employeeNo: string
  deptName: string | null
  planId: number | null
  planStatus: 'NONE' | PlanStatus
  planOverdue: boolean
  reportStatus: ReportStatus
  reportOverdue: boolean
  completionRate: number | null
  planDays: number | null
  itemCount: number
  commented: boolean
}

export interface StatResponse {
  week: WeekInfo
  total: number
  planSubmitted: number
  planOverdue: number
  reportSubmitted: number
  avgRate: number | null
  rows: StatRow[]
}

export interface TodoResponse {
  week: WeekInfo
  planStatus: 'NONE' | PlanStatus
  reportStatus: ReportStatus
  planDeadlinePassed: boolean
  reportDeadlinePassed: boolean
  returnReason: string | null
}

export const weeklyApi = {
  mine: (weekStart?: string) => get<MineResponse>('/weekly/mine', weekStart ? { weekStart } : {}),
  history: (page: number, size: number) => get<PageResult<HistoryRow>>('/weekly/mine/history', { page, size }),
  todo: () => get<TodoResponse>('/weekly/todo'),
  savePlan: (weekStart: string, items: PlanItemForm[]) => put<WeeklyPlan>('/weekly/plan', { weekStart, items }),
  submitReport: (weekStart: string, items: ReportItemForm[], extras: ExtraItemForm[]) =>
    put<WeeklyPlan>('/weekly/report', { weekStart, items, extras }),
  stat: (weekStart: string, deptId?: number | null) => get<StatResponse>('/weekly/stat', deptId ? { weekStart, deptId } : { weekStart }),
  detail: (id: number) => get<WeeklyPlan>(`/weekly/plans/${id}`),
  comment: (id: number, comment: string) => put(`/weekly/plans/${id}/comment`, { comment }),
  returnPlan: (id: number, reason: string) => post(`/weekly/plans/${id}/return`, { reason })
}
