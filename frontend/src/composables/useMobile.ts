import { onBeforeUnmount, onMounted, ref } from 'vue'

/** 手机端断点（与 CSS 媒体查询保持一致） */
export const MOBILE_BREAKPOINT = 768

const isMobile = ref(typeof window !== 'undefined' && window.innerWidth < MOBILE_BREAKPOINT)
let listeners = 0
const onResize = () => {
  isMobile.value = window.innerWidth < MOBILE_BREAKPOINT
}

/** 当前是否为手机屏幕（全局共享一个 resize 监听） */
export function useMobile() {
  onMounted(() => {
    if (listeners++ === 0) window.addEventListener('resize', onResize)
    onResize()
  })
  onBeforeUnmount(() => {
    if (--listeners === 0) window.removeEventListener('resize', onResize)
  })
  return isMobile
}
