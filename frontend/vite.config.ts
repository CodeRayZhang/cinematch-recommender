import process from "node:process";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": process.env.CINEMATCH_API_TARGET || "http://127.0.0.1:8080",
      "/images/catalog":
        process.env.CINEMATCH_API_TARGET || "http://127.0.0.1:8080",
    },
  },
});
