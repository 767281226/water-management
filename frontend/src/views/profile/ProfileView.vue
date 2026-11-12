<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'
import { DATA_SCOPE_LABEL } from '@/api/system'
import { fmtTime } from '@/utils/format'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'

const auth = useAuthStore()
const formRef = ref<FormInstance>()
const saving = ref(false)
const pwdVisible = ref(false)
const form = reactive({ phone: auth.user?.phone || '', email: auth.user?.email || '' })
const rules: FormRules = {
  phone: [{ pattern: /^$|^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await authApi.updateProfile(form.phone, form.email)
    await auth.fetchMe()
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head"><div><h3>个人中心</h3></div></div>
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <div class="card">
          <el-descriptions title="基本信息" :column="1" border>
            <el-descriptions-item label="姓名">{{ auth.user?.realName }}</el-descriptions-item>
            <el-descriptions-item label="登录账号">{{ auth.user?.username }}</el-descriptions-item>
            <el-descriptions-item label="工号">{{ auth.user?.employeeNo }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ auth.user?.deptName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="职务">{{ auth.user?.title || '—' }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ auth.user?.roles.join('、') }}</el-descriptions-item>
            <el-descriptions-item label="数据范围">{{ auth.user ? DATA_SCOPE_LABEL[auth.user.dataScope] : '' }}</el-descriptions-item>
            <el-descriptions-item label="上次登录">{{ fmtTime(auth.user?.lastLoginAt) }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="card">
          <h4>联系方式</h4>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="70px">
            <el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" maxlength="11" /></el-form-item>
            <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="save">保存</el-button>
              <el-button @click="pwdVisible = true">修改密码</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-col>
    </el-row>
    <ChangePasswordDialog v-model="pwdVisible" />
  </div>
</template>

<style scoped>
h4 {
  margin: 0 0 16px;
}
.card {
  margin-bottom: 16px;
}
</style>
