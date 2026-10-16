const fs = require("node:fs");
const path = require("node:path");
const { readDefinition, writeGenerated } = require("./syntax");
const { prepareDefinition } = require("./prepare-definition");
async function main() {
  const source = process.env.DC_LANGUAGE_SERVER_JAR || path.resolve(__dirname, "../../../dclanguage/lsp/build/libs/dclanguage-lsp.jar");
  const target = path.resolve(__dirname, "../server/dclanguage-lsp.jar");
  if (!fs.existsSync(source)) throw new Error("Build tools/dclanguage/lsp shadowJar before packaging: " + source);
  const definition = await readDefinition(source);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.copyFileSync(source, target);
  writeGenerated(path.resolve(__dirname, ".."), definition);
  prepareDefinition();
  console.log("Bundled language server and generated editor syntax: " + definition.definitionHash);
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
