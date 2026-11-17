import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En desarrollo el front end llama a /api/sri y /api/ant y Vite los redirige a cada microservicio.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/sri': 'http://localhost:8081',
      '/api/ant': 'http://localhost:8082',
    },
  },
});
