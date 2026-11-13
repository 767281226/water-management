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
const activePath = computed(() => route.meta.menu || route.path)
const openeds = computed(() =>
  menus.value.filter((m): m is MenuGroup => isGroup(m) && m.children.some((c) => c.path === activePath.value)).map((m) => m.title)
)
/** 面包屑：所属菜单分组 */
const groupTitle = computed(() => menus.value.find((m): m is MenuGroup => isGroup(m) && m.children.some((c) => c.path === activePath.value))?.title)
const asGroup = (m: MenuItem | MenuGroup) => m as MenuGroup
const asItem = (m: MenuItem | MenuGroup) => m as MenuItem

const today = new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })
const sideWidth = computed(() => (collapsed.value ? (isMobile.value ? '0px' : '72px') : '232px'))

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
    <el-aside :width="sideWidth" class="aside" :class="{ mobile: isMobile, folded: collapsed }">
      <div class="brand" @click="router.push('/')">
        <div class="logo">
          <svg viewBox="0 0 24 24"><path d="M12 2.5s7 7.4 7 12.4a7 7 0 0 1-14 0c0-5 7-12.4 7-12.4z" /></svg>
        </div>
        <div v-show="!collapsed" class="brand-text">
          <b>宏禹水利</b>
          <small>项目与资料管理平台</small>
        </div>
      </div>

      <el-scrollbar class="menu-scroll">
        <el-menu
          class="side-menu"
          :default-active="activePath"
          :default-openeds="openeds"
          :collapse="collapsed && !isMobile"
          :collapse-transition="false"
          popper-class="side-popper"
          router
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

      <!-- 底部水波装饰 -->
      <svg class="waves" viewBox="0 0 240 70" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0 30 C 40 10, 80 50, 120 30 S 200 10, 240 30 V70 H0 Z" fill="rgba(25,182,255,0.10)" />
        <path d="M0 42 C 50 25, 90 60, 140 42 S 210 28, 240 44 V70 H0 Z" fill="rgba(47,107,255,0.16)" />
      </svg>
    </el-aside>

    <el-container class="right">
      <el-header class="header">
        <button class="fold" :title="collapsed ? '展开菜单' : '收起菜单'" @click="collapsed = !collapsed">
          <el-icon :size="18"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
        </button>
        <div class="crumb">
          <span v-if="groupTitle && !isMobile" class="crumb-group">{{ groupTitle }}<el-icon><ArrowRight /></el-icon></span>
          <h2>{{ route.meta.title }}</h2>
        </div>
        <div class="spacer" />
        <span v-if="!isMobile" class="date"><el-icon><Calendar /></el-icon>{{ today }}</span>
        <el-dropdown trigger="click" @command="onCommand">
          <div class="user-chip">
            <div class="avatar">{{ auth.user?.realName.slice(-1) }}</div>
            <div v-if="!isMobile" class="nm">
              <span>{{ auth.user?.realName }}</span>
              <small>{{ auth.user?.deptName || '—' }} · {{ auth.user?.roles[0] }}</small>
            </div>
            <el-icon class="caret"><ArrowDown /></el-icon>
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

/* ---------------- 侧边栏 ---------------- */
.aside {
  position: relative;
  background: linear-gradient(180deg, #0b1f4b 0%, #0e2a63 60%, #10357a 100%);
  display: flex;
  flex-direction: column;
  transition: width 0.22s ease;
  overflow: hidden;
  box-shadow: 4px 0 24px rgba(11, 31, 75, 0.18);
  z-index: 10;
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
  background: rgba(11, 31, 75, 0.45);
  backdrop-filter: blur(2px);
  z-index: 29;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 68px;
  padding: 0 18px;
  color: #fff;
  white-space: nowrap;
  cursor: pointer;
  flex: none;
}
.aside.folded:not(.mobile) .brand {
  padding: 0 17px;
}
.logo {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: var(--hy-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  box-shadow: 0 6px 16px rgba(25, 182, 255, 0.35);
}
.logo svg {
  width: 22px;
  height: 22px;
  fill: #fff;
}
.brand-text b {
  display: block;
  font-size: 17px;
  letter-spacing: 1px;
}
.brand-text small {
  font-size: 11px;
  color: #8fa7cf;
}
.menu-scroll {
  flex: 1;
  position: relative;
  z-index: 1;
}
.side-menu {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #b9c8e2;
  --el-menu-hover-text-color: #fff;
  --el-menu-active-color: #fff;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.07);
  --el-menu-item-height: 46px;
  --el-menu-sub-item-height: 42px;
  border-right: none;
  padding: 4px 0 80px;
}
.side-menu :deep(.el-menu) {
  --el-menu-bg-color: transparent;
}
.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  margin: 3px 12px;
  border-radius: 10px;
  transition: background 0.18s, color 0.18s;
}
.side-menu :deep(.el-sub-menu .el-menu-item) {
  padding-left: 46px !important;
  min-width: 0;
}
.side-menu :deep(.el-menu-item.is-active) {
  background: var(--hy-gradient) !important;
  box-shadow: 0 6px 16px rgba(47, 107, 255, 0.4);
  font-weight: 600;
}
.side-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #fff;
}
.side-menu.el-menu--collapse :deep(.el-menu-item),
.side-menu.el-menu--collapse :deep(.el-sub-menu__title) {
  margin: 3px 12px;
  padding: 0 !important;
  justify-content: center;
}
.wip {
  margin-left: auto;
  font-size: 10px;
  color: #7f95bd;
  border: 1px solid rgba(127, 149, 189, 0.4);
  border-radius: 8px;
  padding: 0 6px;
  line-height: 16px;
}
.waves {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: 70px;
  pointer-events: none;
}

/* ---------------- 顶栏 ---------------- */
.right {
  min-width: 0;
}
.header {
  height: 60px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--hy-line);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 22px 0 14px;
  position: sticky;
  top: 0;
  z-index: 5;
}
.fold {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: #f1f5fd;
  color: #4b5568;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
}
.fold:hover {
  background: #e3ecff;
  color: var(--hy-primary);
}
.crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.crumb-group {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--hy-text-3);
  font-size: 14px;
}
.crumb h2 {
  font-size: 16px;
  margin: 0;
  font-weight: 700;
  white-space: nowrap;
}
.spacer {
  flex: 1;
}
.date {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--hy-text-2);
  font-size: 13px;
  background: #f1f5fd;
  border-radius: 16px;
  padding: 5px 12px;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  outline: none;
  padding: 4px 8px 4px 4px;
  border-radius: 24px;
  transition: background 0.15s;
}
.user-chip:hover {
  background: #f1f5fd;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--hy-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  box-shadow: 0 4px 10px rgba(47, 107, 255, 0.3);
}
.nm {
  line-height: 1.3;
  font-size: 14px;
  font-weight: 600;
}
.nm small {
  display: block;
  color: var(--hy-text-3);
  font-size: 11px;
  font-weight: normal;
}
.caret {
  color: var(--hy-text-3);
}

/* ---------------- 内容区 ---------------- */
.main {
  padding: 0;
  background:
    radial-gradient(1200px 300px at 70% -120px, rgba(47, 107, 255, 0.08), transparent 70%),
    var(--hy-bg);
}

@media (max-width: 768px) {
  .header {
    padding: 0 12px;
    height: 54px;
  }
}
</style>
