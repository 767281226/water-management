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

const features = [
  { icon: 'EditPen', title: '周报填报', desc: '周一报计划、周五报完成，自动统计完成率' },
  { icon: 'Star', title: '月度考评', desc: '存档项目统计打分，年度自动汇总' },
  { icon: 'Reading', title: '资料共享', desc: '规范、成果、行业资料统一查询与审批下载' }
]

async function submit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success(`欢迎回来，${auth.user?.realName}`)
    const r = route.query.redirect
    router.replace(typeof r === 'string' && r.startsWith('/') && !r.startsWith('/login') ? r : '/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <!-- 左侧品牌区 -->
    <section class="hero">
      <div class="hero-inner">
        <div class="brand">
          <div class="logo">
            <svg viewBox="0 0 24 24"><path d="M12 2.5s7 7.4 7 12.4a7 7 0 0 1-14 0c0-5 7-12.4 7-12.4z" /></svg>
          </div>
          <span>宏禹水利</span>
        </div>
        <h1>项目与资料<br />管理平台</h1>
        <p class="slogan">让每一项水利工程的进度、质量与成果都清晰可见</p>
        <ul class="features">
          <li v-for="f in features" :key="f.title">
            <div class="fi"><el-icon :size="20"><component :is="f.icon" /></el-icon></div>
            <div><b>{{ f.title }}</b><span>{{ f.desc }}</span></div>
          </li>
        </ul>
      </div>
      <!-- 动态水波 -->
      <div class="waves" aria-hidden="true">
        <svg class="wave w1" viewBox="0 0 1440 200" preserveAspectRatio="none"><path d="M0 100 C 240 40 480 160 720 100 S 1200 40 1440 100 V200 H0 Z" /></svg>
        <svg class="wave w2" viewBox="0 0 1440 200" preserveAspectRatio="none"><path d="M0 120 C 260 70 460 170 760 120 S 1180 70 1440 120 V200 H0 Z" /></svg>
        <svg class="wave w3" viewBox="0 0 1440 200" preserveAspectRatio="none"><path d="M0 140 C 300 100 520 180 800 140 S 1220 100 1440 140 V200 H0 Z" /></svg>
      </div>
      <span class="bubble b1" /><span class="bubble b2" /><span class="bubble b3" />
    </section>

    <!-- 右侧登录区 -->
    <section class="panel">
      <div class="card">
        <div class="m-brand">
          <div class="logo">
            <svg viewBox="0 0 24 24"><path d="M12 2.5s7 7.4 7 12.4a7 7 0 0 1-14 0c0-5 7-12.4 7-12.4z" /></svg>
          </div>
          <span>宏禹水利管理平台</span>
        </div>
        <h2>欢迎登录</h2>
        <p class="sub">请使用公司分配的账号登录</p>
        <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="submit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="账号 / 工号" prefix-icon="User" autocomplete="username" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password
              autocomplete="current-password" @keyup.enter="submit" />
          </el-form-item>
          <el-button type="primary" class="submit" :loading="loading" native-type="submit">登 录</el-button>
        </el-form>
        <p class="tip"><el-icon><InfoFilled /></el-icon>忘记密码请联系系统管理员重置</p>
      </div>
      <footer>© {{ new Date().getFullYear() }} 宏禹 · 内部系统，仅限公司员工使用</footer>
    </section>
  </div>
</template>

<style scoped>
.login {
  min-height: 100%;
  display: flex;
  background: #f5f8ff;
}

/* ---------------- 品牌区 ---------------- */
.hero {
  position: relative;
  flex: 1.15;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(800px 500px at 15% 10%, rgba(25, 182, 255, 0.35), transparent 60%),
    radial-gradient(700px 500px at 90% 80%, rgba(47, 107, 255, 0.45), transparent 60%),
    linear-gradient(135deg, #0a1c45 0%, #0f327e 50%, #1d5be0 100%);
  display: flex;
  align-items: center;
}
.hero-inner {
  position: relative;
  z-index: 2;
  padding: 0 9% 120px;
  max-width: 680px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 2px;
}
.logo {
  width: 44px;
  height: 44px;
  border-radius: 13px;
  background: var(--hy-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(25, 182, 255, 0.4);
  flex: none;
}
.logo svg {
  width: 26px;
  height: 26px;
  fill: #fff;
}
h1 {
  font-size: 46px;
  line-height: 1.25;
  margin: 46px 0 16px;
  letter-spacing: 4px;
  background: linear-gradient(90deg, #ffffff 0%, #bfe6ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.slogan {
  font-size: 16px;
  color: #b8cdf0;
  margin: 0 0 40px;
  letter-spacing: 1px;
}
.features {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.features li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(6px);
  max-width: 420px;
}
.fi {
  width: 40px;
  height: 40px;
  border-radius: 11px;
  background: rgba(25, 182, 255, 0.2);
  color: #7fd4ff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.features b {
  display: block;
  font-size: 15px;
}
.features span {
  font-size: 13px;
  color: #a9bfe6;
}
.waves {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 200px;
  z-index: 1;
}
.wave {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 200%;
  height: 100%;
}
.wave path {
  fill: rgba(255, 255, 255, 0.06);
}
.w1 {
  animation: drift 18s linear infinite;
}
.w2 {
  animation: drift 13s linear infinite reverse;
}
.w2 path {
  fill: rgba(25, 182, 255, 0.12);
}
.w3 {
  animation: drift 22s linear infinite;
}
.w3 path {
  fill: rgba(255, 255, 255, 0.08);
}
@keyframes drift {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}
.bubble {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.35), rgba(255, 255, 255, 0.04));
  z-index: 1;
}
.b1 {
  width: 140px;
  height: 140px;
  right: 12%;
  top: 14%;
}
.b2 {
  width: 60px;
  height: 60px;
  right: 30%;
  top: 34%;
}
.b3 {
  width: 26px;
  height: 26px;
  right: 18%;
  top: 46%;
}

/* ---------------- 登录区 ---------------- */
.panel {
  flex: 1;
  min-width: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px 20px;
  position: relative;
}
.card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 20px;
  padding: 44px 40px 34px;
  box-shadow: 0 20px 60px rgba(23, 43, 99, 0.12);
  border: 1px solid #edf1f8;
}
.m-brand {
  display: none;
}
h2 {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
}
.sub {
  color: var(--hy-text-3);
  margin: 8px 0 30px;
  font-size: 14px;
}
.card :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 2px 14px;
  background: #f7f9fd;
  box-shadow: 0 0 0 1px #e3e9f3 inset;
}
.card :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 1px var(--hy-primary) inset, 0 0 0 4px rgba(47, 107, 255, 0.1);
}
.submit {
  width: 100%;
  height: 46px;
  margin-top: 8px;
  border-radius: 10px;
  font-size: 16px;
  letter-spacing: 6px;
}
.tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--hy-text-3);
  font-size: 12px;
  margin: 18px 0 0;
}
footer {
  margin-top: 28px;
  font-size: 12px;
  color: #a3adbd;
}

@media (max-width: 960px) {
  .hero {
    display: none;
  }
  .panel {
    min-width: 0;
    background:
      radial-gradient(600px 300px at 50% -60px, rgba(47, 107, 255, 0.35), transparent 70%),
      linear-gradient(180deg, #0f327e 0%, #1d5be0 240px, #f5f8ff 240px);
  }
  .card {
    padding: 34px 26px 28px;
  }
  .m-brand {
    display: flex;
    align-items: center;
    gap: 10px;
    font-weight: 700;
    margin-bottom: 26px;
  }
  .m-brand .logo {
    width: 36px;
    height: 36px;
    border-radius: 10px;
  }
  .m-brand .logo svg {
    width: 20px;
    height: 20px;
  }
}
</style>
