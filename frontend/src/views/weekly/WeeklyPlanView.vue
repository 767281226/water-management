<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { weeklyApi, type MineResponse } from '@/api/weekly'
import { projectApi, type ProjectOption } from '@/api/project'
import { fmtTime } from '@/utils/format'
import { fmtDeadline, mondayOf } from '@/utils/week'
import WeekSwitcher from '@/components/WeekSwitcher.vue'
import WeeklyNav from './WeeklyNav.vue'

/** -1 表示"其他工作（非项目）"，null 表示未选择 */
const OTHER = -1
interface DraftItem {
  projectId: number | null
  content: string
  target: string
  planDays: number
  remark: string
}

const route = useRoute()
const router = useRouter()
const week = ref(typeof route.query.week === 'string' ? mondayOf(route.query.week) : mondayOf())
const data = ref<MineResponse | null>(null)
const projects = ref<ProjectOption[]>([])
const items = ref<DraftItem[]>([])
const loading = ref(false)
const saving = ref(false)

const editable = computed(() => !!data.value?.canEditPlan)
const totalDays = computed(() => items.value.reduce((s, i) => s + (Number(i.planDays) || 0), 0))
const blank = (): DraftItem => ({ projectId: null, content: '', target: '', planDays: 1, remark: '' })

async function load() {
  loading.value = true
  try {
    data.value = await weeklyApi.mine(week.value)
    const plan = data.value.plan
    items.value = plan
      ? plan.items.filter((i) => !i.extra).map((i) => ({
          projectId: i.projectId ?? OTHER, content: i.content, target: i.target || '', planDays: Number(i.planDays) || 1, remark: i.remark || ''
        }))
      : [blank()]
  } finally {
    loading.value = false
  }
}

watch(week, (w) => {
  router.replace({ query: { week: w } })
  load()
})

function carryOver() {
  const todo = data.value?.lastWeekUnfinished ?? []
  if (!todo.length) return ElMessage.info('上周没有未完成的事项')
  const fillable = new Set(projects.value.map((p) => p.id))
  items.value = items.value.filter((i) => i.content.trim()).concat(
    todo.map((i) => ({
      projectId: i.projectId && fillable.has(i.projectId) ? i.projectId : i.projectId ? null : OTHER,
      content: `${i.content}（续）`,
      target: i.target || '',
      planDays: Number(i.planDays) || 1,
      remark: ''
    }))
  )
  ElMessage.success(`已带入 ${todo.length} 项上周未完成的事项`)
}

async function submit() {
  for (const [i, it] of items.value.entries()) {
    if (it.projectId === null) return ElMessage.warning(`事项 ${i + 1}：请选择关联项目或"其他工作"`)
    if (!it.content.trim()) return ElMessage.warning(`事项 ${i + 1}：请填写工作内容`)
    if (!(it.planDays >= 0.5)) return ElMessage.warning(`事项 ${i + 1}：计划工时至少 0.5 天`)
  }
  saving.value = true
  try {
    await weeklyApi.savePlan(week.value, items.value.map((i) => ({ ...i, projectId: i.projectId === OTHER ? null : i.projectId })))
    ElMessage.success(data.value?.plan ? '计划已更新' : '本周计划提交成功')
    load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  load()
  projects.value = await projectApi.fillable()
})
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-head">
      <div>
        <h3>周计划填报</h3>
        <p v-if="data">{{ data.week.label }} · 截止 {{ fmtDeadline(data.week.planDeadline) }}</p>
      </div>
      <WeeklyNav active="plan" :week="week" />
    </div>
    <div class="toolbar"><WeekSwitcher v-model="week" /></div>

    <template v-if="data">
      <el-alert v-if="data.plan?.planStatus === 'RETURNED'" type="error" :closable="false" show-icon style="margin-bottom: 14px"
        :title="`周报被退回：${data.plan.returnReason}`" description="请修改后重新提交计划，再填写完成情况。" />
      <el-alert v-else-if="data.plan" :type="data.plan.planOverdue ? 'warning' : 'success'" :closable="false" show-icon style="margin-bottom: 14px"
        :title="`计划已于 ${fmtTime(data.plan.planSubmittedAt)} 提交${data.plan.planOverdue ? '（逾期提交，将计入考评）' : ''}`"
        :description="editable ? '填写完成情况前还可以修改。' : data.lockedReason || ''" />
      <el-alert v-else-if="!editable" type="info" :closable="false" show-icon style="margin-bottom: 14px" :title="data.lockedReason || '不能填写'" />
      <el-alert v-else type="info" :closable="false" show-icon style="margin-bottom: 14px"
        title="请填写本周工作计划，一个项目一行；会议、投标、培训等可选择「其他工作」。" />
      <el-alert v-if="data.plan?.comment" type="info" :closable="false" style="margin-bottom: 14px"
        :title="`${data.plan.commentByName} 点评：${data.plan.comment}`" />

      <div class="card">
        <div class="card-head">
          <h4>工作计划 <small class="muted">共 {{ items.length }} 项 · 计划工时 {{ totalDays }} 天</small></h4>
          <el-button v-if="editable" size="small" icon="Refresh" :disabled="!data.lastWeekUnfinished.length" @click="carryOver">
            带入上周未完成（{{ data.lastWeekUnfinished.length }}）
          </el-button>
        </div>

        <div v-for="(it, i) in items" :key="i" class="item">
          <div class="item-head">
            <span class="no">事项 {{ i + 1 }}</span>
            <el-button v-if="editable && items.length > 1" link type="danger" icon="Delete" @click="items.splice(i, 1)" />
          </div>
          <div class="grid">
            <el-form-item label="关联项目" required>
              <el-select v-model="it.projectId" :disabled="!editable" filterable placeholder="请选择" style="width: 100%">
                <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
                <el-option label="其他工作（会议 / 投标 / 培训等）" :value="OTHER" />
              </el-select>
            </el-form-item>
            <el-form-item label="工作内容" required>
              <el-input v-model="it.content" :disabled="!editable" maxlength="255" placeholder="如：水文分析计算" />
            </el-form-item>
            <el-form-item label="计划目标 / 成果">
              <el-input v-model="it.target" :disabled="!editable" maxlength="255" placeholder="如：完成计算书" />
            </el-form-item>
            <el-form-item label="计划工时（天）" required>
              <el-input-number v-model="it.planDays" :disabled="!editable" :min="0.5" :max="7" :step="0.5" style="width: 100%" />
            </el-form-item>
            <el-form-item label="备注" class="full">
              <el-input v-model="it.remark" :disabled="!editable" maxlength="255" placeholder="选填" />
            </el-form-item>
          </div>
        </div>
        <el-empty v-if="!items.length" description="本周没有计划" :image-size="60" />

        <template v-if="editable">
          <el-button class="add" icon="Plus" @click="items.push(blank())">添加一项</el-button>
          <div class="foot">
            <span class="muted small">提交后部门负责人可以查看；完成情况提交前可以修改</span>
            <el-button type="primary" :loading="saving" @click="submit">{{ data.plan ? '保存修改' : '提交计划' }}</el-button>
          </div>
        </template>
      </div>
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
  margin-bottom: 4px;
}
.no {
  font-weight: 600;
  color: var(--hy-primary);
}
.grid {
  display: grid;
  grid-template-columns: 2fr 3fr 2fr 140px;
  gap: 0 12px;
}
.grid .full {
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
.add {
  width: 100%;
  border-style: dashed;
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
  .foot {
    position: sticky;
    bottom: 0;
    background: #fff;
    padding-bottom: 8px;
  }
}
</style>
