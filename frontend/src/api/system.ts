import { del, get, post, put, type PageResult } from './http'
import type { DataScope } from './auth'

// ---------------- 部门 ----------------
export interface DeptNode {
  id: number
  parentId: number | null
  name: string
  leaderId: number | null
  leaderName: string | null
  sort: number
  userCount: number
  children: DeptNode[]
}

export interface DeptForm {
  parentId: number | null
  name: string
  leaderId: number | null
  sort: number
}

// ---------------- 用户 ----------------
export type UserStatus = 'ENABLED' | 'DISABLED'

export interface RoleBrief {
  id: number
  code: string
  name: string
}

export interface UserVO {
  id: number
  username: string
  employeeNo: string
  realName: string
  deptId: number | null
  deptName: string | null
  title: string | null
  phone: string | null
  email: string | null
  status: UserStatus
  builtin: boolean
  locked: boolean
  roles: RoleBrief[]
  lastLoginAt: string | null
  lastLoginIp: string | null
  createdAt: string
  remark: string | null
}

export interface UserForm {
  username: string
  employeeNo: string
  realName: string
  deptId: number | null
  title: string
  phone: string
  email: string
  roleIds: number[]
  remark: string
}

export interface UserQuery {
  keyword?: string
  deptId?: number | null
  status?: UserStatus | ''
  roleId?: number | null
  page: number
  size: number
}

export interface UserOption {
  id: number
  realName: string
  employeeNo: string
  deptName: string | null
}

// ---------------- 角色 ----------------
export interface RoleVO {
  id: number
  code: string
  name: string
  dataScope: DataScope
  builtin: boolean
  superAdmin: boolean
  sort: number
  remark: string | null
  permissions: string[]
  userCount: number
  createdAt: string
}

export interface RoleForm {
  code: string
  name: string
  dataScope: DataScope
  sort: number
  remark: string
  permissions: string[]
}

export interface PermissionNode {
  code: string
  name: string
  type: 'MENU' | 'BUTTON'
  children: PermissionNode[]
}

// ---------------- 参数 ----------------
export interface ConfigItem {
  key: string
  value: string
  group: string
  name: string
  remark: string | null
  updatedAt: string
}

export interface ScoreRule {
  id: number | null
  ruleKey: string
  name: string
  weight: number
  description: string | null
  sort: number | null
  enabled: boolean
}

// ---------------- 日志 ----------------
export interface OperLog {
  id: number
  userId: number | null
  username: string | null
  realName: string | null
  module: string
  action: string
  method: string | null
  uri: string | null
  params: string | null
  success: boolean
  errorMsg: string | null
  ip: string | null
  costMs: number | null
  createdAt: string
}

export interface LoginLog {
  id: number
  username: string
  success: boolean
  message: string
  ip: string | null
  userAgent: string | null
  createdAt: string
}

export interface LogQuery {
  keyword?: string
  module?: string
  success?: boolean | ''
  start?: string
  end?: string
  page: number
  size: number
}

/** 去掉空字符串 / null 参数 */
function clean<T extends object>(q: T): Partial<T> {
  return Object.fromEntries(Object.entries(q).filter(([, v]) => v !== '' && v !== null && v !== undefined)) as Partial<T>
}

export const deptApi = {
  tree: () => get<DeptNode[]>('/system/depts/tree'),
  create: (f: DeptForm) => post<number>('/system/depts', f),
  update: (id: number, f: DeptForm) => put(`/system/depts/${id}`, f),
  remove: (id: number) => del(`/system/depts/${id}`)
}

export const userApi = {
  page: (q: UserQuery) => get<PageResult<UserVO>>('/system/users', clean(q)),
  options: () => get<UserOption[]>('/system/users/options'),
  create: (f: UserForm) => post<number>('/system/users', f),
  update: (id: number, f: UserForm) => put(`/system/users/${id}`, f),
  setStatus: (id: number, status: UserStatus) => put(`/system/users/${id}/status`, { status }),
  unlock: (id: number) => put(`/system/users/${id}/unlock`),
  resetPassword: (id: number) => put<{ password: string }>(`/system/users/${id}/reset-password`),
  remove: (id: number) => del(`/system/users/${id}`)
}

export const roleApi = {
  list: () => get<RoleVO[]>('/system/roles'),
  permissionTree: () => get<PermissionNode[]>('/system/permissions/tree'),
  create: (f: RoleForm) => post<number>('/system/roles', f),
  update: (id: number, f: RoleForm) => put(`/system/roles/${id}`, f),
  remove: (id: number) => del(`/system/roles/${id}`)
}

export const configApi = {
  list: (group: string) => get<ConfigItem[]>('/system/configs', { group }),
  update: (items: { key: string; value: string }[]) => put('/system/configs', items),
  scoreRules: () => get<ScoreRule[]>('/system/score-rules'),
  saveScoreRules: (rules: ScoreRule[]) => put('/system/score-rules', rules)
}

export const logApi = {
  oper: (q: LogQuery) => get<PageResult<OperLog>>('/system/logs/oper', clean(q)),
  login: (q: LogQuery) => get<PageResult<LoginLog>>('/system/logs/login', clean(q))
}

export const DATA_SCOPE_LABEL: Record<DataScope, string> = {
  ALL: '全公司',
  DEPT: '本部门（含下级）',
  SELF: '仅本人'
}
