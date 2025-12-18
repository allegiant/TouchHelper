import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: './', // 🔥 关键：确保在 Android WebView (file://) 中能加载资源
  server: {
    host: '0.0.0.0',
    port: 5173
  }
})
