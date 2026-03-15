import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const repoName = process.env.GITHUB_PAGES_REPO || "";

export default defineConfig({
  base: repoName ? `/${repoName}/` : "/",
  plugins: [react()],
  server: {
    port: 4176
  }
});
