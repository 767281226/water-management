import { defineStore } from 'pinia'
import { authApi, type Me } from '@/api/auth'
import { TOKEN_KEY } from '@/api/http'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: null as Me | null,
    /** 需要强制修改密码（初始密码 / 被重置） */
    mustChangePwd: false
  }),
  getters: {
    permSet: (s) => new Set(s.user?.permissions || [])
  },
  actions: {
    hasPerm(code?: string | string[]): boolean {
      if (!code) return true
      if (this.user?.superAdmin) return true
      const codes = Array.isArray(code) ? code : [code]
      return codes.some((c) => this.permSet.has(c))
    },
    setToken(token: string) {
      this.token = token
      localStorage.setItem(TOKEN_KEY, token)
    },
    async login(username: string, password: string) {
      const res = await authApi.login(username, password)
      this.setToken(res.token)
      await this.fetchMe()
      return res
    },
    async fetchMe(silent = false) {
      this.user = await authApi.me(silent)
      this.mustChangePwd = this.user.mustChangePwd
    },
    requirePasswordChange() {
      this.mustChangePwd = true
    },
    async changePassword(oldPassword: string, newPassword: string) {
      const res = await authApi.changePassword(oldPassword, newPassword)
      this.setToken(res.token)
      await this.fetchMe()
    },
    async logout(callApi = true) {
      if (callApi && this.token) {
        try {
          await authApi.logout()
        } catch {
          // 令牌已失效时忽略
        }
      }
      this.token = ''
      this.user = null
      this.mustChangePwd = false
      localStorage.removeItem(TOKEN_KEY)
    }
  }
})
