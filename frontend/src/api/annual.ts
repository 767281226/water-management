import { get } from './http'

export interface AnnualTiles {
  archivedProjects: number
  publishedScores: number
  avgScore: number | null
  onTimeRate: number | null
  fillRate: number | null
  people: number
}

export interface MonthPoint {
  month: number
  archived: number
  avgScore: number | null
}

export interface PersonRow {
  userId: number
  name: string
  employeeNo: string
  deptName: string | null
  projects: number
  hours: number
  fillRate: number | null
  monthly: (number | null)[]
  avg: number | null
  rank: number | null
}

export interface DeptRow {
  deptId: number
  name: string
  people: number
  archivedProjects: number
  avgScore: number | null
  fillRate: number | null
}

export interface AnnualResponse {
  year: number
  scope: string
  tiles: AnnualTiles
  months: MonthPoint[]
  persons: PersonRow[]
  depts: DeptRow[]
  me: PersonRow | null
}

export const annualApi = {
  summary: (year: number, deptId?: number | null) => get<AnnualResponse>('/annual', deptId ? { year, deptId } : { year })
}
