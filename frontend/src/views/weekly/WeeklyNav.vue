<script setup lang="ts">
/** 周计划 / 周完成情况 切换，保留当前周次 */
defineProps<{ active: 'plan' | 'report'; week: string }>()
</script>

<template>
  <div class="seg">
    <router-link :to="{ path: '/weekly/plan', query: { week } }" :class="{ active: active === 'plan' }">周一 · 本周计划</router-link>
    <router-link :to="{ path: '/weekly/report', query: { week } }" :class="{ active: active === 'report' }">周五 · 完成情况</router-link>
  </div>
</template>

<style scoped>
.seg {
  display: inline-flex;
  background: #eef1f5;
  border-radius: 8px;
  padding: 3px;
}
.seg a {
  padding: 6px 14px;
  border-radius: 6px;
  color: #5a6577;
  font-size: 13px;
  text-decoration: none;
}
.seg a.active {
  background: #fff;
  color: var(--hy-primary);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(16, 30, 54, 0.08);
}
</style>
