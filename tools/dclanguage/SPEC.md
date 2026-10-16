# DC Text Format 0.1

[简体中文](SPEC.zh.md)

The default MPS Definition model defines the `.dc` text format. The executable definition is `mps/solutions/definition/models/dc.mps`; generated metadata is stored at `src/generated/resources/com/cozooo/dclanguage/language-definition.json`. A `.dc` file is UTF-8 configuration text with an optional UTF-8 BOM. LF, CRLF, and CR line endings are supported. Syntax is separate from application-specific field meaning: any valid document can be parsed, edited, and written without a registry.

## Documents and categories

The root document is an ordered object. Assignments use `name = value`. Bracketed headings open categories, and subsequent fields belong to the current category:

```dc
format = 1

[basic]
identifier = "example"
mode = "hash"
```

Indentation expresses nested categories. Fields may use the same indentation as their bracketed category for compatibility with existing `.dc` files:

```dc
[parent]
  [child]
  name = "example"
```

Category headings with multiple unquoted dot-separated segments specify a complete path from the root:

```dc
[parent.child]
name = "example"

[resources."resource.with.dots".targets."target-id"]
path = "mods/example.jar"
```

Quotes protect dots, spaces, slashes, quotation marks, and other characters within a name. `["parent.child"]` is a single category whose name contains a dot, unlike `[parent.child]`. Dots in field names remain part of the name and are not split implicitly.

A category may be reopened to add more fields. Duplicate fields at the same path and conflicts between values and categories are syntax errors; earlier values are not silently overwritten.

## Colon blocks and bare entries

Colon blocks use indentation to describe object contents. When that indentation ends, fields return to the containing category:

```dc
[preset]
changelog:
  zh_cn = "更新内容"
hidden = false
```

Here, `hidden` belongs to `preset`, not `changelog`.

A bare entry represents an empty object with that name. This supports list-like categories such as dependencies and conflicts:

```dc
[dependencies]
"library"
"mods/extra.jar"

[dependencies.optional]
  [basic]
  identifier = "optional"
```

The generic data model is `dependencies.library = {}`. The library does not assign DLC-specific meaning to category names; applications may convert this structure to a list of names as needed.

## Values

Supported values include:

- Double-quoted strings and single-quoted literal strings.
- Literal multiline strings enclosed by three double or single quotes, preserving line breaks and backslashes.
- `true` and `false`, accepted in either case, and `null`.
- Decimal integers, fractions, and exponent notation, using `BigDecimal` to preserve precision.
- Arrays and inline objects, including nested values.
- Unquoted plain strings for compatibility with existing configurations. Quote text that contains comment markers.

```dc
string = "line 1\nline 2"
literal = 'C:\Games\DLC'
boolean = true
number = 1.25e3
optional = null
items = ["one", "two", {"enabled": true}]
object = {"name": "example", "count": 2}
```

Double-quoted strings accept `\n`, `\r`, `\t`, `\b`, `\f`, `\"`, `\\`, `\/`, and `\uFFFF`. Unrecognized escapes preserve their backslash for compatibility with older paths. Single-quoted and triple-quoted strings do not interpret escapes.

Arrays and inline objects may span multiple lines. Items may be separated by commas or line breaks, and trailing commas are allowed. Inline object keys and values may use either `:` or `=`:

```dc
items = [
  "one", # first item
  "two"
]
options = {
  enabled: true
  path = "mods/example.jar"
}
```

An empty assignment, `field =`, is interpreted as an empty string for compatibility. Missing quotes, missing closing brackets, and invalid object members are errors.

## Comments and formatting

Outside strings, `#` and `//` start line comments:

```dc
# comment
[basic] // category
identifier = "example" # inline comment
url = "https://example.invalid/file#part"
```

Comment markers inside strings are ordinary text. See [write-back behavior](DEVELOPMENT.md#write-back-behavior) for the difference between formatting preservation and normalization.

## Semantic rules

Names retain their original spelling and case. Registries match declared names case-insensitively by default, with an explicit option for exact matching. Unknown categories and fields are always retained.

`required` means the entry must appear explicitly. It does not imply a nonempty string, range restriction, or an additional default. Use a field's `.check(...)` for further constraints. Fields inside an absent optional category are not validated; once the category appears, its required fields are checked.

Application paths such as `DLC/required/example.dc` and `mods/example.jar` are strings. The language library does not expand them to absolute paths, infer a game directory, or rewrite them automatically.
