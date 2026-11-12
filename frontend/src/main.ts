import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as Icons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import './styles/index.css'

import App from './App.vue'
import router from './router'
import { permDirective } from './directives/perm'

const app = createApp(App)
// 接口错误已由 http 拦截器提示；确认框取消、表单校验失败也属于正常流程，不再输出到控制台
app.config.errorHandler = (err) => {
  if (err === 'cancel' || err === 'close') return
  if (err && typeof err === 'object' && ('isAxiosError' in err || !(err instanceof Error))) return
  if (err instanceof Error && err.name === 'ApiError') return
  console.error(err)
}
window.addEventListener('unhandledrejection', (e) => {
  const r = e.reason
  if (r === 'cancel' || r === 'close' || (r instanceof Error && r.name === 'ApiError') ||
    (r && typeof r === 'object' && ('isAxiosError' in r || !(r instanceof Error)))) {
    e.preventDefault()
  }
})
for (const [name, comp] of Object.entries(Icons)) {
  app.component(name, comp)
}
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.directive('perm', permDirective)
app.mount('#app')
