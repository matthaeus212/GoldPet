import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  esbuild: {
    pure: mode === 'production' ? ['console.log', 'console.debug'] : [],
  },
  server: {
    port: 5173,
    host: true, // 외부 접근 허용 (Flutter WebView에서 접근 가능하도록)
    allowedHosts: ['*.ciccosoft.com', 'www.ciccosoft.com', 'app.ciccosoft.com', '10.80.1.59'],
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router-dom'],
          query: ['@tanstack/react-query'],
          ui: ['zustand', 'axios'],
          chart: ['chart.js', 'react-chartjs-2'],
          recharts: ['recharts'],
          websocket: ['@stomp/stompjs', 'sockjs-client'],
          motion: ['motion'],
        },
      },
    },
  },
  publicDir: 'public', // public 디렉토리 명시
  define: {
    global: 'globalThis', // SockJS compatibility for browser
  },
}));
