# DC 文本格式 0.1

[English](SPEC.md)

`.dc` 文本格式由 MPS Definition 默认模型定义。可执行定义保存在 `mps/solutions/definition/models/dc.mps`，对应生成元数据位于 `src/generated/resources/com/cozooo/dclanguage/language-definition.json`。`.dc` 是 UTF-8 配置文本，可有 UTF-8 BOM；支持 LF、CRLF 和 CR 换行。语法与字段的业务含义分开：没有注册表也能解析、修改、写回任何合法文档。

## 文档与分类

根文档是有序对象。赋值为 `名称 = 值`。方括号声明分类，后续字段归属于当前分类：

```dc
format = 1

[basic]
identifier = "example"
mode = "hash"
```

缩进表示嵌套分类；字段可以与方括号分类保持相同缩进，兼容既有 `.dc` 文件：

```dc
[parent]
  [child]
  name = "example"
```

包含多个未引用点分段的分类头使用完整的根路径：

```dc
[parent.child]
name = "example"

[resources."resource.with.dots".targets."target-id"]
path = "mods/example.jar"
```

引号保护名称内的点、空格、斜杠、引号等字符。`["parent.child"]` 是名称包含点的单个分类，与 `[parent.child]` 不同。字段名里的点属于名称，不会被隐式拆分。

允许再次打开同一个分类添加其他字段。相同路径的字段重复定义、值与分类冲突会报告语法错误，不静默覆盖前值。

## 冒号块与裸条目

冒号块用缩进表示对象内容。内容缩进结束后回到所属分类：

```dc
[preset]
changelog:
  zh_cn = "更新内容"
hidden = false
```

以上 `hidden` 属于 `preset`，而不是 `changelog`。

裸条目表示对应名称的空对象，可用于依赖／冲突等列表式分类：

```dc
[dependencies]
"library"
"mods/extra.jar"

[dependencies.optional]
  [basic]
  identifier = "optional"
```

通用数据模型为 `dependencies.library = {}`。库不把某个分类名字预设为 DLC 业务字段；应用可根据需要把这种结构转换为名称列表。

## 值

支持：

- 双引号字符串，单引号原始字符串。
- 三个双引号或三个单引号包围的原始多行字符串，保留其中的换行和反斜杠。
- `true`／`false`（大小写均接受）、`null`。
- 十进制整数、小数、指数数字，使用 `BigDecimal` 保留精度。
- 数组、内联对象，允许嵌套。
- 未引用的普通字符串，兼容现有配置；包含注释标记的文本建议加引号。

```dc
string = "line 1\nline 2"
literal = 'C:\Games\DLC'
boolean = true
number = 1.25e3
optional = null
items = ["one", "two", {"enabled": true}]
object = {"name": "example", "count": 2}
```

双引号字符串接受 `\n`、`\r`、`\t`、`\b`、`\f`、`\"`、`\\`、`\/` 和 `\uFFFF`。为兼容旧路径，未识别的转义保留其反斜杠。单引号及三引号字符串不进行转义替换。

数组与内联对象可跨行，可用逗号或换行分隔元素，并允许尾逗号。内联对象的键和值可用 `:` 或 `=` 分隔：

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

空赋值 `field =` 按旧语法解释为空字符串；缺少引号、缺少闭合括号或错误的对象成员会报错。

## 注释与排版

字符串外的 `#` 和 `//` 开始行注释：

```dc
# comment
[basic] // category
identifier = "example" # inline comment
url = "https://example.invalid/file#part"
```

字符串内的注释标记是文本。格式保留和规范化写回的区别见 [写回行为](DEVELOPMENT.zh.md#写回行为)。

## 语义规则

名称在文本中保留原样，不强制转换大小写。注册表默认对已声明名称作不区分大小写的匹配，可显式开启精确模式。未知分类和字段始终保留。

`required` 只表示必须显式出现，不隐含非空字符串、范围或额外默认值。额外限制可用字段 `.check(...)` 声明。可选分类缺失时不检查内部字段；分类出现后会检查其必填字段。

业务路径如 `DLC/required/example.dc`、`mods/example.jar` 是字符串。语言库不会将它们展开为绝对路径、推断游戏目录或自动修改路径。
