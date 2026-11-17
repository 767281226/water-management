import { defineStore } from 'pinia'
import { weeklyApi } from '@/api/weekly'
import { scoreApi } from '@/api/score'
import { docApi } from '@/api/doc'
import { useAuthStore } from './auth'

/**
 * 待办数量（用于手机端底部导航角标）。最多每 60 秒刷新一次。
 */
export const useTodoStore = defineStore('todo', {
  state: () => ({
    /** 本周周报待办：计划未填 / 被退回 / 完成情况未填 */
    weekly: 0,
    /** 审批类待办：待打分 + 待发布 + 下载审批 */
    approvals: 0,
    loadedAt: 0
  }),
  actions: {
    async refresh(force = false) {
      const auth = useAuthStore()
      if (!auth.user || (!force && Date.now() - this.loadedAt < 60_000)) return
      this.loadedAt = Date.now()
      const tasks: Promise<void>[] = []
      if (auth.hasPerm('weekly:plan') && !auth.user.superAdmin) {
        tasks.push(weeklyApi.todo().then((t) => {
          this.weekly = t.planStatus !== 'SUBMITTED' ? 1 : t.reportStatus !== 'SUBMITTED' ? 1 : 0
        }).catch(() => undefined))
      }
      let approvals = 0
      if (auth.hasPerm(['score:grade', 'score:publish'])) {
        tasks.push(scoreApi.todo().then((s) => { approvals += s.toGrade + s.toPublish }).catch(() => undefined))
      }
      if (auth.hasPerm('doc:approve')) {
        tasks.push(docApi.todoCount().then((n) => { approvals += n }).catch(() => undefined))
      }
      await Promise.all(tasks)
      this.approvals = approvals
    }
  }
})
