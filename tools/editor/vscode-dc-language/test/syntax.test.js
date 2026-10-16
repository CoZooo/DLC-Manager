const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const textmate = require("vscode-textmate");
const oniguruma = require("vscode-oniguruma");
const { readDefinition, generate } = require("../scripts/syntax");
const jar = process.env.DC_LANGUAGE_SERVER_JAR || path.resolve(__dirname, "../../../dclanguage/lsp/build/libs/dclanguage-lsp.jar");
const wasm = fs.readFileSync(require.resolve("vscode-oniguruma/release/onig.wasm"));
const onigLib = oniguruma.loadWASM(wasm.buffer.slice(wasm.byteOffset, wasm.byteOffset + wasm.byteLength)).then(() => ({
  createOnigScanner: (patterns) => new oniguruma.OnigScanner(patterns), createOnigString: (value) => new oniguruma.OnigString(value)
}));
async function grammar(definition) {
  const generated = generate(definition);
  const registry = new textmate.Registry({ onigLib, loadGrammar: async () => textmate.parseRawGrammar(JSON.stringify(generated.grammar), "dc.json") });
  return { generated, grammar: await registry.loadGrammar("source.dc") };
}
function scopesAt(tokens, offset) { return tokens.find((value) => value.startIndex <= offset && value.endIndex > offset)?.scopes || []; }

test("TextMate, comments, auto-close and snippets follow model mutations", async () => {
  const definition = JSON.parse(JSON.stringify(require("../language-definition.json")));
  const tokens = definition.syntax.tokens;
  tokens.assignment = ":="; tokens.comment = "%%"; tokens.categoryOpen = "<"; tokens.categoryClose = ">";
  definition.syntax.literals.true = "yes";
  const { grammar: rules, generated } = await grammar(definition);
  const line = "value := yes %% model comment";
  const result = rules.tokenizeLine(line).tokens;
  assert.ok(scopesAt(result, line.indexOf(":=")).includes("keyword.operator.assignment.dc"));
  assert.ok(scopesAt(result, line.indexOf("yes")).includes("constant.language.dc"));
  assert.ok(scopesAt(result, line.indexOf("%%")).includes("comment.line.dc"));
  const old = rules.tokenizeLine("value = true # previous syntax").tokens;
  assert.ok(!old.some((value) => value.scopes.includes("comment.line.dc") || value.scopes.includes("constant.language.dc")));
  assert.equal(generated.configuration.comments.lineComment, "%%");
  assert.ok(generated.configuration.autoClosingPairs.some((value) => value.open === "<" && value.close === ">"));
  assert.ok(generated.snippets["DC Section"].body[0].startsWith("<"));
  assert.ok(generated.snippets["DC Section"].body[1].includes(":="));
});

test("generated syntax and metadata match the server packed with the extension", { skip: !fs.existsSync(jar) }, async () => {
  const definition = await readDefinition(jar);
  assert.deepEqual(require("../language-definition.json"), definition);
  const generated = generate(definition);
  assert.deepEqual(require("../syntaxes/dc.tmLanguage.json"), generated.grammar);
  assert.deepEqual(require("../language-configuration.json"), generated.configuration);
  assert.deepEqual(require("../snippets/dc.json"), generated.snippets);
});

test("application definition is separate from the syntax metadata", () => {
  assert.deepEqual(require("../language-definition.json").profiles, []);
  assert.deepEqual(require("../generated/dlc-definition.json"), require("../../../../src/main/resources/META-INF/dlc-manager/dlc-definition.json"));
});
