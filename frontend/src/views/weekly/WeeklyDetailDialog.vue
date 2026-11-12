<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { weeklyApi, type WeeklyPlan } from '@/api/weekly'
import { useAuthStore } from '@/stores/auth'
import { fmtTime } from '@/utils/format'
import { fmtDays } from '@/utils/week'

const props = defineProps<{ modelValue: boolean; planId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; changed: [] }>()

const auth = useAuthStore()
const plan = ref<WeeklyPlan | null>(null)
const comment = ref('')
const loading = ref(false)
const saving = ref(false)

const canComment = computed(() => !!plan.value && plan.value.userId !== auth.user?.id && auth.hasPerm('weekly:stat:comment'))

watch(
  () => [props.modelValue, props.planId],
  async () => {
    if (!props.modelValue || !props.planId) return
    loading.value = true
    try {
      plan.value = await weeklyApi.detail(props.planId)
      comment.value = plan.value.comment || ''
    } finally {
      loading.value = false
    }
  }
)

async function saveComment() {
  saving.value = true
  try {
    await weeklyApi.comment(plan.value!.id, comment.value)
    ElMessage.success('点评已保存')
    emit('changed')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}

async function returnPlan() {
  const { value } = await ElMessageBox.prompt(`退回后「${plan.value!.userName}」需要重新提交计划和完成情况。`, '退回周报', {
    inputPlaceholder: '请填写退回原因，员工可以看到',
    inputValidator: (v) => (!!v && v.trim().length > 0) || '请填写退回原因'
  })
  await weeklyApi.returnPlan(plan.value!.id, value)
  ElMessage.success('已退回')
  emit('changed')
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="plan ? `${plan.userName} · ${plan.week.label}` : '周报详情'" width="900px" top="5vh"
    @update:model-value="emit('update:modelValue', $event)">
    <div v-loading="loading">
      <template v-if="plan">
        <div class="meta">
          <el-tag :type="plan.planStatus === 'RETURNED' ? 'danger' : 'success'" size="small">{{ plan.planStatus === 'RETURNED' ? '已退回' : '计划已提交' }}</el-tag>
          <span class="muted small">{{ fmtTime(plan.planSubmittedAt) }}</span>
          <el-tag v-if="plan.planOverdue" type="warning" size="small">计划逾期</el-tag>
          <el-tag :type="plan.reportStatus === 'SUBMITTED' ? 'success' : 'info'" size="small">{{ plan.reportStatus === 'SUBMITTED' ? '完成情况已提交' : '完成情况未提交' }}</el-tag>
          <el-tag v-if="plan.reportOverdue" type="warning" size="small">完成情况逾期</el-tag>
          <span v-if="plan.completionRate !== null">完成率 <b>{{ plan.completionRate }}%</b></span>
          <span class="muted small">计划工时 {{ Number(plan.planDays) }} 天</span>
        </div>
        <el-alert v-if="plan.returnReason" type="error" :closable="false" :title="`退回原因：${plan.returnReason}`" style="margin-bottom: 10px" />
        <el-table :data="plan.items" border size="small">
          <el-table-column label="项目" min-width="150">
            <template #default="{ row }">
              <el-tag v-if="row.extra" size="small" type="warning" effect="plain">计划外</el-tag>
              {{ row.projectName || '其他工作' }}
            </template>
          </el-table-column>
          <el-table-column prop="content" label="工作内容" min-width="150" />
          <el-table-column label="计划目标" min-width="110"><template #default="{ row }">{{ row.target || '—' }}</template></el-table-column>
          <el-table-column label="计划 / 实际工时" width="120"><template #default="{ row }">{{ fmtDays(row.planDays) }} / {{ fmtDays(row.actualDays) }}</template></el-table-column>
          <el-table-column label="完成比例" width="90" align="center">
            <template #default="{ row }">
              <b v-if="row.completion !== null" :style="{ color: row.completion < 100 ? 'var(--el-color-warning)' : 'var(--el-color-success)' }">{{ row.completion }}%</b>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="实际情况 / 原因" min-width="160">
            <template #default="{ row }">
              <div>{{ row.actualContent || '—' }}</div>
              <div v-if="row.reason" class="small" style="color: var(--el-color-warning)">原因：{{ row.reason }}</div>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="canComment" class="comment">
          <div class="small muted">负责人点评（员工可以看到）</div>
          <el-input v-model="comment" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="选填" />
        </div>
        <el-alert v-else-if="plan.comment" type="info" :closable="false" style="margin-top: 12px" :title="`${plan.commentByName} 点评：${plan.comment}`" />
      </template>
    </div>
    <template #footer>
      <template v-if="canComment">
        <el-button v-if="plan?.planStatus !== 'RETURNED'" type="danger" plain @click="returnPlan">退回重填</el-button>
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
        <el-button type="primary" :loading="saving" @click="saveComment">保存点评</el-button>
      </template>
      <el-button v-else @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.comment {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
