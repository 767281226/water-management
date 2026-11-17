<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { MENUS, isGroup, type MenuGroup, type MenuItem } from '@/router/menu'
import { scoreApi } from '@/api/score'
import { docApi } from '@/api/doc'
import { DATA_SCOPE_LABEL } from '@/api/system'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import { avatarText } from '@/utils/format'

/** 手机端"我的"：个人信息 + 全部功能入口 */
const auth = useAuthStore()
const router = useRouter()
const pwdVisible = ref(false)
const badges = ref<Record<string, number>>({})

const groups = computed(() => {
  const result: { title: string; items: MenuItem[] }[] = []
  const singles: MenuItem[] = []
  for (const m of MENUS) {
    if (isGroup(m)) {
      const items = (m as MenuGroup).children.filter((c) => auth.hasPerm(c.perm))
      if (items.length) result.push({ title: m.title, items })
    } else if (auth.hasPerm(m.perm) && m.path !== '/dashboard') {
      singles.push(m)
    }
  }
  return singles.length ? [{ title: '常用', items: singles }, ...result] : result
})

const TONES = ['#2f6bff', '#0ea5e9', '#7c5cff', '#f59e0b', '#10b981', '#ec4899']
const tone = (i: number) => TONES[i % TONES.length]

async function logout() {
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  await auth.logout()
  router.replace('/login')
}

onMounted(() => {
  if (auth.hasPerm(['score:grade', 'score:publish'])) {
    scoreApi.todo().then((s) => (badges.value = { ...badges.value, '/score': s.toGrade + s.toPublish })).catch(() => undefined)
  }
  if (auth.hasPerm('doc:approve')) {
    docApi.todoCount().then((n) => (badges.value = { ...badges.value, '/doc/approve': n })).catch(() => undefined)
  }
})
</script>

<template>
  <div class="page">
    <section class="profile">
      <div class="avatar">{{ avatarText(auth.user?.realName) }}</div>
      <div class="pi">
        <b>{{ auth.user?.realName }}</b>
        <span>{{ auth.user?.employeeNo }} · {{ auth.user?.deptName || '未分配部门' }}{{ auth.user?.title ? ` · ${auth.user.title}` : '' }}</span>
        <div class="roles">
          <em v-for="r in auth.user?.roles" :key="r">{{ r }}</em>
          <em v-if="auth.user" class="scope">数据范围：{{ DATA_SCOPE_LABEL[auth.user.dataScope] }}</em>
        </div>
      </div>
      <svg class="wave" viewBox="0 0 400 60" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0 30 C 80 0, 160 60, 240 30 S 360 0, 400 30 V60 H0 Z" fill="rgba(255,255,255,0.12)" />
      </svg>
    </section>

    <section v-for="g in groups" :key="g.title" class="card group">
      <h4>{{ g.title }}</h4>
      <div class="grid">
        <a v-for="(m, i) in g.items" :key="m.path" class="entry" @click="router.push(m.path)">
          <span class="ei" :style="{ background: tone(i) }">
            <el-icon :size="20"><component :is="m.icon" /></el-icon>
            <em v-if="badges[m.path]" class="badge">{{ badges[m.path] }}</em>
          </span>
          <span>{{ m.title }}</span>
        </a>
      </div>
    </section>

    <section class="card list">
      <a class="li" @click="router.push('/profile')"><el-icon><User /></el-icon><span>个人信息</span><el-icon class="arrow"><ArrowRight /></el-icon></a>
      <a class="li" @click="pwdVisible = true"><el-icon><Lock /></el-icon><span>修改密码</span><el-icon class="arrow"><ArrowRight /></el-icon></a>
      <a class="li danger" @click="logout"><el-icon><SwitchButton /></el-icon><span>退出登录</span></a>
    </section>
    <p class="ver">宏禹水利管理平台</p>
    <ChangePasswordDialog v-model="pwdVisible" />
  </div>
</template>

<style scoped>
.profile {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 22px 18px 30px;
  border-radius: 18px;
  color: #fff;
  background:
    radial-gradient(300px 160px at 90% 0%, rgba(25, 182, 255, 0.6), transparent 70%),
    linear-gradient(120deg, #1546c9 0%, #2f6bff 55%, #19b6ff 100%);
  box-shadow: 0 12px 30px rgba(47, 107, 255, 0.28);
  margin-bottom: 14px;
}
.avatar {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  border: 2px solid rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  font-weight: 700;
  flex: none;
}
.pi {
  min-width: 0;
  position: relative;
  z-index: 1;
}
.pi b {
  font-size: 19px;
}
.pi > span {
  display: block;
  font-size: 13px;
  opacity: 0.9;
  margin-top: 2px;
}
.roles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.roles em {
  font-style: normal;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.2);
}
.roles .scope {
  background: rgba(11, 31, 75, 0.25);
}
.wave {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: 40px;
}
.group {
  margin-bottom: 14px;
  padding: 16px 12px 8px;
}
.group h4 {
  margin: 0 6px 10px;
  font-size: 15px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
}
.entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 2px;
  font-size: 12px;
  color: var(--hy-text);
  text-align: center;
  border-radius: 12px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.entry:active {
  background: #f3f6fd;
}
.ei {
  position: relative;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 12px rgba(23, 43, 99, 0.15);
}
.badge {
  position: absolute;
  top: -5px;
  right: -6px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #f43f5e;
  color: #fff;
  font-size: 11px;
  font-style: normal;
  line-height: 18px;
  border: 2px solid #fff;
}
.list {
  padding: 4px 16px;
}
.li {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 52px;
  border-bottom: 1px solid #eef2f7;
  font-size: 15px;
  color: var(--hy-text);
  cursor: pointer;
}
.li:last-child {
  border-bottom: none;
}
.li span {
  flex: 1;
}
.li .arrow {
  color: var(--hy-text-3);
}
.li.danger {
  color: #ef4444;
}
.ver {
  text-align: center;
  color: var(--hy-text-3);
  font-size: 12px;
  margin: 18px 0 0;
}
@media (min-width: 769px) {
  .grid {
    grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  }
}
</style>
