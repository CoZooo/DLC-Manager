const fs = require("node:fs");
const yauzl = require("yauzl");

function readDefinition(jar) {
  return new Promise((resolve, reject) => {
    yauzl.open(jar, { lazyEntries: true }, (error, zip) => {
      if (error) return reject(error);
      let found = false;
      zip.on("error", reject);
      zip.on("end", () => { if (!found) reject(new Error("Language definition is missing from the server JAR")); });
      zip.on("entry", (entry) => {
        if (entry.fileName !== "com/cozooo/dclanguage/language-definition.json") return zip.readEntry();
        found = true;
        zip.openReadStream(entry, (error, stream) => {
          if (error) { zip.close(); reject(error); return; }
          const chunks = [];
          stream.on("data", (value) => chunks.push(value));
          stream.on("error", reject);
          stream.on("end", () => {
            zip.close();
            try { resolve(JSON.parse(Buffer.concat(chunks).toString("utf8"))); } catch (error) { reject(error); }
          });
        });
      });
      zip.readEntry();
    });
  });
}

const quote = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const unique = (values) => [...new Set(values)];
function generate(definition) {
  const syntax = definition.syntax, token = syntax.tokens;
  const comments = unique([token.comment, token.lineComment]).filter(Boolean).sort((a, b) => b.length - a.length);
  const strings = [...syntax.stringRules].sort((a, b) => b.delimiter.length - a.delimiter.length);
  const operators = unique([token.assignment, token.block, token.objectSeparator]).filter(Boolean).sort((a, b) => b.length - a.length).map(quote).join("|");
  const literalAlternatives = Object.values(syntax.literals).map(quote).join("|");
  const literalPattern = `(?<![\\w])(?:${literalAlternatives})(?![\\w])`;
  const literalFlags = definition.policies.caseInsensitiveBooleans ? `(?i:${literalPattern})` : literalPattern;
  const grammar = {
    name: "DC", scopeName: "source.dc",
    patterns: [{ include: "#comments" }, { include: "#categories" }, { include: "#strings" }, { include: "#keys" }, { include: "#literals" }, { include: "#numbers" }],
    repository: {
      comments: { patterns: comments.map((value) => ({ name: "comment.line.dc", match: quote(value) + ".*$" })) },
      categories: { patterns: [{
        match: `^\\s*(${quote(token.categoryOpen)})(.*?)(${quote(token.categoryClose)})(?=\\s*(?:${comments.map(quote).join("|")}|$))`,
        captures: { 1: { name: "punctuation.definition.section.begin.dc" }, 2: { name: "entity.name.section.dc" }, 3: { name: "punctuation.definition.section.end.dc" } }
      }] },
      strings: { patterns: strings.map((rule) => ({
        name: rule.multiline ? "string.quoted.multi.dc" : "string.quoted.dc",
        begin: quote(rule.delimiter), end: quote(rule.delimiter),
        ...(rule.escaped ? { patterns: [{ name: "constant.character.escape.dc", match: `${quote(token.escape)}.` }] } : {})
      })) },
      keys: { patterns: [{ match: `^\\s*(${syntax.bareKeyPattern})\\s*(${operators})`, captures: { 1: { name: "entity.name.type.property.dc" }, 2: { name: "keyword.operator.assignment.dc" } } }] },
      literals: { patterns: [{ name: "constant.language.dc", match: literalFlags }] },
      numbers: { patterns: [{ name: "constant.numeric.dc", match: `(?<![\\w])(?:${syntax.numberPattern})(?![\\w])` }] }
    }
  };
  const pairs = unique([[token.categoryOpen, token.categoryClose], [token.arrayOpen, token.arrayClose], [token.objectOpen, token.objectClose]].map(JSON.stringify)).map(JSON.parse);
  const configuration = {
    comments: { lineComment: token.comment }, brackets: pairs,
    autoClosingPairs: [...strings.map((rule) => ({ open: rule.delimiter, close: rule.delimiter, notIn: ["string", "comment"] })), ...pairs.map(([open, close]) => ({ open, close }))],
    surroundingPairs: [...strings.map((rule) => [rule.delimiter, rule.delimiter]), ...pairs]
  };
  const basic = strings.find((rule) => !rule.multiline);
  const multiline = strings.find((rule) => rule.multiline);
  const snippets = {
    "DC Section": { prefix: "dc-section", body: [`${token.categoryOpen}\${1:section}${token.categoryClose}`, `\${2:key} ${token.assignment} ${basic.delimiter}\${3:value}${basic.delimiter}`], description: "A section from the shared DC syntax" }
  };
  if (multiline) snippets["DC Multiline String"] = { prefix: "dc-ml", body: [`${multiline.delimiter}\${1:text}`, "${2:next line}", multiline.delimiter], description: "A multiline string from the shared DC syntax" };
  return { grammar, configuration, snippets };
}

function writeGenerated(directory, definition) {
  const output = generate(definition);
  for (const [file, value] of [["syntaxes/dc.tmLanguage.json", output.grammar], ["language-configuration.json", output.configuration], ["snippets/dc.json", output.snippets], ["language-definition.json", definition]]) {
    fs.writeFileSync(require("node:path").join(directory, file), JSON.stringify(value, null, 2) + "\n");
  }
}
module.exports = { readDefinition, generate, writeGenerated };
