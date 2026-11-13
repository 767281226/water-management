import { del, get, post, put, upload, type PageResult } from './http'

export type DocLevel = 'PUBLIC' | 'INTERNAL' | 'SECRET'
export type StorageType = 'LOCAL' | 'NAS'

export const LEVELS: Record<DocLevel, { label: string; type: 'success' | 'primary' | 'danger' }> = {
  PUBLIC: { label: '公开', type: 'success' },
  INTERNAL: { label: '内部', type: 'primary' },
  SECRET: { label: '涉密', type: 'danger' }
}

export interface CategoryNode {
  id: number
  parentId: number | null
  name: string
  sort: number
  docCount: number
  children: CategoryNode[]
}

export interface DocAccess {
  free: boolean
  canDownload: boolean
  requestId: number | null
  remaining: number | null
  expireAt: string | null
  pending: boolean
}

export interface DocVO {
  id: number
  categoryId: number
  categoryPath: string
  name: string
  code: string | null
  version: string | null
  year: number | null
  level: DocLevel
  levelLabel: string
  needApproval: boolean
  storageType: StorageType
  fileName: string
  fileExt: string | null
  fileSize: number
  previewable: boolean
  status: 'ON' | 'OFF'
  sourceMissing: boolean
  viewCount: number
  downloadCount: number
  isNew: boolean
  uploaderName: string | null
  remark: string | null
  createdAt: string
  updatedAt: string
  access: DocAccess
}

export interface DocQuery {
  keyword?: string
  categoryId?: number | null
  level?: DocLevel | ''
  year?: number | null
  manage?: boolean
  status?: 'ON' | 'OFF' | ''
  storageType?: StorageType | ''
  page: number
  size: number
}

export interface DocForm {
  categoryId: number | null
  name: string
  code: string
  version: string
  year: number | null
  level: DocLevel
  needApproval: boolean
  remark: string
}

export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type StepStatus = 'WAITING' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export const REQUEST_STATUS: Record<RequestStatus, { label: string; type: 'warning' | 'success' | 'danger' | 'info' }> = {
  PENDING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  CANCELLED: { label: '已撤回', type: 'info' }
}

export interface RequestVO {
  id: number
  applicantId: number
  applicantName: string
  applicantDept: string | null
  projectName: string | null
  purpose: string
  status: RequestStatus
  createdAt: string
  expireAt: string | null
  maxDownloads: number
  secret: boolean
  items: { docId: number; name: string; code: string | null; level: DocLevel | null; fileExt: string | null; fileSize: number; downloadCount: number; available: boolean }[]
  steps: { stepNo: number; name: string; approverType: 'DEPT' | 'LEADER'; status: StepStatus; approverName: string | null; comment: string | null; actedAt: string | null }[]
  actions: { approve: boolean; cancel: boolean; download: boolean }
}

export interface StorageInfo {
  nasType: string
  nasEnabled: boolean
  nasDescription: string
  lastSync: string
  nasDocs: number
  localDocs: number
}

export interface SyncResult {
  scanned: number
  added: number
  updated: number
  missing: number
  restored: number
  costMs: number
}

export interface DocLog {
  id: number
  createdAt: string
  userName: string | null
  action: string
  docName: string
  docId: number | null
  requestId: number | null
  ip: string | null
  detail: string | null
}

export const LOG_ACTIONS: Record<string, string> = {
  VIEW: '预览', DOWNLOAD: '下载', UPLOAD: '上传', UPDATE: '修改', REPLACE: '更新文件',
  OFFLINE: '下架', ONLINE: '上架', DELETE: '删除', SYNC: '网盘同步'
}

function clean<T extends object>(q: T): Partial<T> {
  return Object.fromEntries(Object.entries(q).filter(([, v]) => v !== '' && v !== null && v !== undefined && v !== false)) as Partial<T>
}

export const docApi = {
  categories: () => get<CategoryNode[]>('/doc-categories'),
  createCategory: (f: { parentId: number | null; name: string; sort: number }) => post<number>('/doc-categories', f),
  updateCategory: (id: number, f: { parentId: number | null; name: string; sort: number }) => put(`/doc-categories/${id}`, f),
  deleteCategory: (id: number) => del(`/doc-categories/${id}`),

  search: (q: DocQuery) => get<PageResult<DocVO>>('/docs', clean(q)),
  detail: (id: number) => get<DocVO>(`/docs/${id}`),
  ticket: (id: number, mode: 'preview' | 'download', requestId?: number | null) =>
    post<{ url: string; expiresIn: number }>(`/docs/${id}/ticket?mode=${mode}${requestId ? `&requestId=${requestId}` : ''}`),

  upload: (file: File, form: DocForm, onProgress?: (p: number) => void) => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('data', new Blob([JSON.stringify(form)], { type: 'application/json' }))
    return upload<number>('/docs', fd, onProgress)
  },
  update: (id: number, form: DocForm) => put(`/docs/${id}`, form),
  replaceFile: (id: number, file: File, onProgress?: (p: number) => void) => {
    const fd = new FormData()
    fd.append('file', file)
    return upload(`/docs/${id}/file`, fd, onProgress)
  },
  setStatus: (id: number, status: 'ON' | 'OFF') => put(`/docs/${id}/status`, { status }),
  remove: (id: number) => del(`/docs/${id}`),
  logs: (q: { keyword?: string; action?: string; page: number; size: number }) => get<PageResult<DocLog>>('/docs/logs', clean(q)),
  storage: () => get<StorageInfo>('/docs/storage'),
  sync: () => post<SyncResult>('/docs/sync'),

  apply: (docIds: number[], purpose: string, projectId: number | null) => post<RequestVO>('/doc-requests', { docIds, purpose, projectId }),
  myRequests: (page: number, size: number) => get<PageResult<RequestVO>>('/doc-requests/mine', { page, size }),
  cancel: (id: number) => post(`/doc-requests/${id}/cancel`),
  todo: () => get<RequestVO[]>('/doc-requests/todo'),
  todoCount: () => get<number>('/doc-requests/todo-count'),
  done: (page: number, size: number) => get<PageResult<RequestVO>>('/doc-requests/done', { page, size }),
  approve: (id: number, comment = '') => post(`/doc-requests/${id}/approve`, { comment }),
  reject: (id: number, comment: string) => post(`/doc-requests/${id}/reject`, { comment }),
  batchApprove: (ids: number[]) => post<number>('/doc-requests/batch-approve', { ids })
}

/** 通过凭证链接下载（浏览器直接下载，不占用页面内存） */
export async function downloadDoc(id: number, requestId?: number | null) {
  const { url } = await docApi.ticket(id, 'download', requestId)
  const a = document.createElement('a')
  a.href = url
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
}
