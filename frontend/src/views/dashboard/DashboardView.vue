<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { weeklyApi, type TodoResponse } from '@/api/weekly'
import { fmtDeadline } from '@/utils/week'
import { useAuthStore } from '@/stores/auth'
import { flatMenus } from '@/router/menu'
import { DATA_SCOPE_LABEL } from '@/api/system'
import { fmtTime } from '@/utils/format'

const auth = useAuthStore()
const router = useRouter()
const todo = ref<TodoResponse | null>(null)

interface TodoItem { icon: string; cls: string; title: string; sub: string; btn: string; to: string; primary: boolean }
const todos = computed<TodoItem[]>(() => {
  const t = todo.value
  if (!t) return []
  const list: TodoItem[] = []
  if (t.planStatus === 'RETURNED') {
    list.push({ icon: 'WarningFilled', cls: 'bad', title: '本周周报被退回', sub: `原因：${t.returnReason}`, btn: '去修改', to: '/weekly/plan', primary: true })
  } else if (t.planStatus === 'NONE') {
    list.push({ icon: 'EditPen', cls: 'bad', title: '本周计划未填报', sub: `截止 ${fmtDeadline(t.week.planDeadline)}${t.planDeadlinePassed ? '（已逾期）' : ''}`, btn: '去填报', to: '/weekly/plan', primary: true })
  } else {
    list.push({ icon: 'CircleCheckFilled', cls: 'good', title: '本周计划已提交', sub: t.week.label, btn: '查看', to: '/weekly/plan', primary: false })
    if (t.reportStatus === 'SUBMITTED') {
      list.push({ icon: 'CircleCheckFilled', cls: 'good', title: '本周完成情况已提交', sub: '本周周报已完成', btn: '查看', to: '/weekly/report', primary: false })
    } else {
      list.push({ icon: 'Finished', cls: 'warn', title: '本周完成情况待填报', sub: `截止 ${fmtDeadline(t.week.reportDeadline)}${t.reportDeadlinePassed ? '（已逾期）' : ''}`, btn: '去填报', to: '/weekly/report', primary: true })
    }
  }
  return list
})

onMounted(async () => {
  if (auth.hasPerm('weekly:plan') && !auth.user?.superAdmin) todo.value = await weeklyApi.todo()
})
const hour = new Date().getHours()
const greeting = hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'
const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const shortcuts = computed(() => flatMenus().filter((m) => m.path !== '/dashboard' && auth.hasPerm(m.perm)))
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>{{ greeting }}，{{ auth.user?.realName }}</h3>
        <p>{{ today }}</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <div class="card info">
          <el-avatar :size="56" class="avatar">{{ auth.user?.realName.slice(-1) }}</el-avatar>
          <div>
            <b>{{ auth.user?.realName }}</b>
            <div class="muted small">{{ auth.user?.employeeNo }} · {{ auth.user?.deptName || '未分配部门' }} · {{ auth.user?.title || '—' }}</div>
            <div class="tags">
              <el-tag v-for="r in auth.user?.roles" :key="r" size="small" effect="plain">{{ r }}</el-tag>
            </div>
          </div>
        </div>
        <div class="card kv">
          <div><span>数据范围</span><b>{{ auth.user ? DATA_SCOPE_LABEL[auth.user.dataScope] : '—' }}</b></div>
          <div><span>权限点</span><b>{{ auth.user?.superAdmin ? '全部（超级管理员）' : `${auth.user?.permissions.length} 个` }}</b></div>
          <div><span>上次登录</span><b>{{ fmtTime(auth.user?.lastLoginAt) }}</b></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="16">
        <div v-if="todos.length" class="card">
          <h4>我的待办</h4>
          <div v-for="t in todos" :key="t.title" class="todo">
            <div class="ti" :class="t.cls"><el-icon :size="18"><component :is="t.icon" /></el-icon></div>
            <div class="tx"><b>{{ t.title }}</b><small>{{ t.sub }}</small></div>
            <el-button size="small" :type="t.primary ? 'primary' : ''" @click="router.push(t.to)">{{ t.btn }}</el-button>
          </div>
        </div>
        <div class="card">
          <h4>常用功能</h4>
          <div class="shortcuts">
            <router-link v-for="m in shortcuts" :key="m.path" :to="m.path" class="sc">
              <el-icon :size="22"><component :is="m.icon" /></el-icon>
              <span>{{ m.title }}</span>
              <small v-if="!m.component">建设中</small>
            </router-link>
          </div>
        </div>
        <el-alert type="info" :closable="false" show-icon class="notice">
          <template #title>已上线：系统管理、项目台账、周计划 / 周完成情况、周报统计；月度考评、年度汇总、资料中心正在开发中。</template>
        </el-alert>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.card {
  margin-bottom: 16px;
}
.info {
  display: flex;
  gap: 14px;
  align-items: center;
}
.info b {
  font-size: 17px;
}
.avatar {
  background: var(--hy-primary);
  font-size: 20px;
  flex: none;
}
.tags {
  margin-top: 6px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.kv div {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #eef1f5;
}
.kv div:last-child {
  border-bottom: none;
}
.kv span {
  color: #8a94a6;
}
h4 {
  margin: 0 0 14px;
}
.shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
}
.sc {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 16px 8px;
  border-radius: 10px;
  background: #f7f9fb;
  color: #1b2430;
  text-decoration: none;
  font-size: 13px;
}
.sc:hover {
  background: #eaf1fb;
  color: var(--hy-primary);
}
.sc small {
  font-size: 11px;
  color: #8a94a6;
}
.todo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #eef1f5;
}
.todo:last-child {
  border-bottom: none;
}
.ti {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.ti.bad { background: #fdecec; color: #d64545; }
.ti.warn { background: #fdf3e1; color: #b77400; }
.ti.good { background: #e5f5ec; color: #1a8f4d; }
.tx {
  flex: 1;
  min-width: 0;
}
.tx b {
  display: block;
}
.tx small {
  color: #8a94a6;
}
.notice {
  margin-bottom: 16px;
}
</style>
