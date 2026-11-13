import { del, get, post, put, type PageResult } from './http'

export type ProjectStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'TERMINATED' | 'ARCHIVED'

export const PROJECT_STATUS: Record<ProjectStatus, { label: string; type: '' | 'success' | 'warning' | 'info' | 'danger' | 'primary' }> = {
  NOT_STARTED: { label: '未开始', type: 'info' },
  IN_PROGRESS: { label: '进行中', type: 'primary' },
  PAUSED: { label: '暂停', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  TERMINATED: { label: '终止', type: 'danger' },
  ARCHIVED: { label: '已存档', type: '' }
}

/** 与后端 ProjectStatus.TRANSITIONS 保持一致 */
export const STATUS_TRANSITIONS: Record<ProjectStatus, ProjectStatus[]> = {
  NOT_STARTED: ['IN_PROGRESS', 'TERMINATED'],
  IN_PROGRESS: ['PAUSED', 'COMPLETED', 'TERMINATED'],
  PAUSED: ['IN_PROGRESS', 'TERMINATED'],
  COMPLETED: ['IN_PROGRESS'],
  TERMINATED: [],
  ARCHIVED: []
}

export interface UserRef {
  id: number
  name: string
}

export interface ProjectActions {
  edit: boolean
  changeStatus: boolean
  applyArchive: boolean
  confirmArchive: boolean
  delete: boolean
}

export interface ProjectVO {
  id: number
  code: string
  name: string
  type: string
  client: string | null
  leaderId: number
  leaderName: string
  deptId: number | null
  deptName: string | null
  contractAmount: number | null
  startDate: string | null
  planEndDate: string
  actualEndDate: string | null
  status: ProjectStatus
  statusLabel: string
  archiveApplied: boolean
  archiveRejectReason: string | null
  archivedAt: string | null
  archivedByName: string | null
  members: UserRef[]
  remark: string | null
  createdAt: string
  actions: ProjectActions
}

export interface ProjectForm {
  code: string
  name: string
  type: string
  client: string
  leaderId: number | null
  deptId: number | null
  contractAmount: number | null
  startDate: string | null
  planEndDate: string | null
  memberIds: number[]
  remark: string
}

export interface ProjectQuery {
  keyword?: string
  status?: ProjectStatus | ''
  type?: string
  deptId?: number | null
  mine?: boolean
  page: number
  size: number
}

export interface ProjectWeeklyEntry {
  itemId: number
  weekStart: string
  userId: number
  userName: string
  content: string
  target: string | null
  planDays: number | null
  completion: number | null
  actualContent: string | null
  actualDays: number | null
  reason: string | null
  extra: boolean
  reported: boolean
}

export interface ProjectDetail {
  project: ProjectVO
  weeklyCount: number
  totalDays: number
  memberCount: number
  recentEntries: ProjectWeeklyEntry[]
  score: { id: number; month: string; status: 'PENDING' | 'SCORED' | 'PUBLISHED'; total: number | null } | null
}

export interface ProjectOption {
  id: number
  code: string
  name: string
  status: ProjectStatus
}

function clean<T extends object>(q: T): Partial<T> {
  return Object.fromEntries(Object.entries(q).filter(([, v]) => v !== '' && v !== null && v !== undefined && v !== false)) as Partial<T>
}

export const projectApi = {
  page: (q: ProjectQuery) => get<PageResult<ProjectVO>>('/projects', clean(q)),
  detail: (id: number) => get<ProjectDetail>(`/projects/${id}`),
  types: () => get<string[]>('/projects/types'),
  fillable: () => get<ProjectOption[]>('/projects/options/fillable'),
  create: (f: ProjectForm) => post<number>('/projects', f),
  update: (id: number, f: ProjectForm) => put(`/projects/${id}`, f),
  setStatus: (id: number, status: ProjectStatus) => put(`/projects/${id}/status`, { status }),
  applyArchive: (id: number) => post(`/projects/${id}/archive/apply`),
  confirmArchive: (id: number) => post(`/projects/${id}/archive/confirm`),
  rejectArchive: (id: number, reason: string) => post(`/projects/${id}/archive/reject`, { reason }),
  remove: (id: number) => del(`/projects/${id}`)
}
