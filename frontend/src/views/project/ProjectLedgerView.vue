<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { PROJECT_STATUS, projectApi, type ProjectQuery, type ProjectStatus, type ProjectVO } from '@/api/project'
import { commonApi } from '@/api/common'
import type { DeptNode, UserOption } from '@/api/system'
import { useAuthStore } from '@/stores/auth'
import ProjectFormDialog from './ProjectFormDialog.vue'
import { useMobile } from '@/composables/useMobile'

const router = useRouter()
const auth = useAuthStore()
const isMobile = useMobile()
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

      <el-table v-if="!isMobile" v-loading="loading" :data="rows" border stripe @row-click="(r: ProjectVO) => router.push(`/project/ledger/${r.id}`)" class="clickable">
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
      <!-- 手机端：卡片列表 -->
      <div v-else v-loading="loading" class="m-list">
        <div v-for="p in rows" :key="p.id" class="m-card" @click="router.push(`/project/ledger/${p.id}`)">
          <div class="m-top">
            <b>{{ p.name }}</b>
            <el-tag :type="PROJECT_STATUS[p.status].type || undefined" size="small">{{ p.statusLabel }}</el-tag>
          </div>
          <div class="m-sub">{{ p.code }} · {{ p.type }}<template v-if="p.archiveApplied"> · <span class="warn">待存档</span></template></div>
          <div class="m-meta">
            <span><el-icon><User /></el-icon>{{ p.leaderName }}</span>
            <span><el-icon><OfficeBuilding /></el-icon>{{ p.deptName || '—' }}</span>
            <span><el-icon><Calendar /></el-icon>{{ p.planEndDate }} 完成</span>
          </div>
        </div>
        <el-empty v-if="!loading && !rows.length" description="暂无项目" />
      </div>
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
.m-card {
  padding: 14px 2px;
  border-bottom: 1px solid #eef2f7;
  cursor: pointer;
}
.m-card:last-child {
  border-bottom: none;
}
.m-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}
.m-top b {
  font-size: 15px;
  line-height: 1.4;
}
.m-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--hy-text-3);
}
.m-sub .warn {
  color: #f59e0b;
}
.m-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--hy-text-2);
}
.m-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
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
