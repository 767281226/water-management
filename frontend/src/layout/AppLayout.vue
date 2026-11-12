<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { MENUS, isGroup, type MenuGroup, type MenuItem } from '@/router/menu'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isMobile = ref(window.innerWidth < 768)
const collapsed = ref(isMobile.value)
const pwdVisible = ref(false)

function onResize() {
  const m = window.innerWidth < 768
  if (m !== isMobile.value) {
    isMobile.value = m
    collapsed.value = m
  }
}
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

/** 按权限过滤后的菜单 */
const menus = computed(() =>
  MENUS.map((m) => (isGroup(m) ? { ...m, children: m.children.filter((c) => auth.hasPerm(c.perm)) } : m)).filter((m) =>
    isGroup(m) ? m.children.length > 0 : auth.hasPerm(m.perm)
  )
)
const openeds = computed(() =>
  menus.value.filter((m): m is MenuGroup => isGroup(m) && m.children.some((c) => c.path === (route.meta.menu || route.path))).map((m) => m.title)
)
const asGroup = (m: MenuItem | MenuGroup) => m as MenuGroup
const asItem = (m: MenuItem | MenuGroup) => m as MenuItem

function onSelect() {
  if (isMobile.value) collapsed.value = true
}

async function onCommand(cmd: string) {
  if (cmd === 'profile') router.push('/profile')
  if (cmd === 'password') pwdVisible.value = true
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await auth.logout()
    router.replace('/login')
  }
}
</script>

<template>
  <el-container class="layout">
    <div v-if="isMobile && !collapsed" class="mask" @click="collapsed = true" />
    <el-aside :width="collapsed ? (isMobile ? '0px' : '64px') : '220px'" class="aside" :class="{ mobile: isMobile }">
      <div class="brand">
        <div class="logo">
          <svg viewBox="0 0 24 24"><path d="M12 2.5s7 7.4 7 12.4a7 7 0 0 1-14 0c0-5 7-12.4 7-12.4z" /></svg>
        </div>
        <div v-show="!collapsed" class="brand-text"><b>宏禹水利管理</b><small>内部办公系统</small></div>
      </div>
      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="route.meta.menu || route.path"
          :default-openeds="openeds"
          :collapse="collapsed && !isMobile"
          :collapse-transition="false"
          router
          background-color="#0f2a4a"
          text-color="#b9c8db"
          active-text-color="#ffffff"
          @select="onSelect"
        >
          <template v-for="m in menus" :key="isGroup(m) ? m.title : asItem(m).path">
            <el-sub-menu v-if="isGroup(m)" :index="m.title">
              <template #title>
                <el-icon><component :is="m.icon" /></el-icon>
                <span>{{ m.title }}</span>
              </template>
              <el-menu-item v-for="c in asGroup(m).children" :key="c.path" :index="c.path">
                <el-icon><component :is="c.icon" /></el-icon>
                <span>{{ c.title }}</span>
                <span v-if="!c.component" class="wip">建设中</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="asItem(m).path">
              <el-icon><component :is="m.icon" /></el-icon>
              <template #title>{{ asItem(m).title }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container class="right">
      <el-header class="header">
        <el-button text circle @click="collapsed = !collapsed">
          <el-icon :size="18"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
        </el-button>
        <h2 class="title">{{ route.meta.title }}</h2>
        <div class="spacer" />
        <el-dropdown trigger="click" @command="onCommand">
          <div class="user-chip">
            <el-avatar :size="32" class="avatar">{{ auth.user?.realName.slice(-1) }}</el-avatar>
            <div v-if="!isMobile" class="nm">
              <span>{{ auth.user?.realName }}</span>
              <small>{{ auth.user?.deptName || '—' }} · {{ auth.user?.roles.join(' / ') }}</small>
            </div>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile" icon="User">个人中心</el-dropdown-item>
              <el-dropdown-item command="password" icon="Lock">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" icon="SwitchButton" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <ChangePasswordDialog
      :model-value="pwdVisible || auth.mustChangePwd"
      :force="auth.mustChangePwd"
      @update:model-value="pwdVisible = $event"
    />
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: var(--hy-nav);
  display: flex;
  flex-direction: column;
  transition: width 0.2s;
  overflow: hidden;
}
.aside.mobile {
  position: fixed;
  z-index: 30;
  top: 0;
  bottom: 0;
  left: 0;
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 30, 50, 0.4);
  z-index: 29;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 15px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  white-space: nowrap;
}
.logo {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--hy-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.logo svg {
  width: 20px;
  height: 20px;
  fill: #fff;
}
.brand-text b {
  display: block;
  font-size: 15px;
}
.brand-text small {
  font-size: 11px;
  color: #b9c8db;
}
.menu-scroll {
  flex: 1;
}
.el-menu {
  border-right: none;
}
:deep(.el-menu-item.is-active) {
  background: var(--hy-primary) !important;
  border-radius: 8px;
}
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  margin: 2px 8px;
  border-radius: 8px;
}
:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: var(--hy-nav-2) !important;
}
.wip {
  margin-left: auto;
  font-size: 11px;
  color: #7189a8;
}
.right {
  min-width: 0;
}
.header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e3e7ee;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
}
.title {
  font-size: 16px;
  margin: 0;
  font-weight: 600;
}
.spacer {
  flex: 1;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.avatar {
  background: var(--hy-primary);
}
.nm {
  line-height: 1.25;
}
.nm small {
  display: block;
  color: #8a94a6;
  font-size: 11px;
}
.main {
  padding: 0;
  background: var(--hy-bg);
}
</style>
