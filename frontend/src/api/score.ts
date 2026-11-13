import { get, post, put } from './http'

export type ScoreStatus = 'PENDING' | 'SCORED' | 'PUBLISHED'

export const SCORE_STATUS: Record<ScoreStatus, { label: string; type: 'warning' | 'primary' | 'success' }> = {
  PENDING: { label: '待打分', type: 'warning' },
  SCORED: { label: '已打分', type: 'primary' },
  PUBLISHED: { label: '已发布', type: 'success' }
}

export interface RuleRef {
  ruleKey: string
  name: string
  weight: number
  description: string | null
}

export interface ScoreItem {
  ruleKey: string
  ruleName: string
  weight: number
  score: number
}

export interface Reference {
  planDuration: number | null
  actualDuration: number | null
  delayDays: number | null
  weeklyCount: number
  totalDays: number
  avgCompletion: number | null
  overduePlans: number
}

export interface Share {
  userId: number
  name: string
  days: number
  ratio: number
  byHours: boolean
}

export interface ScoreVO {
  id: number
  month: string
  status: ScoreStatus
  project: {
    id: number
    code: string
    name: string
    type: string
    deptName: string | null
    leaderName: string
    archivedAt: string
    startDate: string | null
    planEndDate: string
    actualEndDate: string | null
  }
  detailVisible: boolean
  total: number | null
  items: ScoreItem[]
  comment: string | null
  scorerName: string | null
  scoredAt: string | null
  publisherName: string | null
  publishedAt: string | null
  reference: Reference
  shares: Share[]
  actions: { grade: boolean; publish: boolean; unpublish: boolean }
}

export interface MonthResponse {
  month: string
  pending: number
  scored: number
  published: number
  rules: RuleRef[]
  list: ScoreVO[]
}

export const scoreApi = {
  month: (month?: string) => get<MonthResponse>('/scores', month ? { month } : {}),
  todo: () => get<{ toGrade: number; toPublish: number }>('/scores/todo'),
  grade: (id: number, items: { ruleKey: string; score: number }[], comment: string) =>
    put<ScoreVO>(`/scores/${id}/grade`, { items, comment }),
  publish: (id: number) => post(`/scores/${id}/publish`),
  publishMonth: (month: string) => post<number>(`/scores/publish?month=${month}`),
  unpublish: (id: number, reason: string) => post(`/scores/${id}/unpublish`, { reason })
}
