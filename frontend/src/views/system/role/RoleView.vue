<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type ElTree, type FormInstance, type FormRules } from 'element-plus'
import { DATA_SCOPE_LABEL, roleApi, type PermissionNode, type RoleForm, type RoleVO } from '@/api/system'
import type { DataScope } from '@/api/auth'
import { fmtTime } from '@/utils/format'

const rows = ref<RoleVO[]>([])
const tree = ref<PermissionNode[]>([])
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const editing = ref<RoleVO | null>(null)
const formRef = ref<FormInstance>()
const treeRef = ref<InstanceType<typeof ElTree>>()
const form = reactive<RoleForm>({ code: '', name: '', dataScope: 'SELF', sort: 0, remark: '', permissions: [] })
const rules: FormRules = {
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]{1,31}$/, message: '2~32 位小写字母、数字或下划线，以字母开头', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}
const scopes = Object.entries(DATA_SCOPE_LABEL) as [DataScope, string][]

/** 只回显叶子节点，父节点的勾选状态由 el-tree 自动计算 */
function leafCodes(codes: string[]): string[] {
  const set = new Set(codes)
  const leaves: string[] = []
  const walk = (nodes: PermissionNode[]) =>
    nodes.forEach((n) => {
      if (n.children.length) walk(n.children)
      else if (set.has(n.code)) leaves.push(n.code)
    })
  walk(tree.value)
  return leaves
}

async function load() {
  loading.value = true
  try {
    rows.value = await roleApi.list()
  } finally {
    loading.value = false
  }
}

async function open(row?: RoleVO) {
  editing.value = row ?? null
  Object.assign(form, row
    ? { code: row.code, name: row.name, dataScope: row.dataScope, sort: row.sort, remark: row.remark || '', permissions: row.permissions }
    : { code: '', name: '', dataScope: 'SELF', sort: rows.value.length + 1, remark: '', permissions: [] })
  dialog.value = true
  await nextTick()
  formRef.value?.clearValidate()
  treeRef.value?.setCheckedKeys(leafCodes(form.permissions))
}

async function submit() {
  await formRef.value?.validate()
  const t = treeRef.value!
  form.permissions = [...(t.getCheckedKeys() as string[]), ...(t.getHalfCheckedKeys() as string[])]
  saving.value = true
  try {
    if (editing.value) await roleApi.update(editing.value.id, form)
    else await roleApi.create(form)
    ElMessage.success('已保存，权限变更对相关用户立即生效')
    dialog.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row: RoleVO) {
  await ElMessageBox.confirm(`确定删除角色「${row.name}」吗？`, '删除角色', { type: 'warning' })
  await roleApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

function expandAll(expand: boolean) {
  const nodes = treeRef.value?.store.nodesMap ?? {}
  Object.values(nodes).forEach((n) => (n.expanded = expand))
}

onMounted(async () => {
  load()
  tree.value = await roleApi.permissionTree()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>角色权限</h3>
        <p>配置角色可访问的菜单 / 按钮，以及数据范围；一个用户可以拥有多个角色</p>
      </div>
      <el-button v-perm="'system:role:edit'" type="primary" icon="Plus" @click="open()">新增角色</el-button>
    </div>
    <div class="card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column label="角色名称" min-width="130">
          <template #default="{ row }">
            <b>{{ row.name }}</b>
            <el-tag v-if="row.builtin" size="small" effect="plain" style="margin-left: 6px">内置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="编码" width="110" />
        <el-table-column label="数据范围" width="140">
          <template #default="{ row }">{{ DATA_SCOPE_LABEL[row.dataScope as DataScope] }}</template>
        </el-table-column>
        <el-table-column label="权限" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.superAdmin" type="danger" size="small">全部权限</el-tag>
            <span v-else>{{ row.permissions.length }} 个权限点</span>
          </template>
        </el-table-column>
        <el-table-column prop="userCount" label="用户数" width="80" align="right" />
        <el-table-column label="说明" min-width="220">
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'system:role:edit'" link type="primary" @click="open(row as RoleVO)">{{ row.superAdmin ? '查看' : '编辑' }}</el-button>
            <el-button v-if="!row.builtin" v-perm="'system:role:edit'" link type="danger" @click="remove(row as RoleVO)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="editing ? `编辑角色 · ${editing.name}` : '新增角色'" width="680px" :close-on-click-modal="false" top="6vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="角色名称" prop="name"><el-input v-model="form.name" maxlength="64" /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="角色编码" prop="code"><el-input v-model="form.code" maxlength="32" :disabled="editing?.builtin" /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="数据范围">
              <el-select v-model="form.dataScope" :disabled="editing?.superAdmin" style="width: 100%">
                <el-option v-for="[k, v] in scopes" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="9999" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="说明"><el-input v-model="form.remark" maxlength="255" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="菜单权限">
              <div class="perm-box">
                <div class="perm-tools">
                  <el-alert v-if="editing?.superAdmin" type="warning" :closable="false" title="超级管理员自动拥有全部权限（含以后新增的功能），无需配置" />
                  <template v-else>
                    <el-button link type="primary" @click="expandAll(true)">全部展开</el-button>
                    <el-button link type="primary" @click="expandAll(false)">全部收起</el-button>
                  </template>
                </div>
                <el-scrollbar max-height="340px">
                  <el-tree
                    ref="treeRef"
                    :data="tree"
                    node-key="code"
                    show-checkbox
                    default-expand-all
                    :props="{ label: 'name', children: 'children', disabled: () => !!editing?.superAdmin }"
                  >
                    <template #default="{ data }">
                      <span>{{ data.name }}</span>
                      <el-tag v-if="data.type === 'BUTTON'" size="small" type="info" effect="plain" style="margin-left: 6px">按钮</el-tag>
                    </template>
                  </el-tree>
                </el-scrollbar>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.perm-box {
  width: 100%;
  border: 1px solid #e3e7ee;
  border-radius: 8px;
  padding: 8px;
}
.perm-tools {
  margin-bottom: 6px;
}
</style>
