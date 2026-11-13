import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 静默校验登录状态：401 时只清除令牌，不弹提示、不跳转（由调用方处理） */
    silentAuth?: boolean
  }
}
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
let redirectingToLogin = false

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
    if (err.config?.silentAuth) {
      if (status === 401) localStorage.removeItem(TOKEN_KEY)
      return Promise.reject(err)
    }
    if (status === 401) {
      // 同一页面多个请求同时 401 时只处理一次；必须清空 store 中的登录状态，
      // 否则路由守卫仍认为已登录，会从登录页把用户弹回原页面
      if (!redirectingToLogin) {
        redirectingToLogin = true
        const [{ default: router }, { useAuthStore }] = await Promise.all([import('@/router'), import('@/stores/auth')])
        await useAuthStore().logout(false)
        const current = router.currentRoute.value
        if (current.path !== '/login') {
          ElMessage.warning(msg)
          await router.replace({ path: '/login', query: { redirect: current.fullPath } }).catch(() => undefined)
        }
        redirectingToLogin = false
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

export async function get<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
  return (await http.get<ApiResponse<T>>(url, { ...config, params })).data.data
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
