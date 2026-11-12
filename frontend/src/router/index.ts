import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { flatMenus } from './menu'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    perm?: string
    public?: boolean
    /** 高亮的菜单（详情页等非菜单页面） */
    menu?: string
  }
}

const menuRoutes: RouteRecordRaw[] = flatMenus().map((m) => ({
  path: m.path.slice(1),
  component: m.component ?? (() => import('@/views/PlaceholderView.vue')),
  meta: { title: m.title, perm: m.perm }
}))

const routes: RouteRecordRaw[] = [
  { path: '/login', component: () => import('@/views/login/LoginView.vue'), meta: { public: true, title: '登录' } },
  {
    path: '/',
    component: () => import('@/layout/AppLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      ...menuRoutes,
      {
        path: 'project/ledger/:id(\\d+)',
        component: () => import('@/views/project/ProjectDetailView.vue'),
        meta: { title: '项目详情', perm: 'project:ledger', menu: '/project/ledger' }
      },
      { path: 'profile', component: () => import('@/views/profile/ProfileView.vue'), meta: { title: '个人中心' } },
      { path: '403', component: () => import('@/views/error/ForbiddenView.vue'), meta: { title: '无权访问' } }
    ]
  },
  { path: '/:pathMatch(.*)*', component: () => import('@/views/error/NotFoundView.vue'), meta: { public: true, title: '页面不存在' } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (to.path === '/login' && auth.token) return '/'
    return true
  }
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      await auth.logout(false)
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (to.meta.perm && !auth.hasPerm(to.meta.perm)) return '/403'
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 宏禹水利管理软件` : '宏禹水利管理软件'
})

export default router
