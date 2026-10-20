import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const editorRoot = path.resolve(fileURLToPath(new URL("..", import.meta.url)));
const repoRoot = path.resolve(editorRoot, "../..");
const properties = fs.readFileSync(path.join(repoRoot, "gradle.properties"), "utf8");
const version = properties.match(/^mod_version=(.+)$/m)?.[1]?.trim();

if (!version || !/^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/.test(version)) {
  throw new Error(`Invalid mod_version in gradle.properties: ${version || "missing"}`);
}

const output = path.join(editorRoot, "src-tauri/tauri.generated.conf.json");
fs.writeFileSync(output, `${JSON.stringify({ version }, null, 2)}\n`);
console.log(`Generated Tauri metadata version ${version}.`);
