import { get, post, put } from './http'

export type DataScope = 'ALL' | 'DEPT' | 'SELF'

export interface LoginResponse {
  token: string
  expiresIn: number
  mustChangePwd: boolean
}

export interface Me {
  id: number
  username: string
  realName: string
  employeeNo: string
  deptId: number | null
  deptName: string | null
  title: string | null
  phone: string | null
  email: string | null
  superAdmin: boolean
  dataScope: DataScope
  roles: string[]
  permissions: string[]
  mustChangePwd: boolean
  lastLoginAt: string | null
}

export const authApi = {
  login: (username: string, password: string) => post<LoginResponse>('/auth/login', { username, password }),
  logout: () => post('/auth/logout'),
  me: () => get<Me>('/auth/me'),
  changePassword: (oldPassword: string, newPassword: string) =>
    put<LoginResponse>('/auth/password', { oldPassword, newPassword }),
  updateProfile: (phone: string, email: string) => put('/auth/profile', { phone, email })
}
