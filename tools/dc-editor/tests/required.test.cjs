const assert = require("node:assert/strict");
const fs = require("node:fs");
const test = require("node:test");
const ts = require("typescript");

require.extensions[".ts"] = (module, filename) => {
  const result = ts.transpileModule(fs.readFileSync(filename, "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true }
  });
  module._compile(result.outputText, filename);
};

const { documentSchemas, requiredBasicDefaults } = require("../src/schemas/documentSchemas.ts");
const { createEmptyTemplate } = require("../src/services/document/templates.ts");
const { parseStructuredDocument, serializeStructuredDocumentWithPreservedRaw } = require("../src/services/document/simpleDc.ts");
const { translateStaticText } = require("../src/i18n/editorI18n.ts");
const definition = require("../../../src/main/resources/META-INF/dlc-manager/dlc-definition.json");
const schema = documentSchemas.required;
const parse = (raw) => parseStructuredDocument(raw, schema).model;
const save = (raw, model) => serializeStructuredDocumentWithPreservedRaw(raw, model, schema);

function editBasic(raw, changes) {
  const model = parse(raw);
  model.basic = { ...model.basic, ...changes };
  return save(raw, model);
}

test("required fields and unzip default come from the application definition", () => {
  const basic = definition.profiles.find((profile) => profile.id === "required").categories.find((category) => category.name === "basic");
  const fields = schema.sections.find((section) => section.key === "basic").fields;
  assert.deepEqual(fields.map((field) => field.key), basic.fields.map((field) => field.name));
  assert.equal(fields.find((field) => field.key === "unzip").kind, "boolean");
  assert.equal(requiredBasicDefaults.unzip, false);
  assert.equal(parse(createEmptyTemplate("required")).basic.unzip, false);
  assert.ok(!createEmptyTemplate("remote").includes("unzip"));
  assert.ok(!documentSchemas.remote.sections.find((section) => section.key === "basic").fields.some((field) => field.key === "unzip"));
});

test("omitted unzip stays omitted and saving an untouched document is lossless", () => {
  const raw = '# heading\r\n[basic]\r\nfile_name  = "pack.zip" # keep\r\nappliedTarget = "mods/"\r\nfuture = { value: true }\r\n';
  assert.equal(parse(raw).basic.unzip, undefined);
  assert.equal(save(raw, parse(raw)), raw);
  assert.equal(editBasic(raw, { unzip: true }), raw + "unzip = true\r\n");
});

test("toggle round trip retains inline comments and both comment styles", () => {
  const raw = '[basic] // config\nfile_name = "pack.zip"\nunzip  = false   # extraction\nappliedTarget = "mods/" // destination\n';
  const on = editBasic(raw, { unzip: true });
  assert.equal(on, raw.replace("false", "true"));
  assert.equal(parse(on).basic.unzip, true);
  assert.equal(editBasic(on, { unzip: false }), raw);
});

test("directory and rename targets retain exact paths", () => {
  const raw = '[basic]\nfile_name = "pack.zip"\nappliedTarget = "mods/"\nunzip = false\n';
  for (const target of ["mods/", "config/v1.0/", "mods/renamed.zip", "mods/, config/assets/"]) {
    const output = editBasic(raw, { appliedTarget: target });
    assert.equal(parse(output).basic.appliedTarget, target);
    assert.equal(parse(output).basic.unzip, false);
  }
});

test("editing basic preserves unknown fields, multiline data and nested dependencies", () => {
  const raw = [
    '# keep heading', '[download]', 'directUrl = [', '  "https://example.com/a.zip", # keep source', ']',
    '[basic]', 'file_name = "a.zip"', 'appliedTarget = "mods/"', 'unzip = false',
    'future = {', '  unzip = "do not change"', '}', 'text = """', '[basic]', 'unzip = false', '"""',
    '[dependencies.child]', '  [basic]', '  file_name = "child.zip"', '  unzip = true', '  appliedTarget = "config/"',
    '[future]', 'unknown = true', ''
  ].join("\n");
  const output = editBasic(raw, { unzip: true });
  assert.equal(output, raw.replace('appliedTarget = "mods/"\nunzip = false', 'appliedTarget = "mods/"\nunzip = true'));
});

test("editing multiline downloads preserves unrelated configuration", () => {
  const raw = '# head\n[download]\ndirectUrl = [\n  "https://example.com/old.zip",\n] # sources\nfuture = 123\n[basic]\nfile_name = "a.zip"\nunzip = true\n';
  const model = parse(raw);
  model.download.directUrl = ["https://example.com/new.zip"];
  const output = save(raw, model);
  assert.equal(output, raw.replace('[\n  "https://example.com/old.zip",\n]', '["https://example.com/new.zip"]'));
  assert.deepEqual(parse(output).download.directUrl, model.download.directUrl);
});

