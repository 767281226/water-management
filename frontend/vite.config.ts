import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        // 代理后已是同源请求，去掉 Origin 头，避免后端按跨域请求处理
        configure: (proxy) => proxy.on('proxyReq', (req) => req.removeHeader('origin'))
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 1500
  }
})
