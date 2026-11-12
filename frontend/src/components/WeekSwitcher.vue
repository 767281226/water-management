<script setup lang="ts">
import { computed } from 'vue'
import { addWeeks, mondayOf, offsetLabel } from '@/utils/week'

/** 周次切换：上一周 / 本周 / 下一周 */
const props = defineProps<{ modelValue: string; label?: string; min?: number; max?: number }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()

const offset = computed(() => Math.round((new Date(props.modelValue).getTime() - new Date(mondayOf()).getTime()) / (7 * 864e5)))
const canPrev = computed(() => props.min === undefined || offset.value > props.min)
const canNext = computed(() => props.max === undefined || offset.value < props.max)

function move(n: number) {
  emit('update:modelValue', addWeeks(props.modelValue, n))
}
</script>

<template>
  <div class="week-switcher">
    <el-button-group>
      <el-button icon="ArrowLeft" :disabled="!canPrev" @click="move(-1)" />
      <el-button :type="offset === 0 ? 'primary' : ''" @click="emit('update:modelValue', mondayOf())">本周</el-button>
      <el-button icon="ArrowRight" :disabled="!canNext" @click="move(1)" />
    </el-button-group>
    <span class="label">
      <el-tag size="small" :type="offset === 0 ? 'primary' : 'info'" effect="plain">{{ offsetLabel(offset) }}</el-tag>
      {{ label }}
    </span>
  </div>
</template>

<style scoped>
.week-switcher {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.label {
  font-size: 14px;
  color: #5a6577;
  display: flex;
  gap: 6px;
  align-items: center;
}
</style>
