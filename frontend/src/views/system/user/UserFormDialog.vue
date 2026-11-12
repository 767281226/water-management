<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { userApi, type DeptNode, type RoleVO, type UserForm, type UserVO } from '@/api/system'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ modelValue: boolean; user: UserVO | null; depts: DeptNode[]; roles: RoleVO[] }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>()

const auth = useAuthStore()
const formRef = ref<FormInstance>()
const saving = ref(false)
const empty = (): UserForm => ({ username: '', employeeNo: '', realName: '', deptId: null, title: '', phone: '', email: '', roleIds: [], remark: '' })
const form = reactive<UserForm>(empty())

const isEdit = computed(() => !!props.user)
/** 只有超级管理员才能分配超级管理员角色 */
const roleOptions = computed(() => props.roles.map((r) => ({ ...r, disabled: r.superAdmin && !auth.user?.superAdmin })))

const rules: FormRules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_.-]{3,32}$/, message: '3~32 位字母、数字、下划线、点或横线', trigger: 'blur' }
  ],
  employeeNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  roleIds: [{ type: 'array', required: true, min: 1, message: '请至少选择一个角色', trigger: 'change' }]
}

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    const u = props.user
    Object.assign(
      form,
      u
        ? {
            username: u.username,
            employeeNo: u.employeeNo,
            realName: u.realName,
            deptId: u.deptId,
            title: u.title || '',
            phone: u.phone || '',
            email: u.email || '',
            roleIds: u.roles.map((r) => r.id),
            remark: u.remark || ''
          }
        : { ...empty(), roleIds: props.roles.filter((r) => r.code === 'staff').map((r) => r.id) }
    )
    formRef.value?.clearValidate()
  }
)

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (props.user) {
      await userApi.update(props.user.id, form)
      ElMessage.success('已保存')
    } else {
      await userApi.create(form)
      ElMessage.success('已新增，用户可使用初始密码登录')
    }
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑用户' : '新增用户'"
    width="620px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
      <el-row :gutter="12">
        <el-col :xs="24" :sm="12">
          <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" maxlength="32" /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="工号" prop="employeeNo"><el-input v-model="form.employeeNo" maxlength="32" /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="登录账号" prop="username">
            <el-input v-model="form.username" maxlength="32" :disabled="user?.builtin" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="所属部门" prop="deptId">
            <el-tree-select
              v-model="form.deptId"
              :data="depts"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              check-strictly
              default-expand-all
              clearable
              placeholder="请选择部门"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="职务" prop="title"><el-input v-model="form.title" maxlength="64" /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" maxlength="11" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="128" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="角色" prop="roleIds">
            <el-checkbox-group v-model="form.roleIds">
              <el-checkbox v-for="r in roleOptions" :key="r.id" :value="r.id" :disabled="r.disabled">{{ r.name }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" /></el-form-item>
        </el-col>
      </el-row>
      <el-alert v-if="!isEdit" type="info" :closable="false" show-icon title="初始密码可在「参数配置 › 安全设置」中修改，用户首次登录时须设置新密码。" />
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
