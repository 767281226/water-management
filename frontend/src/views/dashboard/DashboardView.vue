<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { flatMenus } from '@/router/menu'
import { DATA_SCOPE_LABEL } from '@/api/system'
import { fmtTime } from '@/utils/format'

const auth = useAuthStore()
const hour = new Date().getHours()
const greeting = hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'
const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const shortcuts = computed(() => flatMenus().filter((m) => m.path !== '/dashboard' && auth.hasPerm(m.perm)))
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h3>{{ greeting }}，{{ auth.user?.realName }}</h3>
        <p>{{ today }}</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <div class="card info">
          <el-avatar :size="56" class="avatar">{{ auth.user?.realName.slice(-1) }}</el-avatar>
          <div>
            <b>{{ auth.user?.realName }}</b>
            <div class="muted small">{{ auth.user?.employeeNo }} · {{ auth.user?.deptName || '未分配部门' }} · {{ auth.user?.title || '—' }}</div>
            <div class="tags">
              <el-tag v-for="r in auth.user?.roles" :key="r" size="small" effect="plain">{{ r }}</el-tag>
            </div>
          </div>
        </div>
        <div class="card kv">
          <div><span>数据范围</span><b>{{ auth.user ? DATA_SCOPE_LABEL[auth.user.dataScope] : '—' }}</b></div>
          <div><span>权限点</span><b>{{ auth.user?.superAdmin ? '全部（超级管理员）' : `${auth.user?.permissions.length} 个` }}</b></div>
          <div><span>上次登录</span><b>{{ fmtTime(auth.user?.lastLoginAt) }}</b></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="16">
        <div class="card">
          <h4>常用功能</h4>
          <div class="shortcuts">
            <router-link v-for="m in shortcuts" :key="m.path" :to="m.path" class="sc">
              <el-icon :size="22"><component :is="m.icon" /></el-icon>
              <span>{{ m.title }}</span>
              <small v-if="!m.component">建设中</small>
            </router-link>
          </div>
        </div>
        <el-alert type="info" :closable="false" show-icon class="notice">
          <template #title>当前版本已上线"系统管理"模块；周报填报、月度考评、资料中心等业务模块正在开发中，功能设计见演示原型。</template>
        </el-alert>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.card {
  margin-bottom: 16px;
}
.info {
  display: flex;
  gap: 14px;
  align-items: center;
}
.info b {
  font-size: 17px;
}
.avatar {
  background: var(--hy-primary);
  font-size: 20px;
  flex: none;
}
.tags {
  margin-top: 6px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.kv div {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #eef1f5;
}
.kv div:last-child {
  border-bottom: none;
}
.kv span {
  color: #8a94a6;
}
h4 {
  margin: 0 0 14px;
}
.shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
}
.sc {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 16px 8px;
  border-radius: 10px;
  background: #f7f9fb;
  color: #1b2430;
  text-decoration: none;
  font-size: 13px;
}
.sc:hover {
  background: #eaf1fb;
  color: var(--hy-primary);
}
.sc small {
  font-size: 11px;
  color: #8a94a6;
}
.notice {
  margin-bottom: 16px;
}
</style>
