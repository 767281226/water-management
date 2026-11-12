<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { PROJECT_STATUS, projectApi, type ProjectQuery, type ProjectStatus, type ProjectVO } from '@/api/project'
import { commonApi } from '@/api/common'
import type { DeptNode, UserOption } from '@/api/system'
import { useAuthStore } from '@/stores/auth'
import ProjectFormDialog from './ProjectFormDialog.vue'

const router = useRouter()
const auth = useAuthStore()
const rows = ref<ProjectVO[]>([])
const total = ref(0)
const loading = ref(false)
const types = ref<string[]>([])
const users = ref<UserOption[]>([])
const depts = ref<DeptNode[]>([])
const query = reactive<ProjectQuery>({ keyword: '', status: '', type: '', deptId: null, mine: false, page: 1, size: 20 })
const dialog = ref(false)
const editing = ref<ProjectVO | null>(null)

const scopeText = computed(() => {
  const s = auth.user?.dataScope
  return s === 'ALL' ? '全公司项目' : s === 'DEPT' ? '本部门项目 + 我参与的项目' : '我负责 / 参与的项目'
})
const statuses = Object.entries(PROJECT_STATUS) as [ProjectStatus, (typeof PROJECT_STATUS)[ProjectStatus]][]

async function load() {
  loading.value = true
  try {
    const r = await projectApi.page(query)
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  Object.assign(query, { keyword: '', status: '', type: '', deptId: null, mine: false, page: 1 })
  load()
}

async function ensureOptions() {
  if (!users.value.length) [users.value, depts.value] = await Promise.all([commonApi.users(), commonApi.depts()])
}

async function openCreate() {
  await ensureOptions()
  editing.value = null
  dialog.value = true
}

function onSaved(id: number) {
  if (!editing.value) router.push(`/project/ledger/${id}`)
  else load()
}

onMounted(async () => {
  load()
  types.value = await projectApi.types()
  if (auth.user?.dataScope !== 'SELF') depts.value = await commonApi.depts()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>项目台账</h3>
        <p>数据范围：{{ scopeText }} · 共 {{ total }} 个</p>
      </div>
      <el-button v-perm="'project:ledger:edit'" type="primary" icon="Plus" @click="openCreate">新建项目</el-button>
    </div>
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="项目名称 / 编号 / 委托单位" clearable style="width: 230px" @keyup.enter="search" @clear="search" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="search">
          <el-option v-for="[k, v] in statuses" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-select v-model="query.type" placeholder="全部类型" clearable style="width: 120px" @change="search">
          <el-option v-for="t in types" :key="t" :label="t" :value="t" />
        </el-select>
        <el-tree-select
          v-if="auth.user?.dataScope !== 'SELF'"
          v-model="query.deptId"
          :data="depts"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          check-strictly
          default-expand-all
          clearable
          placeholder="全部部门"
          style="width: 160px"
          @change="search"
        />
        <el-checkbox v-model="query.mine" @change="search">只看我参与的</el-checkbox>
        <el-button type="primary" icon="Search" @click="search">查询</el-button>
        <el-button icon="Refresh" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe @row-click="(r: ProjectVO) => router.push(`/project/ledger/${r.id}`)" class="clickable">
        <el-table-column label="项目名称 / 编号" min-width="240">
          <template #default="{ row }">
            <b class="pname">{{ row.name }}</b>
            <div class="muted small">{{ row.code }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="80" />
        <el-table-column label="委托单位" min-width="140">
          <template #default="{ row }">{{ row.client || '—' }}</template>
        </el-table-column>
        <el-table-column label="负责人" width="100">
          <template #default="{ row }">{{ row.leaderName }}</template>
        </el-table-column>
        <el-table-column label="部门" min-width="110">
          <template #default="{ row }">{{ row.deptName || '—' }}</template>
        </el-table-column>
        <el-table-column label="计划工期" width="190">
          <template #default="{ row }"><span class="small">{{ row.startDate || '—' }} ~ {{ row.planEndDate }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="PROJECT_STATUS[row.status as ProjectStatus].type || undefined" size="small">{{ row.statusLabel }}</el-tag>
            <el-tag v-if="row.archiveApplied" type="warning" size="small" effect="plain" style="margin-left: 4px">待存档</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>
    <ProjectFormDialog v-model="dialog" :project="editing" :users="users" :depts="depts" :types="types" @saved="onSaved" />
  </div>
</template>

<style scoped>
.clickable :deep(tbody tr) {
  cursor: pointer;
}
.pname {
  color: #1b2430;
}
.clickable :deep(tbody tr:hover) .pname {
  color: var(--hy-primary);
}
</style>
