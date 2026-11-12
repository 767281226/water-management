<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { weeklyApi, type ExtraItemForm, type MineResponse, type WeeklyItem } from '@/api/weekly'
import { projectApi, type ProjectOption } from '@/api/project'
import { fmtTime } from '@/utils/format'
import { fmtDays, fmtDeadline, mondayOf } from '@/utils/week'
import WeekSwitcher from '@/components/WeekSwitcher.vue'
import WeeklyNav from './WeeklyNav.vue'

const OTHER = -1
interface DraftReport {
  item: WeeklyItem
  completion: number
  actualContent: string
  actualDays: number | null
  reason: string
}

const route = useRoute()
const router = useRouter()
const week = ref(typeof route.query.week === 'string' ? mondayOf(route.query.week) : mondayOf())
const data = ref<MineResponse | null>(null)
const projects = ref<ProjectOption[]>([])
const rows = ref<DraftReport[]>([])
const extras = ref<(ExtraItemForm & { projectId: number | null })[]>([])
const loading = ref(false)
const saving = ref(false)

const plan = computed(() => data.value?.plan ?? null)
const reported = computed(() => plan.value?.reportStatus === 'SUBMITTED')
const editable = computed(() => !!data.value?.canSubmitReport)
const rate = computed(() => (rows.value.length ? Math.round(rows.value.reduce((s, r) => s + r.completion, 0) / rows.value.length) : 0))

async function load() {
  loading.value = true
  try {
    data.value = await weeklyApi.mine(week.value)
    const items = data.value.plan?.items ?? []
    rows.value = items.filter((i) => !i.extra).map((i) => ({
      item: i,
      completion: i.completion ?? 100,
      actualContent: i.actualContent || '',
      actualDays: i.actualDays !== null ? Number(i.actualDays) : i.planDays !== null ? Number(i.planDays) : null,
      reason: i.reason || ''
    }))
    extras.value = items.filter((i) => i.extra).map((i) => ({ projectId: i.projectId ?? OTHER, content: i.content, actualDays: Number(i.actualDays) || 0.5 }))
  } finally {
    loading.value = false
  }
}

watch(week, (w) => {
  router.replace({ query: { week: w } })
  load()
})

async function submit() {
  for (const [i, r] of rows.value.entries()) {
    if (r.completion < 100 && !r.reason.trim()) return ElMessage.warning(`事项 ${i + 1} 未全部完成，请填写未完成原因`)
  }
  for (const [i, e] of extras.value.entries()) {
    if (!e.content.trim()) return ElMessage.warning(`计划外工作 ${i + 1}：请填写工作内容`)
  }
  saving.value = true
  try {
    const res = await weeklyApi.submitReport(
      week.value,
      rows.value.map((r) => ({ id: r.item.id, completion: r.completion, actualContent: r.actualContent, actualDays: r.actualDays, reason: r.reason })),
      extras.value.map((e) => ({ ...e, projectId: e.projectId === OTHER ? null : e.projectId }))
    )
    ElMessage.success(`提交成功，本周完成率 ${res.completionRate}%`)
    load()
  } finally {
    saving.value = false
  }
}

const pname = (i: WeeklyItem) => i.projectName || '其他工作'

