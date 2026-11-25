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
const { documentSchemas40 } = require("../src/schemas/documentSchemas40.ts");
const { ALL_DOCUMENT_TYPES, getDocumentSchemas, getOrderedDocumentTypes, isEditorTargetVersion } = require("../src/schemas/versionedSchemas.ts");
const { detectDocumentType } = require("../src/services/document/typeDetection.ts");
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
  const raw = '# heading\r\n[basic]\r\nsource  = "pack.zip" # keep\r\ntarget = "mods/"\r\nfuture = { value: true }\r\n';
  assert.equal(parse(raw).basic.unzip, undefined);
  assert.equal(save(raw, parse(raw)), raw);
  assert.equal(editBasic(raw, { unzip: true }), raw + "unzip = true\r\n");
});

test("toggle round trip retains inline comments and both comment styles", () => {
  const raw = '[basic] // config\nsource = "pack.zip"\nunzip  = false   # extraction\ntarget = "mods/" // destination\n';
  const on = editBasic(raw, { unzip: true });
  assert.equal(on, raw.replace("false", "true"));
  assert.equal(parse(on).basic.unzip, true);
  assert.equal(editBasic(on, { unzip: false }), raw);
});

test("directory and rename targets retain exact paths", () => {
  const raw = '[basic]\nsource = "pack.zip"\ntarget = "mods/"\nunzip = false\n';
  for (const target of ["mods/", "config/v1.0/", "mods/renamed.zip", "mods/, config/assets/"]) {
    const output = editBasic(raw, { target: target });
    assert.equal(parse(output).basic.target, target);
    assert.equal(parse(output).basic.unzip, false);
  }
});

test("editing basic preserves unknown fields, multiline data and nested dependencies", () => {
  const raw = [
    '# keep heading', '[download]', 'directUrl = [', '  "https://example.com/a.zip", # keep source', ']',
    '[basic]', 'source = "a.zip"', 'target = "mods/"', 'unzip = false',
    'future = {', '  unzip = "do not change"', '}', 'text = """', '[basic]', 'unzip = false', '"""',
    '[dependencies.child]', '  [basic]', '  source = "child.zip"', '  unzip = true', '  target = "config/"',
    '[future]', 'unknown = true', ''
  ].join("\n");
  const output = editBasic(raw, { unzip: true });
  assert.equal(output, raw.replace('target = "mods/"\nunzip = false', 'target = "mods/"\nunzip = true'));
});

test("editing multiline downloads preserves unrelated configuration", () => {
  const raw = '# head\n[download]\ndirectUrl = [\n  "https://example.com/old.zip",\n] # sources\nfuture = 123\n[basic]\nsource = "a.zip"\nunzip = true\n';
  const model = parse(raw);
  model.download.directUrl = ["https://example.com/new.zip"];
  const output = save(raw, model);
  assert.equal(output, raw.replace('[\n  "https://example.com/old.zip",\n]', '["https://example.com/new.zip"]'));
  assert.deepEqual(parse(output).download.directUrl, model.download.directUrl);
});

test("new fields insert in basic without rewriting later sections", () => {
  const raw = '[basic]\nsource = "a.zip"\n\n[dependencies]\n"base"\n[dependencies.child]\n  unzip = false\n';
  const output = editBasic(raw, { unzip: true, target: "mods/" });
  assert.equal(output, raw.replace('[dependencies]\n', 'target = "mods/"\nunzip = true\n[dependencies]\n'));
});

test("required dependency list edits preserve nested dependencies and unknown entries", () => {
  const raw = '[basic]\nsource = "a.zip"\n[dependencies]\n# list\n"old"\nfuture = "keep"\n[dependencies.child]\n  unzip = true\n';
  const model = parse(raw);
  model.dependencies.entries = ["new"];
  const output = save(raw, model);
  assert.equal(output, raw.replace('"old"\n', '').replace('[dependencies.child]', '"new"\n[dependencies.child]'));
});

