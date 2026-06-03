import { defineConfig } from "vite";

export default defineConfig({
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:9910",
        changeOrigin: true
      }
    }
  }
});
