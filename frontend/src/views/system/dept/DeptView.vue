<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { deptApi, userApi, type DeptForm, type DeptNode, type UserOption } from '@/api/system'

const rows = ref<DeptNode[]>([])
const users = ref<UserOption[]>([])
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<DeptForm>({ parentId: null, name: '', leaderId: null, sort: 0 })
const rules: FormRules = { name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }] }

async function load() {
  loading.value = true
  try {
    rows.value = await deptApi.tree()
  } finally {
    loading.value = false
  }
}

async function open(parent: DeptNode | null, row?: DeptNode) {
  editingId.value = row?.id ?? null
  Object.assign(form, row
    ? { parentId: row.parentId, name: row.name, leaderId: row.leaderId, sort: row.sort }
    : { parentId: parent?.id ?? null, name: '', leaderId: null, sort: (parent?.children.length ?? rows.value.length) + 1 })
  dialog.value = true
  formRef.value?.clearValidate()
  if (!users.value.length) users.value = await userApi.options()
}

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) await deptApi.update(editingId.value, form)
    else await deptApi.create(form)
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row: DeptNode) {
  await ElMessageBox.confirm(`确定删除部门「${row.name}」吗？`, '删除部门', { type: 'warning' })
  await deptApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>部门管理</h3>
        <p>维护公司部门树和部门负责人；部门负责人用于周报统计、下载审批等业务</p>
      </div>
      <el-button v-perm="'system:dept:edit'" type="primary" icon="Plus" @click="open(null)">新增部门</el-button>
    </div>
    <div class="card">
      <el-table v-loading="loading" :data="rows" row-key="id" default-expand-all border :tree-props="{ children: 'children' }">
        <el-table-column prop="name" label="部门名称" min-width="200" />
        <el-table-column label="负责人" min-width="120">
          <template #default="{ row }">{{ row.leaderName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="userCount" label="人数" width="90" align="right" />
        <el-table-column prop="sort" label="排序" width="80" align="right" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button v-perm="'system:dept:edit'" link type="primary" @click="open(row as DeptNode)">新增下级</el-button>
            <el-button v-perm="'system:dept:edit'" link type="primary" @click="open(null, row as DeptNode)">编辑</el-button>
            <el-button v-perm="'system:dept:edit'" link type="danger" @click="remove(row as DeptNode)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="editingId ? '编辑部门' : '新增部门'" width="480px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="rows"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            default-expand-all
            clearable
            placeholder="无（顶级部门）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="name"><el-input v-model="form.name" maxlength="64" /></el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.leaderId" filterable clearable placeholder="请选择" style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="`${u.realName}（${u.employeeNo}）`" :value="u.id">
              <span>{{ u.realName }}</span>
              <span class="muted small" style="float: right">{{ u.deptName || '' }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="9999" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
