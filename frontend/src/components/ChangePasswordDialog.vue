<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { checkPassword } from '@/utils/format'

const props = defineProps<{ modelValue: boolean; force?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const auth = useAuthStore()
const router = useRouter()
const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { validator: (_r, v, cb) => { const e = checkPassword(v); if (e) cb(new Error(e)); else cb() }, trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: (_r, v, cb) => (v !== form.newPassword ? cb(new Error('两次输入的密码不一致')) : cb()), trigger: 'blur' }
  ]
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      Object.assign(form, { oldPassword: '', newPassword: '', confirm: '' })
      formRef.value?.clearValidate()
    }
  }
)

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await auth.changePassword(form.oldPassword, form.newPassword)
    ElMessage.success('密码已修改')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}

async function logout() {
  await auth.logout()
  router.replace('/login')
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="force ? '请先修改初始密码' : '修改密码'"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="!force"
    :show-close="!force"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert
      v-if="force"
      type="warning"
      :closable="false"
      show-icon
      title="您正在使用初始密码或密码已被重置，为了账号安全，请先设置新密码。"
      style="margin-bottom: 16px"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password placeholder="8~32 位，同时包含字母和数字" autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirm">
        <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" @keyup.enter="submit" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-if="force" @click="logout">退出登录</el-button>
      <el-button v-else @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">确认修改</el-button>
    </template>
  </el-dialog>
</template>
