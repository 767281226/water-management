<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入账号或工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success(`欢迎回来，${auth.user?.realName}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="card">
      <div class="brand">
        <div class="logo">
          <svg viewBox="0 0 24 24"><path d="M12 2.5s7 7.4 7 12.4a7 7 0 0 1-14 0c0-5 7-12.4 7-12.4z" /></svg>
        </div>
        <h1>宏禹水利管理软件</h1>
      </div>
      <p class="sub">内部办公系统 · 请使用公司分配的账号登录</p>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="账号 / 工号" prefix-icon="User" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            show-password
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" native-type="submit">登 录</el-button>
      </el-form>
      <p class="tip">忘记密码请联系系统管理员重置</p>
    </div>
  </div>
</template>

<style scoped>
.login {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(135deg, #0f2a4a 0%, #1c5cab 60%, #2a78d6 100%);
  position: relative;
  overflow: hidden;
}
.login::before,
.login::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
}
.login::before {
  width: 620px;
  height: 620px;
  left: -200px;
  bottom: -280px;
}
.login::after {
  width: 420px;
  height: 420px;
  right: -120px;
  top: -160px;
}
.card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 16px;
  padding: 36px 32px 28px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}
.brand h1 {
  font-size: 20px;
  margin: 0;
}
.logo {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: var(--hy-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.logo svg {
  width: 24px;
  height: 24px;
  fill: #fff;
}
.sub {
  color: #8a94a6;
  font-size: 13px;
  margin: 8px 0 26px;
}
.submit {
  width: 100%;
  margin-top: 6px;
}
.tip {
  text-align: center;
  color: #8a94a6;
  font-size: 12px;
  margin: 14px 0 0;
}
</style>
