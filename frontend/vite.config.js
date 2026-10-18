import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// /api chuyển sang backend Spring Boot ở :8080, nên frontend không cần cấu hình CORS.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    strictPort: true, // cổng bận thì báo lỗi, không âm thầm chuyển sang 5174 trong khi script mở 5173
    proxy: { '/api': 'http://127.0.0.1:8080' },
  },
});
