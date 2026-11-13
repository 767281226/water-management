<script setup lang="ts">
import { computed } from 'vue'

/** 文件类型图标：按扩展名着色 */
const props = withDefaults(defineProps<{ ext?: string | null; size?: number }>(), { ext: '', size: 40 })

const COLORS: Record<string, string> = {
  pdf: '#ef4444', doc: '#2f6bff', docx: '#2f6bff', wps: '#2f6bff', txt: '#64748b',
  xls: '#10b981', xlsx: '#10b981', et: '#10b981', ppt: '#f97316', pptx: '#f97316',
  dwg: '#0ea5e9', dxf: '#0ea5e9', zip: '#a16207', rar: '#a16207', '7z': '#a16207',
  jpg: '#8b5cf6', jpeg: '#8b5cf6', png: '#8b5cf6', gif: '#8b5cf6', bmp: '#8b5cf6', webp: '#8b5cf6', tif: '#8b5cf6', tiff: '#8b5cf6'
}
const label = computed(() => (props.ext || 'FILE').toUpperCase().slice(0, 4))
const color = computed(() => COLORS[(props.ext || '').toLowerCase()] ?? '#94a3b8')
</script>

<template>
  <div class="fi" :style="{ width: size + 'px', height: size * 1.18 + 'px', '--c': color }">
    <span>{{ label }}</span>
  </div>
</template>

<style scoped>
.fi {
  position: relative;
  border-radius: 6px;
  background: linear-gradient(160deg, var(--c), color-mix(in srgb, var(--c) 75%, #000));
  display: flex;
  align-items: flex-end;
  justify-content: center;
  flex: none;
  box-shadow: 0 4px 10px color-mix(in srgb, var(--c) 30%, transparent);
}
.fi::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  border-style: solid;
  border-width: 0 11px 11px 0;
  border-color: transparent rgba(255, 255, 255, 0.55) transparent transparent;
}
.fi span {
  color: #fff;
  font-size: 10px;
  font-weight: 800;
  padding-bottom: 6px;
  letter-spacing: 0.3px;
}
</style>