onMounted(async () => {
  load()
  projects.value = await projectApi.fillable()
})
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-head">
      <div>
        <h3>周完成情况填报</h3>
        <p v-if="data">{{ data.week.label }} · 截止 {{ fmtDeadline(data.week.reportDeadline) }}</p>
      </div>
      <WeeklyNav active="report" :week="week" />
    </div>
    <div class="toolbar"><WeekSwitcher v-model="week" /></div>

    <template v-if="data">
      <div v-if="!plan" class="card">
        <el-empty description="该周还没有提交计划，完成情况需要对照计划填写">
          <el-button type="primary" @click="router.push({ path: '/weekly/plan', query: { week } })">去填写周计划</el-button>
        </el-empty>
      </div>
      <template v-else>
        <el-alert v-if="plan.planStatus === 'RETURNED'" type="error" :closable="false" show-icon style="margin-bottom: 14px"
          :title="`周报被退回：${plan.returnReason}`">
          <template #default>请先 <el-link type="primary" @click="router.push({ path: '/weekly/plan', query: { week } })">修改并重新提交计划</el-link>，再填写完成情况。</template>
        </el-alert>
        <el-alert v-else-if="reported" :type="plan.reportOverdue ? 'warning' : 'success'" :closable="false" show-icon style="margin-bottom: 14px"
          :title="`完成情况已于 ${fmtTime(plan.reportSubmittedAt)} 提交，本周完成率 ${plan.completionRate}%${plan.reportOverdue ? '（逾期提交）' : ''}`" />
        <el-alert v-else-if="!editable" type="info" :closable="false" show-icon style="margin-bottom: 14px" :title="data.lockedReason || ''" />
        <el-alert v-else type="info" :closable="false" show-icon style="margin-bottom: 14px"
          title="系统已带出本周计划，请逐条填写完成比例；低于 100% 时需要填写未完成原因。" />
        <el-alert v-if="plan.comment" type="info" :closable="false" style="margin-bottom: 14px" :title="`${plan.commentByName} 点评：${plan.comment}`" />

        <div class="card">
          <div class="card-head">
            <h4>计划事项完成情况</h4>
            <div>本周完成率 <span class="rate">{{ reported ? plan.completionRate : rate }}%</span></div>
          </div>
          <div v-for="(r, i) in rows" :key="r.item.id" class="item">
            <div class="item-head">
              <span class="no">事项 {{ i + 1 }}</span>
              <el-tag size="small" effect="plain">{{ pname(r.item) }}</el-tag>
            </div>
            <div class="plan-ro">
              <span>计划内容</span><b>{{ r.item.content }}</b>
              <span>计划目标</span><b>{{ r.item.target || '—' }}</b>
              <span>计划工时</span><b>{{ fmtDays(r.item.planDays) }}</b>
            </div>
            <div class="grid">
              <el-form-item label="完成比例" class="span2" required>
                <div class="pct">
                  <el-slider v-model="r.completion" :step="10" :disabled="!editable" show-stops style="flex: 1" />
                  <el-input-number v-model="r.completion" :min="0" :max="100" :step="10" :disabled="!editable" controls-position="right" style="width: 110px" />
                </div>
              </el-form-item>
              <el-form-item label="实际工时（天）">
                <el-input-number v-model="r.actualDays" :min="0" :max="7" :step="0.5" :disabled="!editable" style="width: 100%" />
              </el-form-item>
              <el-form-item label="实际完成内容" class="span2">
                <el-input v-model="r.actualContent" :disabled="!editable" maxlength="255" placeholder="如：已完成计算书初稿" />
              </el-form-item>
              <el-form-item :label="r.completion < 100 ? '未完成原因（必填）' : '未完成原因'" class="span3" :required="r.completion < 100">
                <el-input v-model="r.reason" :disabled="!editable || r.completion >= 100" maxlength="255" placeholder="完成比例低于 100% 时必填" />
              </el-form-item>
            </div>
          </div>
        </div>

        <div class="card" style="margin-top: 16px">
          <div class="card-head">
            <h4>计划外工作 <small class="muted">本周临时增加的工作</small></h4>
            <el-button v-if="editable" size="small" icon="Plus" @click="extras.push({ projectId: OTHER, content: '', actualDays: 0.5 })">添加</el-button>
          </div>
          <div v-for="(e, i) in extras" :key="i" class="item extra">
            <div class="grid">
              <el-form-item label="关联项目">
                <el-select v-model="e.projectId" :disabled="!editable" filterable style="width: 100%">
                  <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
                  <el-option label="其他工作" :value="OTHER" />
                </el-select>
              </el-form-item>
              <el-form-item label="工作内容" class="span2" required>
                <el-input v-model="e.content" :disabled="!editable" maxlength="255" placeholder="如：配合投标技术标编写" />
              </el-form-item>
              <el-form-item label="工时（天）">
                <div class="pct">
                  <el-input-number v-model="e.actualDays" :min="0" :max="7" :step="0.5" :disabled="!editable" style="flex: 1" />
                  <el-button v-if="editable" link type="danger" icon="Delete" @click="extras.splice(i, 1)" />
                </div>
              </el-form-item>
            </div>
          </div>
          <div v-if="!extras.length" class="muted small">无计划外工作</div>
          <div v-if="editable" class="foot">
            <span class="muted small">提交后计划锁定；如需修改请联系部门负责人退回</span>
            <el-button type="primary" :loading="saving" @click="submit">提交完成情况</el-button>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 8px;
  flex-wrap: wrap;
}
h4 {
  margin: 0;
}
.rate {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-color-primary-dark-2);
}
.item {
  border: 1px solid #e3e7ee;
  border-radius: 10px;
  padding: 12px 14px 0;
  margin-bottom: 12px;
}
.item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.no {
  font-weight: 600;
  color: var(--hy-primary);
}
.plan-ro {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 12px;
  background: #f7f9fb;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  margin-bottom: 12px;
}
.plan-ro span {
  color: #8a94a6;
}
.plan-ro b {
  font-weight: 500;
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 12px;
}
.span2 {
  grid-column: span 2;
}
.span3 {
  grid-column: 1 / -1;
}
.grid :deep(.el-form-item) {
  flex-direction: column;
  align-items: stretch;
  margin-bottom: 12px;
}
.grid :deep(.el-form-item__label) {
  justify-content: flex-start;
  line-height: 22px;
  height: auto;
  font-size: 12px;
}
.pct {
  display: flex;
  gap: 14px;
  align-items: center;
  width: 100%;
}
.foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #eef1f5;
  flex-wrap: wrap;
}
@media (max-width: 900px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .span2,
  .span3 {
    grid-column: auto;
  }
  .foot {
    position: sticky;
    bottom: 0;
    background: #fff;
    padding-bottom: 8px;
  }
}
</style>
