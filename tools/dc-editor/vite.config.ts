import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const repoName = process.env.GITHUB_PAGES_REPO || "";
const embedded = process.env.DC_EDITOR_EMBEDDED === "true";

export default defineConfig({
  base: embedded ? "./" : repoName ? `/${repoName}/` : "/",
  plugins: [react()],
  server: {
    port: 4176
  }
});
