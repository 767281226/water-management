<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deptApi, roleApi, userApi, type DeptNode, type RoleVO, type UserQuery, type UserVO } from '@/api/system'
import { useAuthStore } from '@/stores/auth'
import { fmtTime } from '@/utils/format'
import UserFormDialog from './UserFormDialog.vue'

const auth = useAuthStore()
const depts = ref<DeptNode[]>([])
const roles = ref<RoleVO[]>([])
const rows = ref<UserVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<UserQuery>({ keyword: '', deptId: null, status: '', roleId: null, page: 1, size: 20 })

const dialog = ref(false)
const editing = ref<UserVO | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await userApi.page(query)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  Object.assign(query, { keyword: '', deptId: null, status: '', roleId: null, page: 1 })
  load()
}

function onDeptClick(node: DeptNode) {
  query.deptId = query.deptId === node.id ? null : node.id
  search()
}

function openCreate() {
  editing.value = null
  dialog.value = true
}

function openEdit(u: UserVO) {
  editing.value = u
  dialog.value = true
}

async function toggleStatus(u: UserVO) {
  const next = u.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  if (next === 'DISABLED') {
    await ElMessageBox.confirm(`停用后「${u.realName}」将无法登录，且已登录的设备会立即下线。确定停用吗？`, '停用账号', { type: 'warning' })
  }
  await userApi.setStatus(u.id, next)
  ElMessage.success(next === 'ENABLED' ? '已启用' : '已停用')
  load()
}

async function resetPwd(u: UserVO) {
  await ElMessageBox.confirm(`确定将「${u.realName}」的密码重置为初始密码吗？用户下次登录时需要修改密码。`, '重置密码', { type: 'warning' })
  const res = await userApi.resetPassword(u.id)
  ElMessageBox.alert(`已重置，初始密码为：${res.password}`, '重置成功', { type: 'success' })
}

async function unlock(u: UserVO) {
  await userApi.unlock(u.id)
  ElMessage.success('已解除锁定')
  load()
}

async function remove(u: UserVO) {
  await ElMessageBox.confirm(`确定删除用户「${u.realName}」吗？删除后无法登录，历史数据保留。`, '删除用户', { type: 'warning' })
  await userApi.remove(u.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  load()
  ;[depts.value, roles.value] = await Promise.all([deptApi.tree(), roleApi.list()])
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>用户管理</h3>
        <p>账号由管理员统一开通；新用户使用初始密码登录，首次登录须修改密码</p>
      </div>
      <el-button v-perm="'system:user:create'" type="primary" icon="Plus" @click="openCreate">新增用户</el-button>
    </div>

    <div class="layout">
      <div class="card dept-panel">
        <div class="panel-title">部门</div>
        <el-tree
          :data="depts"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          :current-node-key="query.deptId ?? undefined"
          @node-click="onDeptClick"
        >
          <template #default="{ data }">
            <span class="tree-node">{{ data.name }}<span class="muted small">{{ data.userCount || '' }}</span></span>
          </template>
        </el-tree>
      </div>

      <div class="card main">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="姓名 / 账号 / 工号 / 手机号" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
          <el-select v-model="query.roleId" placeholder="全部角色" clearable style="width: 140px" @change="search">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="search">
            <el-option label="正常" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
          <el-button type="primary" icon="Search" @click="search">查询</el-button>
          <el-button icon="Refresh" @click="reset">重置</el-button>
        </div>

        <el-table v-loading="loading" :data="rows" border stripe>
          <el-table-column label="姓名 / 职务" min-width="110">
            <template #default="{ row }">
              <b>{{ row.realName }}</b>
              <el-tag v-if="row.builtin" size="small" type="warning" effect="plain" style="margin-left: 6px">内置</el-tag>
              <div class="muted small">{{ row.title || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="账号 / 工号" min-width="110">
            <template #default="{ row }">
              {{ row.username }}
              <div class="muted small">{{ row.employeeNo }}</div>
            </template>
          </el-table-column>
          <el-table-column label="部门" min-width="100">
            <template #default="{ row }">{{ row.deptName || '—' }}</template>
          </el-table-column>
          <el-table-column label="角色" min-width="130">
            <template #default="{ row }">
              <el-tag v-for="r in row.roles" :key="r.id" size="small" class="role-tag" :type="r.code === 'admin' ? 'danger' : 'primary'" effect="plain">
                {{ r.name }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status === 'ENABLED' ? '正常' : '停用' }}</el-tag>
              <el-tag v-if="row.locked" type="danger" size="small" style="margin-top: 2px">锁定</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后登录" width="140">
            <template #default="{ row }">{{ fmtTime(row.lastLoginAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-perm="'system:user:update'" link type="primary" @click="openEdit(row as UserVO)">编辑</el-button>
              <el-button v-perm="'system:user:reset-pwd'" link type="primary" @click="resetPwd(row as UserVO)">重置密码</el-button>
              <template v-if="auth.hasPerm('system:user:status')">
                <el-button v-if="row.locked" link type="warning" @click="unlock(row as UserVO)">解锁</el-button>
                <el-button v-if="!row.builtin" link :type="row.status === 'ENABLED' ? 'warning' : 'success'" @click="toggleStatus(row as UserVO)">
                  {{ row.status === 'ENABLED' ? '停用' : '启用' }}
                </el-button>
              </template>
              <el-button v-if="!row.builtin" v-perm="'system:user:delete'" link type="danger" @click="remove(row as UserVO)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.size"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="load"
            @size-change="search"
          />
        </div>
      </div>
    </div>

    <UserFormDialog v-model="dialog" :user="editing" :depts="depts" :roles="roles" @saved="load" />
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.dept-panel {
  padding: 12px 8px;
}
.panel-title {
  font-weight: 600;
  padding: 0 8px 8px;
}
.tree-node {
  display: flex;
  justify-content: space-between;
  flex: 1;
  padding-right: 8px;
}
.role-tag {
  margin: 2px 4px 2px 0;
}
@media (max-width: 900px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .dept-panel {
    display: none;
  }
}
</style>
