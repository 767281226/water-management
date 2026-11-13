<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { annualApi, type AnnualResponse, type PersonRow } from '@/api/annual'
import { commonApi } from '@/api/common'
import type { DeptNode } from '@/api/system'
import { useAuthStore } from '@/stores/auth'
import BarChart from '@/components/BarChart.vue'

const auth = useAuthStore()
const thisYear = new Date().getFullYear()
const year = ref(thisYear)
const years = Array.from({ length: 5 }, (_, i) => thisYear - i)
const deptId = ref<number | null>(null)
const depts = ref<DeptNode[]>([])
const data = ref<AnnualResponse | null>(null)
const loading = ref(false)

const isSelf = computed(() => auth.user?.dataScope === 'SELF')
const monthChart = computed(() => (data.value?.months ?? []).map((m) => ({
  label: `${m.month}月`,
  value: m.archived,
  tip: `${m.month}月：存档 ${m.archived} 个${m.avgScore !== null ? `，平均分 ${m.avgScore}` : ''}`
})))
const rankChart = computed(() => (data.value?.persons ?? []).filter((p) => p.avg !== null).slice(0, 15).map((p) => ({
  label: p.name,
  value: Number(p.avg),
  tip: `第 ${p.rank} 名 · ${p.name}：${p.avg} 分（${p.projects} 个项目）`
})))
const myTrend = computed(() => (data.value?.me?.monthly ?? []).map((v, i) => ({ label: `${i + 1}月`, value: v === null ? null : Number(v) })))

async function load() {
  loading.value = true
  try {
    data.value = await annualApi.summary(year.value, deptId.value)
  } finally {
    loading.value = false
  }
}

function sortNum(key: keyof PersonRow) {
  return (a: PersonRow, b: PersonRow) => Number(a[key] ?? -1) - Number(b[key] ?? -1)
}

