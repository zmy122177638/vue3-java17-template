import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            // 后端服务地址（apps/backend，端口见 application.yaml）
            target: 'http://localhost:4838/api',
            ws: true,
          },
        },
      },
    },
  };
});
