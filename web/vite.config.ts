import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import pkg from './package.json' with { type: 'json' }

export default defineConfig({
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icons/*.png', 'images/foods/*'],
      manifest: {
        name: '윈난 7박 8일 · 딸들과 함께 여행을',
        short_name: '윈난 여행',
        description: '가족 여행 일정·숙소·택시 카드',
        lang: 'ko',
        theme_color: '#1F6F6B',
        background_color: '#F6F3EC',
        display: 'standalone',
        start_url: '.',
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // 암호문 데이터·키·음식 사진까지 미리 캐시 → 오프라인에서도 로그인·열람
        globPatterns: ['**/*.{js,css,html,png,jpg,jpeg,svg,webmanifest,enc,json}'],
        navigateFallback: 'index.html',
        cleanupOutdatedCaches: true,
      },
    }),
  ],
  build: { target: 'es2022' },
})
