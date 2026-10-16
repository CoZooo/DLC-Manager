const fs = require("node:fs");
const path = require("node:path");

function prepareDefinition() {
  const source = path.resolve(__dirname, "../../../../src/main/resources/META-INF/dlc-manager/dlc-definition.json");
  const target = path.resolve(__dirname, "../generated/dlc-definition.json");
  const text = fs.readFileSync(source, "utf8");
  JSON.parse(text);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, text);
}

if (require.main === module) prepareDefinition();
module.exports = { prepareDefinition };
