# DC Quick Look development

[简体中文](DEVELOPMENT.zh.md)

## Syntax and previews

The Swift scanner uses the MPS-generated definition from `tools/dclanguage`. Before building, `scripts/sync_language.py` synchronizes tokens, string rules, literals, number patterns, and parsing policies. Quick Look reads this definition locally without starting a JVM.

Supported syntax includes categories and quoted paths, nested categories, colon objects, bare entries, arrays, inline objects, numbers, booleans, null, single/double/triple quotes, multiline strings, and escapes. URLs and comment markers inside strings are treated as string content.

Text loading supports UTF-8 and BOM-marked UTF-16/UTF-32. Highlighting uses UTF-16 ranges. Large files are limited to the first 2 MiB with a truncation notice. Reading and scanning happen in the background; AppKit attributes and UI updates run on the main thread. The extension declares only the `.dc` UTI and does not replace previews for other plain-text files.

## Tests

Run in this directory:

```sh
python3 scripts/sync_language.py --check
swift test --scratch-path build/swift-tests
```

To change assignment, comment, category, and boolean rules in a copy of the real MPS model, regenerate the definition, and run the same Swift tests:

```sh
python3 scripts/test_mps_model.py --mps-home "$MPS_HOME"
```

This leaves the authoritative model and core generated files untouched. Tests cover token ranges, Unicode, truncation boundaries, URLs, preservation of the original text, and light/dark appearance. They do not launch Finder or a preview window.