test("new fields insert in basic without rewriting later sections", () => {
  const raw = '[basic]\nfile_name = "a.zip"\n\n[dependencies]\n"base"\n[dependencies.child]\n  unzip = false\n';
  const output = editBasic(raw, { unzip: true, appliedTarget: "mods/" });
  assert.equal(output, raw.replace('[dependencies]\n', 'appliedTarget = "mods/"\nunzip = true\n[dependencies]\n'));
});

test("required dependency list edits preserve nested dependencies and unknown entries", () => {
  const raw = '[basic]\nfile_name = "a.zip"\n[dependencies]\n# list\n"old"\nfuture = "keep"\n[dependencies.child]\n  unzip = true\n';
  const model = parse(raw);
  model.dependencies.entries = ["new"];
  const output = save(raw, model);
  assert.equal(output, raw.replace('"old"\n', '').replace('[dependencies.child]', '"new"\n[dependencies.child]'));
});

test("missing basic section and empty values can be edited without corrupting content", () => {
  const raw = '# keep\n[download]\ndirectUrl = []';
  const output = editBasic(raw, { unzip: true, appliedTarget: "mods/" });
  assert.equal(parse(output).basic.unzip, true);
  assert.equal(parse(output).basic.appliedTarget, "mods/");
  const empty = '[basic]\nappliedTarget =   # destination\n';
  assert.equal(parse(editBasic(empty, { appliedTarget: "mods/" })).basic.appliedTarget, "mods/");
});

test("ZIP-only help is localized and does not hide the flag for unresolved filenames", () => {
  const fields = schema.sections.find((section) => section.key === "basic").fields;
  const unzip = fields.find((field) => field.key === "unzip");
  const target = fields.find((field) => field.key === "appliedTarget");
  assert.equal(unzip.visibleWhen, undefined);
  assert.match(translateStaticText("zh_cn", unzip.help), /仅对 \.zip 生效/);
  assert.match(translateStaticText("en_us", unzip.help), /Only affects \.zip/);
  for (const locale of ["zh_cn", "en_us"]) {
    assert.match(translateStaticText(locale, target.help), /mods\//);
    assert.match(translateStaticText(locale, target.help), /renamed\.zip/);
  }
});

test("editing legacy dependency lists replaces the source and retains comments", () => {
  const raw = '[basic]\nfile_name = "a.zip"\ndependencies = "old" # preserve note\n';
  const model = parse(raw);
  model.dependencies.entries = ["new"];
  const output = save(raw, model);
  assert.ok(!output.includes('dependencies = "old"'));
  assert.ok(output.includes("# preserve note"));
  assert.deepEqual(parse(output).dependencies.entries, ["new"]);
});

test("all existing document templates still parse and serialize", () => {
  for (const type of Object.keys(documentSchemas)) {
    const template = createEmptyTemplate(type);
    const parsed = parseStructuredDocument(template, documentSchemas[type]);
    assert.ok(parsed.model, type);
    assert.ok(!parsed.issues.some((issue) => issue.severity === "error"), type);
    const output = serializeStructuredDocumentWithPreservedRaw(template, parsed.model, documentSchemas[type]);
    assert.ok(!parseStructuredDocument(output, documentSchemas[type]).issues.some((issue) => issue.severity === "error"), type);
  }
});

test("generic registration has no application fields before explicit registration", () => {
  const { DcLanguages, DcField, registerDefinition } = require("../../dclanguage/js/registry.ts");
  const language = DcLanguages.create();
  assert.deepEqual(language.rootCategory.fields, []);
  assert.equal(registerDefinition({ formatVersion: 1, profiles: [] }).size, 0);
  const options = language.registerMainCategory("options", false);
  const enabled = language.addField(options, new DcField({ name: "enabled", type: "boolean", defaultValue: false }), false);
  assert.equal(language.category(["OPTIONS"]).fields[0], enabled);
  assert.equal(enabled.defaultValue, false);
  assert.throws(() => language.addField(options, new DcField({ name: "ENABLED", type: "boolean" }), false), /already registered/);
  const foreign = DcLanguages.create().registerMainCategory("options", false);
  assert.throws(() => language.addField(foreign, new DcField({ name: "count", type: "number" }), false), /does not belong/);
  assert.throws(() => registerDefinition({ formatVersion: 2, profiles: [] }), /Unsupported/);
  assert.throws(() => registerDefinition({ profiles: [{ id: "a", categories: [] }, { id: "a", categories: [] }] }), /duplicate/);
});
