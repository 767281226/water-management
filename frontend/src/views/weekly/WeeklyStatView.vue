<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { weeklyApi, type HistoryRow, type StatResponse, type StatRow } from '@/api/weekly'
import { commonApi } from '@/api/common'
import type { DeptNode } from '@/api/system'
import { useAuthStore } from '@/stores/auth'
import { mondayOf } from '@/utils/week'
import WeekSwitcher from '@/components/WeekSwitcher.vue'
import WeeklyDetailDialog from './WeeklyDetailDialog.vue'

const auth = useAuthStore()
const canTeam = computed(() => auth.user?.dataScope !== 'SELF')
const tab = ref<'team' | 'mine'>(canTeam.value ? 'team' : 'mine')

// 我的周报
const history = ref<HistoryRow[]>([])
const historyTotal = ref(0)
const hq = reactive({ page: 1, size: 20 })

// 填报统计
const week = ref(mondayOf())
const deptId = ref<number | null>(null)
const depts = ref<DeptNode[]>([])
const stat = ref<StatResponse | null>(null)
const statusFilter = ref<'' | 'NO_PLAN' | 'NO_REPORT' | 'OVERDUE'>('')
const loading = ref(false)

const detailVisible = ref(false)
const detailId = ref<number | null>(null)

const rows = computed(() => {
  const all = stat.value?.rows ?? []
  switch (statusFilter.value) {
    case 'NO_PLAN': return all.filter((r) => r.planStatus !== 'SUBMITTED')
    case 'NO_REPORT': return all.filter((r) => r.reportStatus !== 'SUBMITTED')
    case 'OVERDUE': return all.filter((r) => r.planOverdue || r.reportOverdue)
    default: return all
  }
})

async function loadHistory() {
  loading.value = true
  try {
    const r = await weeklyApi.history(hq.page, hq.size)
    history.value = r.records
    historyTotal.value = r.total
  } finally {
    loading.value = false
  }
}

async function loadStat() {
  loading.value = true
  try {
    stat.value = await weeklyApi.stat(week.value, deptId.value)
  } finally {
    loading.value = false
  }
}

function load() {
  if (tab.value === 'team') loadStat()
  else loadHistory()
}

function view(id: number | null) {
  if (!id) return
  detailId.value = id
  detailVisible.value = true
}

function planTag(r: StatRow) {
  if (r.planStatus === 'NONE') return { text: '未填', type: 'danger' as const }
  if (r.planStatus === 'RETURNED') return { text: '已退回', type: 'warning' as const }
  return { text: '已提交', type: 'success' as const }
}

watch([week, deptId], loadStat)
watch(tab, load)

