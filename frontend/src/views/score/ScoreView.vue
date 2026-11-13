<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { SCORE_STATUS, scoreApi, type MonthResponse, type ScoreVO } from '@/api/score'
import { useAuthStore } from '@/stores/auth'
import { fmtTime } from '@/utils/format'
import ScoreGradeDialog from './ScoreGradeDialog.vue'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const queryMonth = typeof route.query.month === 'string' && /^\d{4}-\d{2}$/.test(route.query.month) ? route.query.month : null
const month = ref(queryMonth ?? dayjs().subtract(1, 'month').format('YYYY-MM'))
const data = ref<MonthResponse | null>(null)
const loading = ref(false)
const dialog = ref(false)
const current = ref<ScoreVO | null>(null)
const canPublishAll = computed(() => (data.value?.list ?? []).some((s) => s.actions.publish))

async function load() {
  loading.value = true
  try {
    data.value = await scoreApi.month(month.value)
  } finally {
    loading.value = false
  }
}

function openGrade(s: ScoreVO) {
  current.value = s
  dialog.value = true
}

async function publish(s: ScoreVO) {
  await ElMessageBox.confirm(`发布后「${s.project.name}」的考评结果对项目成员可见，并锁定不能修改。`, '审核发布', { type: 'warning' })
  await scoreApi.publish(s.id)
  ElMessage.success('已发布')
  load()
}

async function publishAll() {
  await ElMessageBox.confirm(`发布 ${month.value} 全部已打分的考评？`, '批量发布', { type: 'warning' })
  const n = await scoreApi.publishMonth(month.value)
  ElMessage.success(`已发布 ${n} 个项目的考评`)
  load()
}

async function unpublish(s: ScoreVO) {
  const { value } = await ElMessageBox.prompt('撤回后可以重新打分，原因会记录在操作日志中。', '撤回发布', {
    inputPlaceholder: '请填写撤回原因',
    inputValidator: (v) => (!!v && v.trim().length > 0) || '请填写撤回原因'
  })
  await scoreApi.unpublish(s.id, value)
  ElMessage.success('已撤回')
  load()
}

const delayText = (d: number | null) => (d === null ? '' : d > 0 ? `延期 ${d} 天` : d < 0 ? `提前 ${-d} 天` : '按期')

