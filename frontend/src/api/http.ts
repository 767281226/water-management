import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export const TOKEN_KEY = 'hy_token'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.request.use((cfg) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

/** 全局错误提示；401 跳转登录，403「请先修改初始密码」弹出改密框。 */
http.interceptors.response.use(
  (res) => {
    const body = res.data as ApiResponse<unknown>
    if (body && typeof body === 'object' && 'code' in body && body.code !== 0) {
      ElMessage.error(body.message || '操作失败')
      const e = new Error(body.message)
      e.name = 'ApiError'
      return Promise.reject(e)
    }
    return res
  },
  async (err: AxiosError<ApiResponse<unknown>>) => {
    const status = err.response?.status
    const msg = err.response?.data?.message || (status ? `请求失败（${status}）` : '网络异常，请检查网络或服务是否启动')
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      const { default: router } = await import('@/router')
      if (router.currentRoute.value.path !== '/login') {
        ElMessage.warning(msg)
        router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    } else if (status === 403 && msg.includes('修改初始密码')) {
      const { useAuthStore } = await import('@/stores/auth')
      useAuthStore().requirePasswordChange()
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export async function get<T>(url: string, params?: object): Promise<T> {
  return (await http.get<ApiResponse<T>>(url, { params })).data.data
}

export async function post<T = void>(url: string, body?: unknown): Promise<T> {
  return (await http.post<ApiResponse<T>>(url, body)).data.data
}

export async function put<T = void>(url: string, body?: unknown): Promise<T> {
  return (await http.put<ApiResponse<T>>(url, body)).data.data
}

export async function del<T = void>(url: string): Promise<T> {
  return (await http.delete<ApiResponse<T>>(url)).data.data
}
