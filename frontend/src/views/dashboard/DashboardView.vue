<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { weeklyApi, type TodoResponse } from '@/api/weekly'
import { scoreApi } from '@/api/score'
import { projectApi } from '@/api/project'
import { annualApi } from '@/api/annual'
import { fmtDeadline } from '@/utils/week'
import { fmtTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import { flatMenus } from '@/router/menu'

const auth = useAuthStore()
const router = useRouter()
const todo = ref<TodoResponse | null>(null)
const scoreTodo = ref<{ toGrade: number; toPublish: number } | null>(null)
const myProjects = ref<number | null>(null)
const myAvg = ref<number | null>(null)

const hour = new Date().getHours()
const greeting = hour < 6 ? '夜深了' : hour < 12 ? '上午好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好'
const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

interface TodoItem { icon: string; tone: string; title: string; sub: string; btn: string; to: string; primary: boolean }
const todos = computed<TodoItem[]>(() => {
  const t = todo.value
  const list: TodoItem[] = []
  if (t) {
    if (t.planStatus === 'RETURNED') {
      list.push({ icon: 'WarningFilled', tone: 'red', title: '本周周报被退回', sub: `原因：${t.returnReason}`, btn: '去修改', to: '/weekly/plan', primary: true })
    } else if (t.planStatus === 'NONE') {
      list.push({ icon: 'EditPen', tone: 'red', title: '本周计划未填报', sub: `截止 ${fmtDeadline(t.week.planDeadline)}${t.planDeadlinePassed ? '（已逾期）' : ''}`, btn: '去填报', to: '/weekly/plan', primary: true })
    } else {
      list.push({ icon: 'CircleCheckFilled', tone: 'green', title: '本周计划已提交', sub: t.week.label, btn: '查看', to: '/weekly/plan', primary: false })
      if (t.reportStatus === 'SUBMITTED') {
        list.push({ icon: 'CircleCheckFilled', tone: 'green', title: '本周完成情况已提交', sub: '本周周报已完成', btn: '查看', to: '/weekly/report', primary: false })
      } else {
        list.push({ icon: 'Finished', tone: 'orange', title: '本周完成情况待填报', sub: `截止 ${fmtDeadline(t.week.reportDeadline)}${t.reportDeadlinePassed ? '（已逾期）' : ''}`, btn: '去填报', to: '/weekly/report', primary: true })
      }
    }
  }
  const s = scoreTodo.value
  if (s?.toGrade) list.push({ icon: 'Star', tone: 'orange', title: `${s.toGrade} 个存档项目待打分`, sub: '月度考评', btn: '去打分', to: '/score', primary: true })
  if (s?.toPublish) list.push({ icon: 'Stamp', tone: 'violet', title: `${s.toPublish} 个考评待审核发布`, sub: '月度考评', btn: '去发布', to: '/score', primary: true })
  return list
})
const pending = computed(() => todos.value.filter((t) => t.primary).length)

const weeklyText = computed(() => {
  const t = todo.value
  if (!t) return null
  if (t.planStatus === 'RETURNED') return { v: '被退回', sub: '请修改后重新提交' }
  if (t.planStatus === 'NONE') return { v: '未填报', sub: '本周计划' }
  return t.reportStatus === 'SUBMITTED' ? { v: '已完成', sub: '计划 + 完成情况' } : { v: '进行中', sub: '待填完成情况' }
})

const stats = computed(() => [
  { icon: 'Folder', tone: 'blue', label: '我参与的项目', value: myProjects.value ?? '—', unit: myProjects.value !== null ? '个' : '', sub: '', to: '/project/ledger' },
  { icon: 'Calendar', tone: 'cyan', label: '本周周报', value: weeklyText.value?.v ?? '—', unit: '', sub: weeklyText.value?.sub ?? '', to: '/weekly/plan' },
  { icon: 'Bell', tone: 'orange', label: '待我处理', value: pending.value, unit: '项', sub: '', to: '' },
  { icon: 'Trophy', tone: 'violet', label: '年度平均分', value: myAvg.value ?? '—', unit: myAvg.value !== null ? '分' : '', sub: '', to: '/annual' }
])

const TONES = ['blue', 'cyan', 'violet', 'orange', 'green', 'pink']
const shortcuts = computed(() =>
  flatMenus().filter((m) => m.path !== '/dashboard' && auth.hasPerm(m.perm)).map((m, i) => ({ ...m, tone: TONES[i % TONES.length] }))
)

onMounted(() => {
  const isAdminOnly = auth.user?.superAdmin
  if (auth.hasPerm('weekly:plan') && !isAdminOnly) weeklyApi.todo().then((r) => (todo.value = r)).catch(() => undefined)
  if (auth.hasPerm(['score:grade', 'score:publish'])) scoreApi.todo().then((r) => (scoreTodo.value = r)).catch(() => undefined)
  if (auth.hasPerm('project:ledger')) projectApi.page({ mine: true, page: 1, size: 1 }).then((r) => (myProjects.value = r.total)).catch(() => undefined)
  if (auth.hasPerm('annual') && !isAdminOnly) annualApi.summary(new Date().getFullYear()).then((r) => (myAvg.value = r.me?.avg ?? null)).catch(() => undefined)
})
</script>

<template>
  <div class="page">
    <!-- 欢迎横幅 -->
    <section class="hero">
      <div class="hero-text">
        <p class="date">{{ today }}</p>
        <h1>{{ greeting }}，{{ auth.user?.realName }}</h1>
        <p class="meta">
          {{ auth.user?.deptName || '未分配部门' }} · {{ auth.user?.title || auth.user?.roles.join(' / ') }}
          <span v-if="auth.user?.lastLoginAt"> · 上次登录 {{ fmtTime(auth.user.lastLoginAt) }}</span>
        </p>
        <p v-if="todo" class="week">
          <el-icon><Calendar /></el-icon>{{ todo.week.label }}
          <template v-if="pending"> · 还有 <b>{{ pending }}</b> 项待办</template>
          <template v-else> · 本周事项都已完成</template>
        </p>
        <div class="actions">
          <el-button v-if="auth.hasPerm('weekly:plan') && !auth.user?.superAdmin" class="btn-light" @click="router.push('/weekly/plan')">
            <el-icon><EditPen /></el-icon>填写周计划
          </el-button>
          <el-button v-if="auth.hasPerm('project:ledger')" class="btn-ghost" @click="router.push('/project/ledger')">
            <el-icon><Folder /></el-icon>项目台账
          </el-button>
          <el-button v-if="auth.user?.superAdmin" class="btn-light" @click="router.push('/system/user')">
            <el-icon><User /></el-icon>用户管理
          </el-button>
        </div>
      </div>
      <!-- 装饰：水滴与波纹 -->
      <svg class="hero-art" viewBox="0 0 320 220" aria-hidden="true">
        <defs>
          <linearGradient id="dropG" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#ffffff" stop-opacity="0.95" />
            <stop offset="1" stop-color="#9fdcff" stop-opacity="0.6" />
          </linearGradient>
        </defs>
        <ellipse cx="190" cy="186" rx="120" ry="18" fill="rgba(255,255,255,0.10)" />
        <ellipse cx="190" cy="186" rx="80" ry="11" fill="rgba(255,255,255,0.12)" />
        <ellipse cx="190" cy="186" rx="42" ry="6" fill="rgba(255,255,255,0.16)" />
        <path d="M190 30 C 190 30 245 92 245 128 A 55 55 0 0 1 135 128 C 135 92 190 30 190 30 Z" fill="url(#dropG)" />
        <path d="M168 120 A 26 26 0 0 0 186 150" stroke="#fff" stroke-width="6" stroke-linecap="round" fill="none" opacity="0.8" />
        <circle cx="82" cy="70" r="16" fill="rgba(255,255,255,0.18)" />
        <circle cx="270" cy="50" r="9" fill="rgba(255,255,255,0.22)" />
        <circle cx="58" cy="140" r="6" fill="rgba(255,255,255,0.25)" />
      </svg>
    </section>

    <!-- 指标卡 -->
    <div class="stats">
      <div v-for="s in stats" :key="s.label" class="stat" :class="[`tone-${s.tone}`, { link: !!s.to }]" @click="s.to && router.push(s.to)">
        <div class="si"><el-icon :size="22"><component :is="s.icon" /></el-icon></div>
        <div class="sv">
          <span>{{ s.label }}</span>
          <b>{{ s.value }}<small>{{ s.unit }}</small></b>
          <em v-if="s.sub">{{ s.sub }}</em>
        </div>
      </div>
    </div>

    <el-row :gutter="18">
      <el-col :xs="24" :lg="10">
        <div class="card block">
          <div class="block-head"><h4>我的待办</h4><span v-if="pending" class="count">{{ pending }}</span></div>
          <div v-for="t in todos" :key="t.title" class="todo">
            <div class="ti" :class="`tone-${t.tone}`"><el-icon :size="18"><component :is="t.icon" /></el-icon></div>
            <div class="tx"><b>{{ t.title }}</b><small>{{ t.sub }}</small></div>
            <el-button size="small" :type="t.primary ? 'primary' : ''" round @click="router.push(t.to)">{{ t.btn }}</el-button>
          </div>
          <el-empty v-if="!todos.length" description="暂无待办事项" :image-size="80" />
        </div>
      </el-col>
      <el-col :xs="24" :lg="14">
        <div class="card block">
          <div class="block-head"><h4>常用功能</h4></div>
          <div class="shortcuts">
            <router-link v-for="m in shortcuts" :key="m.path" :to="m.path" class="sc">
              <div class="sc-icon" :class="`tone-${m.tone}`"><el-icon :size="22"><component :is="m.icon" /></el-icon></div>
              <span>{{ m.title }}</span>
              <small v-if="!m.component">建设中</small>
            </router-link>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
/* 色调：图标底色 */
.tone-blue { --c1: #2f6bff; --c2: #5b8cff; --bg: #eaf0ff; --glow: rgba(47, 107, 255, 0.32); }
.tone-cyan { --c1: #0ea5e9; --c2: #38c4f5; --bg: #e3f6fd; --glow: rgba(14, 165, 233, 0.32); }
.tone-violet { --c1: #7c5cff; --c2: #a08bff; --bg: #f0ecff; --glow: rgba(124, 92, 255, 0.32); }
.tone-orange { --c1: #f59e0b; --c2: #fbbf4b; --bg: #fff5e1; --glow: rgba(245, 158, 11, 0.32); }
.tone-green { --c1: #10b981; --c2: #3fd3a0; --bg: #e3f8f0; --glow: rgba(16, 185, 129, 0.32); }
.tone-pink { --c1: #ec4899; --c2: #f472b6; --bg: #fdeaf4; --glow: rgba(236, 72, 153, 0.32); }
.tone-red { --c1: #ef4444; --c2: #f87171; --bg: #fdecec; --glow: rgba(239, 68, 68, 0.32); }

/* ---------------- 横幅 ---------------- */
.hero {
  position: relative;
  overflow: hidden;
  border-radius: 20px;
  padding: 30px 34px;
  color: #fff;
  background:
    radial-gradient(500px 260px at 85% 0%, rgba(25, 182, 255, 0.55), transparent 70%),
    linear-gradient(120deg, #1546c9 0%, #2f6bff 45%, #19b6ff 100%);
  box-shadow: 0 16px 40px rgba(47, 107, 255, 0.28);
  display: flex;
  align-items: center;
  min-height: 200px;
  margin-bottom: 20px;
}
.hero::after {
  content: '';
  position: absolute;
  inset: auto -10% -60% -10%;
  height: 160px;
  background: radial-gradient(closest-side, rgba(255, 255, 255, 0.14), transparent);
}
.hero-text {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
}
.date {
  margin: 0;
  font-size: 13px;
  opacity: 0.85;
  letter-spacing: 1px;
}
.hero h1 {
  margin: 8px 0;
  font-size: 28px;
  letter-spacing: 1px;
}
.meta {
  margin: 0;
  font-size: 14px;
  opacity: 0.88;
}
.week {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 14px 0 0;
  padding: 6px 14px;
  font-size: 13px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 18px;
  backdrop-filter: blur(4px);
}
.week b {
  font-size: 15px;
}
.actions {
  margin-top: 18px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.btn-light {
  background: #fff !important;
  color: var(--hy-primary) !important;
  border: none !important;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(11, 31, 75, 0.18);
}
.btn-ghost {
  background: rgba(255, 255, 255, 0.14) !important;
  color: #fff !important;
  border: 1px solid rgba(255, 255, 255, 0.4) !important;
}
.btn-light .el-icon,
.btn-ghost .el-icon {
  margin-right: 4px;
}
.hero-art {
  position: relative;
  z-index: 1;
  width: 300px;
  flex: none;
  animation: float 6s ease-in-out infinite;
}
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

/* ---------------- 指标卡 ---------------- */
.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  margin-bottom: 20px;
}
.stat {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border-radius: var(--hy-radius);
  padding: 20px;
  box-shadow: var(--hy-shadow);
  border: 1px solid rgba(230, 235, 243, 0.7);
  position: relative;
  overflow: hidden;
  transition: transform 0.18s, box-shadow 0.18s;
}
.stat::after {
  content: '';
  position: absolute;
  right: -30px;
  top: -30px;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: var(--bg);
  opacity: 0.7;
}
.stat.link {
  cursor: pointer;
}
.stat.link:hover {
  transform: translateY(-3px);
  box-shadow: var(--hy-shadow-hover);
}
.si {
  width: 52px;
  height: 52px;
  border-radius: 15px;
  background: linear-gradient(135deg, var(--c1), var(--c2));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  box-shadow: 0 8px 18px var(--glow);
  position: relative;
  z-index: 1;
}
.sv {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.sv span {
  color: var(--hy-text-2);
  font-size: 13px;
}
.sv b {
  font-size: 26px;
  line-height: 1.3;
  font-variant-numeric: tabular-nums;
}
.sv small {
  font-size: 13px;
  font-weight: normal;
  color: var(--hy-text-3);
  margin-left: 3px;
}
.sv em {
  font-style: normal;
  font-size: 12px;
  color: var(--hy-text-3);
}

/* ---------------- 待办 / 常用功能 ---------------- */
.block {
  margin-bottom: 18px;
  min-height: 300px;
}
.block-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.block-head h4 {
  margin: 0;
  font-size: 16px;
}
.count {
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 11px;
  background: linear-gradient(135deg, #ff6b6b, #f43f5e);
  color: #fff;
  font-size: 12px;
  line-height: 22px;
  text-align: center;
}
.todo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin: 0 -12px;
  border-radius: 12px;
  transition: background 0.15s;
}
.todo:hover {
  background: #f5f8ff;
}
.ti {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--bg);
  color: var(--c1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.tx {
  flex: 1;
  min-width: 0;
}
.tx b {
  display: block;
  font-size: 14px;
}
.tx small {
  color: var(--hy-text-3);
  font-size: 12px;
}
.shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(118px, 1fr));
  gap: 12px;
}
.sc {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 18px 8px 14px;
  border-radius: 14px;
  color: var(--hy-text);
  text-decoration: none;
  font-size: 13px;
  border: 1px solid transparent;
  transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s;
}
.sc:hover {
  transform: translateY(-3px);
  border-color: #e3ebfb;
  box-shadow: var(--hy-shadow-hover);
}
.sc-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--c1), var(--c2));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 16px var(--glow);
}
.sc small {
  font-size: 11px;
  color: var(--hy-text-3);
  margin-top: -4px;
}

@media (max-width: 1100px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .hero-art {
    width: 220px;
  }
}
@media (max-width: 768px) {
  .hero {
    padding: 22px 20px;
    border-radius: 16px;
  }
  .hero h1 {
    font-size: 22px;
  }
  .hero-art {
    display: none;
  }
  .stats {
    gap: 12px;
  }
  .stat {
    padding: 14px;
    gap: 12px;
  }
  .si {
    width: 42px;
    height: 42px;
    border-radius: 12px;
  }
  .sv b {
    font-size: 20px;
  }
  .block {
    min-height: 0;
  }
}
</style>
