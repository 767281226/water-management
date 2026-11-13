<script setup lang="ts">
import type { RequestVO } from '@/api/doc'
import { fmtTime } from '@/utils/format'

/** 审批进度 */
defineProps<{ request: RequestVO }>()

const ICON: Record<string, string> = { APPROVED: 'CircleCheckFilled', REJECTED: 'CircleCloseFilled', PENDING: 'Clock', WAITING: 'MoreFilled', CANCELLED: 'RemoveFilled' }
</script>

<template>
  <div class="steps">
    <div class="step done">
      <el-icon><CircleCheckFilled /></el-icon>
      <div><b>提交申请</b><small>{{ request.applicantName }} · {{ fmtTime(request.createdAt) }}</small></div>
    </div>
    <template v-for="s in request.steps" :key="s.stepNo">
      <span class="line" :class="{ on: s.status !== 'WAITING' && s.status !== 'CANCELLED' }" />
      <div class="step" :class="s.status.toLowerCase()">
        <el-icon><component :is="ICON[s.status]" /></el-icon>
        <div>
          <b>{{ s.name }}</b>
          <small v-if="s.approverName">{{ s.approverName }} · {{ fmtTime(s.actedAt) }}</small>
          <small v-else>{{ s.status === 'PENDING' ? '待审批' : s.status === 'CANCELLED' ? '已取消' : '等待上一步' }}</small>
          <em v-if="s.comment">{{ s.comment }}</em>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.steps {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}
.step {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  color: var(--hy-text-3);
  font-size: 12px;
  max-width: 220px;
}
.step .el-icon {
  font-size: 18px;
  margin-top: 1px;
  flex: none;
}
.step b {
  display: block;
  color: var(--hy-text);
  font-size: 13px;
}
.step small {
  display: block;
}
.step em {
  display: block;
  font-style: normal;
  color: var(--hy-text-2);
  margin-top: 2px;
}
.step.done .el-icon,
.step.approved .el-icon {
  color: #10b981;
}
.step.rejected .el-icon {
  color: #ef4444;
}
.step.rejected em {
  color: #ef4444;
}
.step.pending .el-icon {
  color: #f59e0b;
}
.line {
  width: 28px;
  height: 2px;
  background: #e3e8f0;
  margin-top: 10px;
  border-radius: 1px;
}
.line.on {
  background: var(--hy-gradient);
}
</style>