test("missing basic section and empty values can be edited without corrupting content", () => {
  const raw = '# keep\n[download]\ndirectUrl = []';
  const output = editBasic(raw, { unzip: true, target: "mods/" });
  assert.equal(parse(output).basic.unzip, true);
  assert.equal(parse(output).basic.target, "mods/");
  const empty = '[basic]\ntarget =   # destination\n';
  assert.equal(parse(editBasic(empty, { target: "mods/" })).basic.target, "mods/");
});

test("ZIP-only help is localized and does not hide the flag for unresolved filenames", () => {
  const fields = schema.sections.find((section) => section.key === "basic").fields;
  const unzip = fields.find((field) => field.key === "unzip");
  const target = fields.find((field) => field.key === "target");
  assert.equal(unzip.visibleWhen, undefined);
  assert.match(translateStaticText("zh_cn", unzip.help), /仅对 \.zip 生效/);
  assert.match(translateStaticText("en_us", unzip.help), /Only affects \.zip/);
  for (const locale of ["zh_cn", "en_us"]) {
    assert.match(translateStaticText(locale, target.help), /mods\//);
    assert.match(translateStaticText(locale, target.help), /renamed\.zip/);
  }
});

test("editing legacy dependency lists replaces the source and retains comments", () => {
  const raw = '[basic]\nsource = "a.zip"\ndependencies = "old" # preserve note\n';
  const model = parse(raw);
  model.dependencies.entries = ["new"];
  const output = save(raw, model);
  assert.ok(!output.includes('dependencies = "old"'));
  assert.ok(output.includes("# preserve note"));
  assert.deepEqual(parse(output).dependencies.entries, ["new"]);
});

test("collection include and inline remote sections use collection fields", () => {
  const raw = [
    "[basic]",
    'type = "collection"',
    'include = ["child"]',
    "[remote.example]",
    'target = "mods/example.jar"',
    'directUrl = ["https://example.com/example.jar"]',
    'priority = ["directUrl"]',
    ""
  ].join("\n");
  const parsed = parseStructuredDocument(raw, documentSchemas.info);
  assert.deepEqual(parsed.model.basic.include, ["child"]);
  assert.deepEqual(parsed.model["remote.example"].directUrl, ["https://example.com/example.jar"]);
  assert.deepEqual(parsed.model["remote.example"].priority, ["directUrl"]);
  assert.equal(parsed.issues.some((issue) => issue.severity === "error"), false);
  const output = serializeStructuredDocumentWithPreservedRaw(raw, parsed.model, documentSchemas.info);
  assert.deepEqual(parseStructuredDocument(output, documentSchemas.info).model["remote.example"].directUrl, ["https://example.com/example.jar"]);
});

test("collection inline remote entries are declared one by one", () => {
  const info = documentSchemas.info;
  assert.ok(!info.sections.some((section) => section.key === "remote"));
  assert.deepEqual(info.prefixSections.map((item) => item.prefix), ["remote"]);
  const templateFields = info.prefixSections[0].template.fields.map((field) => field.key);
  for (const key of ["target", "directUrl", "priority", "modrinth_projectid", "curseforge_fileid"]) {
    assert.ok(templateFields.includes(key), key);
  }
  assert.equal(documentSchemas40.info.prefixSections, undefined);
});

test("multiple inline remote sections round-trip independently", () => {
  const raw = [
    "[basic]",
    'type = "collection"',
    'source = "collections/pack.mrpack"',
    "[remote.first]",
    'source = "first.jar"',
    'target = "mods/first.jar"',
    'directUrl = ["https://example.com/first.jar"]',
    "",
    "[remote.second]",
    'source = "second.jar"',
    'target = "config/second.toml"',
    'priority = ["modrinth", "curseforge"]',
    ""
  ].join("\n");
  const parsed = parseStructuredDocument(raw, documentSchemas.info);
  assert.deepEqual(parsed.model["remote.first"].directUrl, ["https://example.com/first.jar"]);
  assert.deepEqual(parsed.model["remote.second"].priority, ["modrinth", "curseforge"]);
  assert.equal(parsed.model.remote, undefined);
  const output = serializeStructuredDocumentWithPreservedRaw(raw, parsed.model, documentSchemas.info);
  assert.ok(!output.includes("[remote.*]"));
  const reparsed = parseStructuredDocument(output, documentSchemas.info);
  assert.deepEqual(reparsed.model["remote.first"], parsed.model["remote.first"]);
  assert.deepEqual(reparsed.model["remote.second"], parsed.model["remote.second"]);
});

test("all existing document templates still parse and serialize", () => {
  for (const type of Object.keys(documentSchemas)) {
    if (!documentSchemas[type].supportsStructured) {
      continue;
    }
    const template = createEmptyTemplate(type);
    const parsed = parseStructuredDocument(template, documentSchemas[type]);
    assert.ok(parsed.model, type);
    assert.ok(!parsed.issues.some((issue) => issue.severity === "error"), type);
    const output = serializeStructuredDocumentWithPreservedRaw(template, parsed.model, documentSchemas[type]);
    assert.ok(!parseStructuredDocument(output, documentSchemas[type]).issues.some((issue) => issue.severity === "error"), type);
  }
});

test("legacy base fields migrate to canonical version fields", () => {
  const legacyVersion = 'base = "1.0.0"\ndlc = "1.0.0"\n';
  const versionModel = parseStructuredDocument(legacyVersion, documentSchemas.version).model;
  assert.equal(versionModel.__root__.version, "1.0.0");
  assert.equal(versionModel.__root__.base, undefined);
  assert.match(serializeStructuredDocumentWithPreservedRaw(legacyVersion, versionModel, documentSchemas.version), /version = "1.0.0"/);

  const legacyUpdate = '[version]\nplatform = "universal"\nbase = "1.0.0"\nbase_new = "1.1.0"\n';
  const updateModel = parseStructuredDocument(legacyUpdate, documentSchemas.update).model;
  assert.equal(updateModel.version.version, "1.0.0");
  assert.equal(updateModel.version.version_new, "1.1.0");
  assert.equal(updateModel.version.base, undefined);
  assert.match(serializeStructuredDocumentWithPreservedRaw(legacyUpdate, updateModel, documentSchemas.update), /version_new = "1.1.0"/);
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


function versionFields(version, type, sectionKey) {
  const section = getDocumentSchemas(version)[type].sections.find((item) => item.key === sectionKey);
  return section ? section.fields : [];
}

test("versioned schema exposes distinct document lists", () => {
  assert.deepEqual(getOrderedDocumentTypes("4.0").filter((type) => type === "required_notice" || type === "folder_metadata"), ["required_notice"]);
  assert.deepEqual(getOrderedDocumentTypes("5.0").filter((type) => type === "required_notice" || type === "folder_metadata"), ["folder_metadata"]);
  assert.ok(ALL_DOCUMENT_TYPES.includes("required_notice"));
  assert.ok(ALL_DOCUMENT_TYPES.includes("folder_metadata"));
  assert.ok(isEditorTargetVersion("4.0"));
  assert.ok(isEditorTargetVersion("5.0"));
  assert.ok(!isEditorTargetVersion("4.1"));
});

test("config fields do not leak across versions", () => {
  const ui40 = versionFields("4.0", "config", "ui");
  const ui50 = versionFields("5.0", "config", "ui");
  assert.ok(ui40.some((field) => field.key === "serverSupport"));
  assert.ok(ui40.some((field) => field.key === "ShowOnFirstRun"));
  assert.ok(ui40.some((field) => field.key === "WaitingTime"));
  assert.ok(!ui50.some((field) => field.key === "serverSupport"));
  assert.ok(ui50.some((field) => field.key === "showOnFirstRun"));
  assert.ok(ui50.some((field) => field.key === "waitingTime"));

  const downloads40 = versionFields("4.0", "config", "downloads");
  const downloads50 = versionFields("5.0", "config", "downloads");
  assert.ok(downloads40.some((field) => field.key === "downloadMirrorProbeCount"));
  assert.ok(downloads40.some((field) => field.key === "downloadMirrorProbeStrategy"));
  assert.ok(!downloads50.some((field) => field.key === "downloadMirrorProbeCount"));

  const modpack50 = versionFields("5.0", "config", "modpack");
  assert.ok(modpack50.some((field) => field.key === "packUpdateChannel"));
  assert.ok(!versionFields("4.0", "config", "modpack").some((field) => field.key === "packUpdateChannel"));
  assert.match(createEmptyTemplate("config", "5.0"), /\npackUpdateChannel = "all"\n/);
  assert.doesNotMatch(createEmptyTemplate("config", "4.0"), /packUpdateChannel/);
  assert.doesNotMatch(createEmptyTemplate("config", "5.0"), /serverSupport/);
  assert.match(createEmptyTemplate("config", "4.0"), /smart_precheck/);
});

test("version and update templates use the selected version format", () => {
  const version40 = createEmptyTemplate("version", "4.0");
  assert.match(version40, /^base = "1\.0\.0"/m);
  assert.match(version40, /^dlc = ""/m);
  assert.doesNotMatch(version40, /^version = /m);

  const version50 = createEmptyTemplate("version", "5.0");
  assert.match(version50, /^version = /m);
  assert.doesNotMatch(version50, /^base = /m);

  const update40 = createEmptyTemplate("update", "4.0");
  assert.match(update40, /^base = ""/m);
  assert.match(update40, /^base_new = ""/m);
  assert.doesNotMatch(update40, /^version = /m);

  const update50 = createEmptyTemplate("update", "5.0");
  assert.match(update50, /^version = ""/m);
  assert.doesNotMatch(update50, /^base = ""/m);
  assert.doesNotMatch(update50, /^base_new = ""/m);
});

test("document detection follows the target version", () => {
  assert.equal(detectDocumentType("DLC/required/notice.dc", "", "4.0"), "required_notice");
  assert.equal(detectDocumentType("DLC/required/notice.dc", "", "5.0"), "notice");
  assert.equal(detectDocumentType("DLC/foo/_metadata.dc", "", "5.0"), "folder_metadata");
});

test("4.0 templates parse without errors for every ordered type", () => {
  const schemas = getDocumentSchemas("4.0");
  for (const type of getOrderedDocumentTypes("4.0")) {
    if (!schemas[type].supportsStructured) {
      continue;
    }
    const parsed = parseStructuredDocument(createEmptyTemplate(type, "4.0"), schemas[type]);
    assert.ok(!parsed.issues.some((issue) => issue.severity === "error"), `${type}: ${JSON.stringify(parsed.issues)}`);
  }
});

test("legacy fields stay hidden in 5.0 and visible in 4.0", () => {
  const version50 = versionFields("5.0", "version", "__root__");
  const version40 = versionFields("4.0", "version", "__root__");
  const update50 = versionFields("5.0", "update", "version");
  const update40 = versionFields("4.0", "update", "version");
  assert.ok(version50.find((field) => field.key === "base").hiddenInForm);
  assert.ok(version50.find((field) => field.key === "dlc").hiddenInForm);
  assert.ok(!version40.find((field) => field.key === "base").hiddenInForm);
  assert.ok(!version40.find((field) => field.key === "dlc").hiddenInForm);
  assert.ok(update50.find((field) => field.key === "dlc").hiddenInForm);
  assert.ok(!update40.find((field) => field.key === "dlc").hiddenInForm);
});

test("collection fields stay in the 5.0 info schema", () => {
  assert.ok(versionFields("5.0", "info", "basic").some((field) => field.key === "include"));
  assert.ok(!versionFields("4.0", "info", "basic").some((field) => field.key === "include"));
});
