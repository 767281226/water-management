/**
 * 菜单定义（与后端 sys_permission 的菜单权限点一一对应）。
 * 业务模块尚未开发的页面先显示"建设中"，参见 prototype/index.html。
 */
export interface MenuItem {
  path: string
  title: string
  icon: string
  perm: string
  /** 已实现的页面组件；未提供则显示建设中 */
  component?: () => Promise<unknown>
}

export interface MenuGroup {
  title: string
  icon: string
  perm: string
  children: MenuItem[]
}

export const MENUS: (MenuItem | MenuGroup)[] = [
  { path: '/dashboard', title: '工作台', icon: 'HomeFilled', perm: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue') },
  {
    title: '项目管理',
    icon: 'Folder',
    perm: 'project',
    children: [
      { path: '/project/ledger', title: '项目台账', icon: 'Files', perm: 'project:ledger', component: () => import('@/views/project/ProjectLedgerView.vue') },
      { path: '/weekly/plan', title: '周计划填报', icon: 'EditPen', perm: 'weekly:plan', component: () => import('@/views/weekly/WeeklyPlanView.vue') },
      { path: '/weekly/report', title: '周完成情况', icon: 'Finished', perm: 'weekly:report', component: () => import('@/views/weekly/WeeklyReportView.vue') },
      { path: '/weekly/stat', title: '周报查询统计', icon: 'List', perm: 'weekly:stat', component: () => import('@/views/weekly/WeeklyStatView.vue') },
      { path: '/score', title: '月度考评', icon: 'Star', perm: 'score', component: () => import('@/views/score/ScoreView.vue') },
      { path: '/annual', title: '年度汇总', icon: 'TrendCharts', perm: 'annual', component: () => import('@/views/annual/AnnualView.vue') }
    ]
  },
  {
    title: '资料中心',
    icon: 'Reading',
    perm: 'doc',
    children: [
      { path: '/doc/search', title: '资料查询', icon: 'Search', perm: 'doc:search', component: () => import('@/views/doc/DocSearchView.vue') },
      { path: '/doc/request', title: '我的下载申请', icon: 'Download', perm: 'doc:request', component: () => import('@/views/doc/MyRequestView.vue') },
      { path: '/doc/approve', title: '下载审批', icon: 'Stamp', perm: 'doc:approve', component: () => import('@/views/doc/ApproveView.vue') },
      { path: '/doc/manage', title: '资料维护', icon: 'UploadFilled', perm: 'doc:manage', component: () => import('@/views/doc/DocManageView.vue') }
    ]
  },
  {
    title: '系统管理',
    icon: 'Setting',
    perm: 'system',
    children: [
      { path: '/system/user', title: '用户管理', icon: 'User', perm: 'system:user', component: () => import('@/views/system/user/UserView.vue') },
      { path: '/system/dept', title: '部门管理', icon: 'OfficeBuilding', perm: 'system:dept', component: () => import('@/views/system/dept/DeptView.vue') },
      { path: '/system/role', title: '角色权限', icon: 'Lock', perm: 'system:role', component: () => import('@/views/system/role/RoleView.vue') },
      { path: '/system/config', title: '参数配置', icon: 'Tools', perm: 'system:config', component: () => import('@/views/system/config/ConfigView.vue') },
      { path: '/system/log', title: '日志审计', icon: 'Document', perm: 'system:log', component: () => import('@/views/system/log/LogView.vue') }
    ]
  }
]

export function isGroup(m: MenuItem | MenuGroup): m is MenuGroup {
  return 'children' in m
}

export function flatMenus(): MenuItem[] {
  return MENUS.flatMap((m) => (isGroup(m) ? m.children : [m]))
}
