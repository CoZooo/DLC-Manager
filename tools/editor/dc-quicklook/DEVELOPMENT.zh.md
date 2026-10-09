# DC Quick Look 开发

[English](DEVELOPMENT.md)

## 语法与预览

预览使用 Swift 扫描器，语法来自 `tools/dclanguage` 的 MPS 生成定义。构建前 `scripts/sync_language.py` 同步 token、字符串规则、字面量、数值正则和解析策略；Quick Look 在本地读取定义，不启动 JVM。

覆盖分类及带引号路径、嵌套分类、冒号对象、裸条目、数组、内联对象、数字、布尔值、null、单／双／三引号、多行字符串和转义。字符串中的 URL 与注释符号会识别为字符串内容。

文本支持 UTF-8、UTF-16、UTF-32 BOM，按 UTF-16 范围应用高亮；大文件只读取前 2 MiB 并显示截断提示。读取与扫描在后台完成，AppKit 属性和界面更新在主线程进行。扩展只声明 `.dc` 的 UTI，不接管其他纯文本预览。

## 测试

在本目录执行：

```sh
python3 scripts/sync_language.py --check
swift test --scratch-path build/swift-tests
```

还可修改真实 MPS 模型的副本，调整赋值、注释、分类与布尔字面量，再生成定义并运行同一组 Swift 测试：

```sh
python3 scripts/test_mps_model.py --mps-home "$MPS_HOME"
```

该测试不修改权威模型与核心生成文件。测试涵盖完整 token 范围、Unicode、截断边界、URL、原文保留及明暗外观，不启动 Finder 或预览窗口。