watch(month, (m) => {
  router.replace({ query: { month: m } })
  load()
})
onMounted(async () => {
  await load()
  // 默认显示上月；上月没有考评而本月已有存档项目时，切到本月
  if (!queryMonth && data.value && !data.value.list.length) {
    const cur = dayjs().format('YYYY-MM')
    const now = await scoreApi.month(cur)
    if (now.list.length) {
      month.value = cur
    }
  }
})
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-head">
      <div>
        <h3>月度考评</h3>
        <p>项目确认存档后进入存档当月的考评清单 · 部门负责人打分 → 公司领导审核发布</p>
      </div>
      <div class="btns">
        <el-date-picker v-model="month" type="month" value-format="YYYY-MM" :clearable="false" style="width: 140px" />
        <el-button v-if="canPublishAll" type="primary" @click="publishAll">批量发布</el-button>
      </div>
    </div>

    <template v-if="data">
      <div class="card head-card">
        <div class="rules">
          <span class="muted small">考评指标：</span>
          <el-tooltip v-for="r in data.rules" :key="r.ruleKey" :content="r.description || r.name" placement="top">
            <el-tag effect="plain">{{ r.name }} {{ r.weight }}%</el-tag>
          </el-tooltip>
          <el-link v-if="auth.hasPerm('system:config')" type="primary" :underline="false" class="small" @click="router.push('/system/config')">调整指标</el-link>
        </div>
        <div class="counts">
          <span>待打分 <b>{{ data.pending }}</b></span>
          <span>待发布 <b>{{ data.scored }}</b></span>
          <span>已发布 <b>{{ data.published }}</b></span>
        </div>
      </div>

      <el-empty v-if="!data.list.length" :description="`${data.month} 没有需要考评的存档项目`" class="card" />

      <div v-for="s in data.list" :key="s.id" class="card item">
        <div class="item-head">
          <div>
            <el-link type="primary" :underline="false" class="pname" @click="router.push(`/project/ledger/${s.project.id}`)">{{ s.project.name }}</el-link>
            <div class="muted small">{{ s.project.code }} · {{ s.project.type }} · {{ s.project.deptName || '—' }} · 负责人 {{ s.project.leaderName }} · 存档于 {{ fmtTime(s.project.archivedAt, 'YYYY-MM-DD') }}</div>
          </div>
          <div class="right">
            <el-tag :type="SCORE_STATUS[s.status].type">{{ SCORE_STATUS[s.status].label }}</el-tag>
            <div v-if="s.detailVisible && s.total !== null" class="total">{{ Number(s.total).toFixed(1) }}</div>
          </div>
        </div>

        <div class="refs">
          <div class="ref"><span>计划 / 实际工期</span><b>{{ s.reference.planDuration ?? '—' }} / {{ s.reference.actualDuration ?? '—' }}<small> 天</small></b>
            <em :class="{ bad: (s.reference.delayDays ?? 0) > 0, good: (s.reference.delayDays ?? 0) <= 0 }">{{ delayText(s.reference.delayDays) }}</em></div>
          <div class="ref"><span>周报完成率</span><b>{{ s.reference.avgCompletion ?? '—' }}<small>%</small></b><em>{{ s.reference.weeklyCount }} 条周报记录</em></div>
          <div class="ref"><span>累计工时</span><b>{{ Number(s.reference.totalDays) }}<small> 人·天</small></b><em>{{ s.shares.length }} 人参与</em></div>
          <div class="ref"><span>逾期填报</span><b>{{ s.reference.overduePlans }}<small> 次</small></b><em>系统自动统计</em></div>
        </div>

        <template v-if="s.detailVisible && s.items.length">
          <div class="scores">
            <el-tag v-for="i in s.items" :key="i.ruleKey" type="info" effect="plain">{{ i.ruleName }}（{{ i.weight }}%）：<b>{{ i.score }}</b></el-tag>
          </div>
          <div class="small muted">评语：{{ s.comment || '—' }} · 打分人 {{ s.scorerName }}（{{ fmtTime(s.scoredAt) }}）<template v-if="s.publisherName"> · 发布人 {{ s.publisherName }}</template></div>
        </template>
        <div v-else-if="!s.detailVisible" class="small muted">考评结果尚未发布</div>

        <div class="small muted shares">
          个人贡献（{{ s.shares[0]?.byHours ? '按周报工时占比' : '暂无周报工时，负责人 40%、其余成员平分' }}）：
          <span v-for="sh in s.shares" :key="sh.userId">{{ sh.name }} {{ Math.round(sh.ratio * 100) }}%<template v-if="sh.byHours">（{{ Number(sh.days) }} 天）</template></span>
        </div>

        <div v-if="s.actions.grade || s.actions.publish || s.actions.unpublish" class="ops">
          <el-button v-if="s.actions.grade" :type="s.status === 'PENDING' ? 'primary' : ''" @click="openGrade(s)">{{ s.status === 'PENDING' ? '开始打分' : '修改打分' }}</el-button>
          <el-button v-if="s.actions.publish" type="success" @click="publish(s)">审核发布</el-button>
          <el-button v-if="s.actions.unpublish" type="warning" plain @click="unpublish(s)">撤回发布</el-button>
        </div>
      </div>
      <ScoreGradeDialog v-model="dialog" :score="current" :rules="data.rules" @saved="load" />
    </template>
  </div>
</template>

<style scoped>
.btns {
  display: flex;
  gap: 8px;
}
.head-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.rules {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.counts {
  display: flex;
  gap: 18px;
  color: #5a6577;
}
.counts b {
  color: #1b2430;
  font-size: 16px;
}
.item {
  margin-bottom: 16px;
}
.item-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.pname {
  font-size: 16px;
  font-weight: 600;
}
.right {
  text-align: right;
  flex: none;
}
.total {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-color-primary-dark-2);
}
.refs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0 12px;
}
.ref {
  background: #f7f9fb;
  border-radius: 10px;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ref span {
  font-size: 12px;
  color: #5a6577;
}
.ref b {
  font-size: 18px;
}
.ref small {
  font-size: 12px;
  color: #8a94a6;
  font-weight: normal;
}
.ref em {
  font-style: normal;
  font-size: 12px;
  color: #8a94a6;
}
.ref em.bad {
  color: var(--el-color-danger);
}
.ref em.good {
  color: var(--el-color-success);
}
.scores {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.shares {
  margin-top: 6px;
}
.shares span + span::before {
  content: '、';
}
.ops {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
@media (max-width: 768px) {
  .refs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
