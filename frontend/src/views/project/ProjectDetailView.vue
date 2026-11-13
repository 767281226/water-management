<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PROJECT_STATUS, STATUS_TRANSITIONS, projectApi, type ProjectDetail, type ProjectStatus } from '@/api/project'
import { commonApi } from '@/api/common'
import type { DeptNode, UserOption } from '@/api/system'
import { fmtTime } from '@/utils/format'
import { fmtDays } from '@/utils/week'
import ProjectFormDialog from './ProjectFormDialog.vue'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const data = ref<ProjectDetail | null>(null)
const loading = ref(false)
const dialog = ref(false)
const users = ref<UserOption[]>([])
const depts = ref<DeptNode[]>([])
const types = ref<string[]>([])

const p = computed(() => data.value?.project)
const nextStatuses = computed(() => (p.value ? STATUS_TRANSITIONS[p.value.status] : []))
const STATUS_ACTION: Partial<Record<ProjectStatus, string>> = {
  IN_PROGRESS: '开始 / 恢复进行',
  PAUSED: '暂停',
  COMPLETED: '标记完成',
  TERMINATED: '终止'
}

async function load() {
  loading.value = true
  try {
    data.value = await projectApi.detail(id)
  } finally {
    loading.value = false
  }
}

async function openEdit() {
  if (!users.value.length) [users.value, depts.value, types.value] = await Promise.all([commonApi.users(), commonApi.depts(), projectApi.types()])
  dialog.value = true
}

async function changeStatus(s: ProjectStatus) {
  const tip: Partial<Record<ProjectStatus, string>> = {
    COMPLETED: '标记完成后，项目负责人可以申请存档。',
    TERMINATED: '终止后项目不能再填报周报，且不可恢复。',
    IN_PROGRESS: p.value?.status === 'COMPLETED' ? '重新进行将清除实际完成日期。' : ''
  }
  await ElMessageBox.confirm(`确定将项目改为「${PROJECT_STATUS[s].label}」吗？${tip[s] || ''}`, '修改状态', { type: 'warning' })
  await projectApi.setStatus(id, s)
  ElMessage.success('状态已更新')
  load()
}

async function applyArchive() {
  await ElMessageBox.confirm('请确认项目成果已提交并完成资料归档。申请后由部门负责人确认存档，存档后项目进入当月考评。', '申请存档', { type: 'info' })
  await projectApi.applyArchive(id)
  ElMessage.success('已提交存档申请')
  load()
}

async function confirmArchive() {
  await ElMessageBox.confirm('确认存档后项目不能再修改，并进入当月考评清单。', '确认存档', { type: 'warning' })
  await projectApi.confirmArchive(id)
  ElMessage.success('项目已存档')
  load()
}

async function rejectArchive() {
  const { value } = await ElMessageBox.prompt('请填写退回原因，项目负责人可以看到', '退回存档申请', {
    inputValidator: (v) => (!!v && v.trim().length > 0) || '请填写退回原因',
    inputPlaceholder: '如：成果资料未归档'
  })
  await projectApi.rejectArchive(id, value)
  ElMessage.success('已退回')
  load()
}

