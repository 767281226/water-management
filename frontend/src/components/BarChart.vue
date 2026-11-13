<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 单系列柱状图（SVG，无第三方依赖）。
 * - 竖向：类别在 X 轴；横向（horizontal）：用于排名，数值直接标注在条形末端
 * - 悬停显示提示；null 表示无数据
 */
export interface BarDatum {
  label: string
  value: number | null
  tip?: string
}

const props = withDefaults(
  defineProps<{ data: BarDatum[]; unit?: string; max?: number; horizontal?: boolean; highlight?: string; height?: number }>(),
  { unit: '', horizontal: false, height: 220 }
)

/** 画布宽度跟随容器，保证文字始终是 11px、高度固定，不随容器放大 */
const W = ref(640)
const hover = ref<{ x: number; y: number; text: string } | null>(null)
const wrap = ref<HTMLDivElement>()
let ro: ResizeObserver | null = null
onMounted(() => {
  ro = new ResizeObserver(([e]) => {
    if (e.contentRect.width > 0) W.value = Math.round(e.contentRect.width)
  })
  ro.observe(wrap.value!)
})
onBeforeUnmount(() => ro?.disconnect())

/** 取整的刻度上限 */
const top = computed(() => {
  if (props.max) return props.max
  const m = Math.max(0, ...props.data.map((d) => d.value ?? 0))
  if (m <= 0) return 4
  // 4 个刻度，每格取 1 / 2 / 2.5 / 5 × 10ⁿ
  const raw = m / 4
  const p = Math.pow(10, Math.floor(Math.log10(raw)))
  const step = [1, 2, 2.5, 5, 10].map((f) => f * p).find((v) => v >= raw) ?? p * 10
  return step < 1 && Number.isInteger(m) ? 4 : step * 4
})
const ticks = computed(() => [0, 1, 2, 3, 4].map((i) => +(top.value * i / 4).toFixed(2)))

// ---- 竖向
const vh = computed(() => props.height)
const pl = 36, pb = 24, pt = 14
const gap = computed(() => (W.value - pl) / Math.max(1, props.data.length))
const bw = computed(() => Math.min(32, gap.value * 0.56))
const y = (v: number) => pt + (vh.value - pt - pb) * (1 - v / top.value)

// ---- 横向
const rowH = 28
const hl = 70, hr = 48
const hh = computed(() => props.data.length * rowH + 6)
const x = (v: number) => hl + (W.value - hl - hr) * (v / top.value)

function barPath(x0: number, y0: number, w: number, h: number, horizontal: boolean) {
  const r = Math.min(4, horizontal ? w : h, horizontal ? h / 2 : w / 2)
  if (h <= 0 || w <= 0) return ''
  if (horizontal) {
    return `M${x0},${y0} H${x0 + w - r} Q${x0 + w},${y0} ${x0 + w},${y0 + r} V${y0 + h - r} Q${x0 + w},${y0 + h} ${x0 + w - r},${y0 + h} H${x0} Z`
  }
  return `M${x0},${y0 + h} V${y0 + r} Q${x0},${y0} ${x0 + r},${y0} H${x0 + w - r} Q${x0 + w},${y0} ${x0 + w},${y0 + r} V${y0 + h} Z`
}

function show(e: MouseEvent, d: BarDatum) {
  const rect = wrap.value!.getBoundingClientRect()
  hover.value = {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top,
    text: d.tip ?? `${d.label}：${d.value === null ? '无数据' : d.value + (props.unit ? ' ' + props.unit : '')}`
  }
}
</script>

<template>
  <div ref="wrap" class="chart" @mouseleave="hover = null">
    <!-- 竖向 -->
    <svg v-if="!horizontal" :viewBox="`0 0 ${W} ${vh}`" role="img">
      <g v-for="t in ticks" :key="t">
        <line class="grid" :x1="pl" :x2="W" :y1="y(t)" :y2="y(t)" />
        <text class="axis" :x="pl - 6" :y="y(t) + 4" text-anchor="end">{{ t }}</text>
      </g>
      <g v-for="(d, i) in data" :key="d.label" @mousemove="show($event, d)">
        <rect class="hit" :x="pl + gap * i" :y="pt" :width="gap" :height="vh - pt - pb" />
        <path v-if="d.value" class="bar" :class="{ hl: d.label === highlight }"
          :d="barPath(pl + gap * i + (gap - bw) / 2, y(d.value), bw, vh - pb - y(d.value), false)" />
        <text class="axis" :x="pl + gap * i + gap / 2" :y="vh - 6" text-anchor="middle">{{ d.label }}</text>
      </g>
    </svg>
    <!-- 横向（排名） -->
    <svg v-else :viewBox="`0 0 ${W} ${hh}`" role="img">
      <g v-for="(d, i) in data" :key="d.label + i" @mousemove="show($event, d)">
        <rect class="hit" x="0" :y="i * rowH + 2" :width="W" :height="rowH" />
        <text class="axis" :class="{ strong: d.label === highlight }" :x="hl - 8" :y="i * rowH + 20" text-anchor="end">{{ d.label }}</text>
        <path v-if="d.value" class="bar" :class="{ hl: d.label === highlight }" :d="barPath(hl, i * rowH + 8, x(d.value) - hl, 16, true)" />
        <text class="val" :x="(d.value ? x(d.value) : hl) + 6" :y="i * rowH + 20">{{ d.value ?? '—' }}</text>
      </g>
    </svg>
    <div v-if="hover" class="tip" :style="{ left: hover.x + 12 + 'px', top: hover.y - 34 + 'px' }">{{ hover.text }}</div>
  </div>
</template>

<style scoped>
.chart {
  position: relative;
}
svg {
  width: 100%;
  height: auto;
  display: block;
  overflow: visible;
}
.grid {
  stroke: #e8ebf0;
  stroke-width: 1;
}
.axis {
  fill: #8a94a6;
  font-size: 11px;
}
.axis.strong {
  fill: #1b2430;
  font-weight: 700;
}
.val {
  fill: #1b2430;
  font-size: 11px;
  font-weight: 600;
}
.bar {
  fill: #2f6bff;
}
.bar.hl,
g:hover .bar {
  fill: #2656cc;
}
.hit {
  fill: transparent;
  cursor: default;
}
.tip {
  position: absolute;
  pointer-events: none;
  background: #1b2430;
  color: #fff;
  font-size: 12px;
  padding: 5px 9px;
  border-radius: 6px;
  white-space: nowrap;
  z-index: 5;
}
</style>