/** 导出个人年度汇总（CSV，Excel 可直接打开） */
function exportCsv() {
  const d = data.value
  if (!d) return
  const head = ['排名', '姓名', '工号', '部门', '存档项目', '累计工时(天)', '周报填报率(%)', ...Array.from({ length: 12 }, (_, i) => `${i + 1}月`), '年度平均分']
  const rows = d.persons.map((p) => [p.rank ?? '', p.name, p.employeeNo, p.deptName ?? '', p.projects, Number(p.hours), p.fillRate ?? '',
    ...p.monthly.map((v) => v ?? ''), p.avg ?? ''])
  const csv = [head, ...rows].map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\r\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${d.year}年度个人考评汇总.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

watch([year, deptId], load)
onMounted(async () => {
  load()
  if (auth.user?.dataScope === 'ALL') depts.value = await commonApi.depts()
})
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-head">
      <div>
        <h3>年度汇总 · {{ year }} 年</h3>
        <p>统计范围：{{ data?.scope ?? '—' }} · 只统计已发布的考评结果</p>
      </div>
      <div class="btns">
        <el-select v-model="year" style="width: 110px"><el-option v-for="y in years" :key="y" :label="`${y} 年`" :value="y" /></el-select>
        <el-tree-select v-if="auth.user?.dataScope === 'ALL'" v-model="deptId" :data="depts" :props="{ label: 'name', children: 'children' }"
          node-key="id" check-strictly default-expand-all clearable placeholder="全部部门" style="width: 160px" />
        <el-button v-if="!isSelf" icon="Download" @click="exportCsv">导出 Excel（CSV）</el-button>
      </div>
    </div>

    <template v-if="data">
      <el-row :gutter="16">
        <el-col :xs="12" :sm="6"><div class="card tile"><span>存档项目</span><b>{{ data.tiles.archivedProjects }}<small> 个</small></b><em>已发布考评 {{ data.tiles.publishedScores }} 个</em></div></el-col>
        <el-col :xs="12" :sm="6"><div class="card tile"><span>项目平均得分</span><b>{{ data.tiles.avgScore ?? '—' }}<small> 分</small></b><em>已发布的考评</em></div></el-col>
        <el-col :xs="12" :sm="6"><div class="card tile"><span>按时完成率</span><b>{{ data.tiles.onTimeRate ?? '—' }}<small>%</small></b><em>实际完成 ≤ 计划完成</em></div></el-col>
        <el-col :xs="12" :sm="6"><div class="card tile"><span>周报填报率</span><b>{{ data.tiles.fillRate ?? '—' }}<small>%</small></b><em>{{ data.tiles.people }} 人应填</em></div></el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :md="isSelf ? 24 : 12">
          <div class="card block">
            <h4>每月存档项目数 <small>单位：个</small></h4>
            <BarChart :data="monthChart" unit="个" />
          </div>
        </el-col>
        <el-col v-if="!isSelf" :xs="24" :md="12">
          <div class="card block">
            <h4>个人年度平均分排名 <small>前 15 名 · 单位：分</small></h4>
            <BarChart v-if="rankChart.length" :data="rankChart" horizontal :max="100" unit="分" :highlight="auth.user?.realName" />
            <el-empty v-else description="暂无已发布的考评" :image-size="70" />
          </div>
        </el-col>
      </el-row>

      <div v-if="data.me && (isSelf || data.me.avg !== null)" class="card block">
        <h4>我的年度考评 <small>排名 {{ data.me.rank ?? '—' }} / {{ data.persons.filter((p) => p.avg !== null).length }} · 平均 {{ data.me.avg ?? '—' }} 分 · 参与存档项目 {{ data.me.projects }} 个 · 工时 {{ Number(data.me.hours) }} 天 · 周报填报率 {{ data.me.fillRate ?? '—' }}%</small></h4>
        <BarChart :data="myTrend" :max="100" unit="分" />
        <div class="legend-note">每月得分 = 当月已发布项目得分按个人贡献（工时占比）加权平均</div>
      </div>

      <div v-if="!isSelf" class="card block">
        <h4>个人年度汇总</h4>
        <el-table :data="data.persons" border stripe>
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="months">
                <span v-for="(v, i) in row.monthly" :key="i">{{ i + 1 }}月 <b>{{ v ?? '—' }}</b></span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="rank" label="排名" width="80" sortable :sort-method="sortNum('rank')">
            <template #default="{ row }">{{ row.rank ? `#${row.rank}` : '—' }}</template>
          </el-table-column>
          <el-table-column label="姓名" min-width="100">
            <template #default="{ row }"><b>{{ row.name }}</b> <span class="muted small">{{ row.employeeNo }}</span></template>
          </el-table-column>
          <el-table-column label="部门" min-width="110"><template #default="{ row }">{{ row.deptName || '—' }}</template></el-table-column>
          <el-table-column prop="projects" label="存档项目" width="100" align="right" sortable />
          <el-table-column prop="hours" label="累计工时" width="110" align="right" sortable :sort-method="sortNum('hours')">
            <template #default="{ row }">{{ Number(row.hours) }} 天</template>
          </el-table-column>
          <el-table-column prop="fillRate" label="周报填报率" width="120" align="right" sortable :sort-method="sortNum('fillRate')">
            <template #default="{ row }">{{ row.fillRate ?? '—' }}{{ row.fillRate !== null ? '%' : '' }}</template>
          </el-table-column>
          <el-table-column prop="avg" label="年度平均分" width="120" align="right" sortable :sort-method="sortNum('avg')">
            <template #default="{ row }"><b>{{ row.avg ?? '—' }}</b></template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="data.depts.length > 1" class="card block">
        <h4>部门对比</h4>
        <el-table :data="data.depts" border>
          <el-table-column prop="name" label="部门" min-width="140" />
          <el-table-column prop="people" label="人数" width="90" align="right" />
          <el-table-column prop="archivedProjects" label="存档项目" width="110" align="right" />
          <el-table-column label="项目平均分" width="120" align="right"><template #default="{ row }">{{ row.avgScore ?? '—' }}</template></el-table-column>
          <el-table-column label="周报填报率" width="120" align="right"><template #default="{ row }">{{ row.fillRate ?? '—' }}{{ row.fillRate !== null ? '%' : '' }}</template></el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<style scoped>
.btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.tile {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.tile span {
  color: #5a6577;
  font-size: 13px;
}
.tile b {
  font-size: 26px;
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
.block {
  margin-bottom: 16px;
}
h4 {
  margin: 0 0 14px;
}
h4 small {
  font-weight: normal;
  color: #8a94a6;
  font-size: 12px;
  margin-left: 6px;
}
.legend-note {
  font-size: 12px;
  color: #8a94a6;
  margin-top: 8px;
}
.months {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  padding: 4px 16px;
  font-size: 13px;
  color: #5a6577;
}
.months b {
  color: #1b2430;
}
</style>