onMounted(async () => {
  load()
  if (auth.user?.dataScope === 'ALL') depts.value = await commonApi.depts()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>周报查询统计</h3>
        <p>{{ canTeam ? '查看填报情况、点评和退回周报；' : '' }}查看自己的历史周报</p>
      </div>
    </div>
    <div class="card">
      <el-tabs v-model="tab">
        <el-tab-pane v-if="canTeam" :label="auth.user?.dataScope === 'ALL' ? '全公司填报统计' : '本部门填报统计'" name="team" />
        <el-tab-pane label="我的周报" name="mine" />
      </el-tabs>

      <template v-if="tab === 'team'">
        <div class="toolbar">
          <WeekSwitcher v-model="week" :label="stat?.week.label" />
          <div class="spacer" />
          <el-tree-select v-if="auth.user?.dataScope === 'ALL'" v-model="deptId" :data="depts" :props="{ label: 'name', children: 'children' }"
            node-key="id" check-strictly default-expand-all clearable placeholder="全部部门" style="width: 160px" />
          <el-select v-model="statusFilter" placeholder="全部人员" clearable style="width: 140px">
            <el-option label="计划未提交" value="NO_PLAN" />
            <el-option label="完成情况未提交" value="NO_REPORT" />
            <el-option label="有逾期" value="OVERDUE" />
          </el-select>
        </div>
        <el-row v-if="stat" :gutter="12" class="tiles">
          <el-col :xs="12" :sm="6"><div class="tile"><span>应填人数</span><b>{{ stat.total }}</b></div></el-col>
          <el-col :xs="12" :sm="6"><div class="tile"><span>计划已提交</span><b>{{ stat.planSubmitted }}<small> / {{ stat.total }}</small></b><em>逾期 {{ stat.planOverdue }} 人</em></div></el-col>
          <el-col :xs="12" :sm="6"><div class="tile"><span>完成情况已提交</span><b>{{ stat.reportSubmitted }}<small> / {{ stat.total }}</small></b></div></el-col>
          <el-col :xs="12" :sm="6"><div class="tile"><span>平均完成率</span><b>{{ stat.avgRate ?? '—' }}<small>%</small></b></div></el-col>
        </el-row>
        <el-table v-loading="loading" :data="rows" border stripe>
          <el-table-column label="姓名" min-width="100">
            <template #default="{ row }"><b>{{ row.userName }}</b> <span class="muted small">{{ row.employeeNo }}</span></template>
          </el-table-column>
          <el-table-column label="部门" min-width="110"><template #default="{ row }">{{ row.deptName || '—' }}</template></el-table-column>
          <el-table-column label="周计划" width="150">
            <template #default="{ row }">
              <el-tag :type="planTag(row as StatRow).type" size="small">{{ planTag(row as StatRow).text }}</el-tag>
              <el-tag v-if="row.planOverdue" type="warning" size="small" effect="plain" style="margin-left: 4px">逾期</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="完成情况" width="150">
            <template #default="{ row }">
              <template v-if="row.planId">
                <el-tag :type="row.reportStatus === 'SUBMITTED' ? 'success' : 'info'" size="small">{{ row.reportStatus === 'SUBMITTED' ? '已提交' : '未提交' }}</el-tag>
                <el-tag v-if="row.reportOverdue" type="warning" size="small" effect="plain" style="margin-left: 4px">逾期</el-tag>
              </template>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="完成率" width="140">
            <template #default="{ row }">
              <div v-if="row.completionRate !== null" class="rate"><el-progress :percentage="row.completionRate" :stroke-width="6" :show-text="false" style="flex: 1" />{{ row.completionRate }}%</div>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="计划" width="110">
            <template #default="{ row }">{{ row.planId ? `${row.itemCount} 项 / ${Number(row.planDays)} 天` : '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.planId" link type="primary" @click="view(row.planId)">{{ auth.hasPerm('weekly:stat:comment') && row.userId !== auth.user?.id ? '查看 / 点评' : '查看' }}</el-button>
              <el-tag v-if="row.commented" size="small" type="info" effect="plain">已点评</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template v-else>
        <el-table v-loading="loading" :data="history" border stripe>
          <el-table-column label="周次" min-width="200"><template #default="{ row }"><b>{{ row.week.label }}</b></template></el-table-column>
          <el-table-column label="计划" width="140">
            <template #default="{ row }">
              {{ row.itemCount }} 项 / {{ Number(row.planDays) }} 天
              <el-tag v-if="row.planOverdue" type="warning" size="small" effect="plain">逾期</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag v-if="row.planStatus === 'RETURNED'" type="danger" size="small">被退回</el-tag>
              <el-tag v-else :type="row.reportStatus === 'SUBMITTED' ? 'success' : 'info'" size="small">{{ row.reportStatus === 'SUBMITTED' ? '已完成填报' : '待填完成情况' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="完成率" width="140">
            <template #default="{ row }">
              <div v-if="row.completionRate !== null" class="rate"><el-progress :percentage="row.completionRate" :stroke-width="6" :show-text="false" style="flex: 1" />{{ row.completionRate }}%</div>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="负责人点评" min-width="180"><template #default="{ row }"><span class="small">{{ row.comment || '—' }}</span></template></el-table-column>
          <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="primary" @click="view(row.id)">查看</el-button></template></el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="hq.page" v-model:page-size="hq.size" :total="historyTotal" layout="total, prev, pager, next" background @current-change="loadHistory" />
        </div>
      </template>
    </div>
    <WeeklyDetailDialog v-model="detailVisible" :plan-id="detailId" @changed="load" />
  </div>
</template>

<style scoped>
.tiles {
  margin-bottom: 14px;
}
.tile {
  background: #f7f9fb;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.tile span {
  color: #5a6577;
  font-size: 13px;
}
.tile b {
  font-size: 22px;
}
.tile small {
  font-size: 13px;
  color: #8a94a6;
  font-weight: normal;
}
.tile em {
  font-style: normal;
  font-size: 12px;
  color: #8a94a6;
}
.rate {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
