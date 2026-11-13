<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { scoreApi, type RuleRef, type ScoreVO } from '@/api/score'

const props = defineProps<{ modelValue: boolean; score: ScoreVO | null; rules: RuleRef[] }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>()

const values = ref<Record<string, number>>({})
const comment = ref('')
const saving = ref(false)

const total = computed(() => props.rules.reduce((s, r) => s + (values.value[r.ruleKey] ?? 0) * r.weight, 0) / 100)
const ref0 = computed(() => props.score?.reference)

watch(
  () => props.modelValue,
  (v) => {
    if (!v || !props.score) return
    const old = Object.fromEntries(props.score.items.map((i) => [i.ruleKey, i.score]))
    values.value = Object.fromEntries(props.rules.map((r) => [r.ruleKey, old[r.ruleKey] ?? 80]))
    comment.value = props.score.comment || ''
  }
)

async function submit() {
  saving.value = true
  try {
    await scoreApi.grade(props.score!.id, props.rules.map((r) => ({ ruleKey: r.ruleKey, score: values.value[r.ruleKey] })), comment.value)
    ElMessage.success('打分已提交，等待公司领导审核发布')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="`考评打分 · ${score?.project.name ?? ''}`" width="720px" top="6vh" :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)">
    <template v-if="score && ref0">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title>
          参考数据：计划工期 {{ ref0.planDuration ?? '—' }} 天，实际 {{ ref0.actualDuration ?? '—' }} 天<template v-if="ref0.delayDays !== null">（{{ ref0.delayDays > 0 ? `延期 ${ref0.delayDays} 天` : `提前 ${-ref0.delayDays} 天` }}）</template>；
          周报完成率 {{ ref0.avgCompletion ?? '—' }}%；累计工时 {{ Number(ref0.totalDays) }} 人·天；逾期填报 {{ ref0.overduePlans }} 次
        </template>
      </el-alert>
      <div v-for="r in rules" :key="r.ruleKey" class="rule">
        <div class="rn"><b>{{ r.name }}（{{ r.weight }}%）</b><small>{{ r.description }}</small></div>
        <el-slider v-model="values[r.ruleKey]" :min="0" :max="100" class="sl" />
        <el-input-number v-model="values[r.ruleKey]" :min="0" :max="100" controls-position="right" style="width: 100px" />
      </div>
      <div class="total">加权总分 <span>{{ total.toFixed(1) }}</span></div>
      <el-input v-model="comment" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="评语（员工可以看到）" />
    </template>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">提交打分</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.rule {
  display: grid;
  grid-template-columns: 1fr 200px 100px;
  gap: 14px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #eef1f5;
}
.rn small {
  display: block;
  color: #8a94a6;
  font-size: 12px;
}
.total {
  text-align: right;
  margin: 14px 0;
}
.total span {
  font-size: 26px;
  font-weight: 700;
  color: var(--el-color-primary-dark-2);
  margin-left: 6px;
}
@media (max-width: 768px) {
  .rule {
    grid-template-columns: 1fr 100px;
  }
  .sl {
    display: none;
  }
}
</style>
