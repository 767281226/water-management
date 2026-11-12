import type { Directive } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 按钮级权限：没有权限时移除元素。
 * 用法：v-perm="'system:user:create'" 或 v-perm="['a', 'b']"（满足任一即可）
 */
export const permDirective: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    if (!useAuthStore().hasPerm(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  }
}