async function remove() {
  await ElMessageBox.confirm(`确定删除项目「${p.value?.name}」吗？`, '删除项目', { type: 'warning' })
  await projectApi.remove(id)
  ElMessage.success('已删除')
  router.replace('/project/ledger')
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <template v-if="p && data">
      <div class="page-head">
        <div>
          <el-link :underline="false" type="primary" @click="router.push('/project/ledger')">‹ 返回项目台账</el-link>
          <h3 style="margin-top: 6px">{{ p.name }}</h3>
          <p>
            {{ p.code }} ·
            <el-tag :type="PROJECT_STATUS[p.status].type || undefined" size="small">{{ p.statusLabel }}</el-tag>
            <el-tag v-if="p.archiveApplied" type="warning" size="small" effect="plain" style="margin-left: 4px">存档申请待确认</el-tag>
          </p>
        </div>
        <div class="btns">
          <el-button v-if="p.actions.edit" icon="Edit" @click="openEdit">编辑</el-button>
          <el-dropdown v-if="p.actions.changeStatus && nextStatuses.length" @command="changeStatus">
            <el-button>修改状态<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="s in nextStatuses" :key="s" :command="s">{{ STATUS_ACTION[s] }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="p.actions.applyArchive" type="primary" @click="applyArchive">申请存档</el-button>
          <template v-if="p.actions.confirmArchive">
            <el-button type="warning" plain @click="rejectArchive">退回</el-button>
            <el-button type="success" @click="confirmArchive">确认存档</el-button>
          </template>
          <el-button v-if="p.actions.delete" type="danger" plain @click="remove">删除</el-button>
        </div>
      </div>

      <el-alert v-if="p.status === 'COMPLETED' && !p.archiveApplied && p.archiveRejectReason" type="warning" :closable="false" show-icon
        :title="`存档申请被退回：${p.archiveRejectReason}`" style="margin-bottom: 16px" />
      <el-alert v-else-if="p.actions.applyArchive" type="info" :closable="false" show-icon
        title="项目已完成。成果提交、资料归档后，请申请存档；部门负责人确认后进入当月考评。" style="margin-bottom: 16px" />

      <div class="card" style="margin-bottom: 16px">
        <el-descriptions :column="3" border class="desc">
          <el-descriptions-item label="项目类型">{{ p.type }}</el-descriptions-item>
          <el-descriptions-item label="委托单位">{{ p.client || '—' }}</el-descriptions-item>
          <el-descriptions-item label="合同金额">{{ p.contractAmount !== null ? `${p.contractAmount} 万元` : '—' }}</el-descriptions-item>
          <el-descriptions-item label="项目负责人">{{ p.leaderName }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ p.deptName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="参与人员">{{ p.members.map((m) => m.name).join('、') || '—' }}</el-descriptions-item>
          <el-descriptions-item label="开始日期">{{ p.startDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="计划完成">{{ p.planEndDate }}</el-descriptions-item>
          <el-descriptions-item label="实际完成">{{ p.actualEndDate || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="p.archivedAt" label="存档">{{ fmtTime(p.archivedAt) }}（{{ p.archivedByName }}）</el-descriptions-item>
          <el-descriptions-item label="备注" :span="p.archivedAt ? 2 : 3">{{ p.remark || '—' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <el-row :gutter="16" class="stats">
        <el-col :xs="12" :sm="6"><div class="card stat"><span>周报记录</span><b>{{ data.weeklyCount }}<small> 条</small></b></div></el-col>
        <el-col :xs="12" :sm="6"><div class="card stat"><span>累计工时</span><b>{{ Number(data.totalDays) }}<small> 人·天</small></b></div></el-col>
        <el-col :xs="12" :sm="6"><div class="card stat"><span>参与人数</span><b>{{ data.memberCount }}<small> 人</small></b></div></el-col>
        <el-col :xs="12" :sm="6">
          <div class="card stat">
            <span>考评得分</span>
            <b v-if="data.score?.total !== null && data.score?.total !== undefined">{{ Number(data.score.total).toFixed(1) }}<small> 分 · {{ data.score.month }}</small></b>
            <b v-else>—<small> {{ data.score ? (data.score.status === 'PENDING' ? '待打分' : '待发布') : '存档后考评' }}</small></b>
          </div>
        </el-col>
      </el-row>

      <div class="card">
        <h4>项目周报动态 <small class="muted">来自成员每周的计划和完成情况（最近 60 条）</small></h4>
        <el-timeline v-if="data.recentEntries.length">
          <el-timeline-item v-for="e in data.recentEntries" :key="e.itemId" :timestamp="`${e.weekStart} 当周 · ${e.userName}`" placement="top">
            <div>
              <el-tag v-if="e.extra" size="small" type="warning" effect="plain">计划外</el-tag>
              {{ e.content }} <span v-if="e.target" class="muted">— {{ e.target }}</span>
            </div>
            <div class="small muted">
              计划 {{ fmtDays(e.planDays) }}
              <template v-if="e.reported">
                · 实际 {{ fmtDays(e.actualDays) }}
                <template v-if="!e.extra">
                  · 完成 <b :style="{ color: (e.completion ?? 0) < 100 ? 'var(--el-color-warning)' : 'var(--el-color-success)' }">{{ e.completion }}%</b>
                </template>
                <span v-if="e.reason"> · 原因：{{ e.reason }}</span>
              </template>
              <template v-else> · 本周进行中</template>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无周报记录" :image-size="80" />
      </div>
      <ProjectFormDialog v-model="dialog" :project="p" :users="users" :depts="depts" :types="types" @saved="load" />
    </template>
  </div>
</template>

<style scoped>
.btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.stats .stat {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.stat span {
  color: #5a6577;
  font-size: 13px;
}
.stat b {
  font-size: 24px;
}
.stat small {
  font-size: 12px;
  color: #8a94a6;
  font-weight: normal;
}
h4 {
  margin: 0 0 16px;
}
@media (max-width: 768px) {
  .desc :deep(.el-descriptions__table) {
    table-layout: fixed;
  }
}
</style>
