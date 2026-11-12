import { get } from './http'
import type { DeptNode, UserOption } from './system'

/** 业务页面通用下拉数据（登录即可访问） */
export const commonApi = {
  users: () => get<UserOption[]>('/common/users'),
  depts: () => get<DeptNode[]>('/common/depts')
}
