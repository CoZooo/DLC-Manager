# DC QuickLook (macOS)

为 `.dc` 文件提供 macOS Finder Quick Look 预览（空格键），包含基础语法高亮。

## 目录结构

- `project.yml`: XcodeGen 工程定义（Host App + Quick Look Extension）
- `HostApp`: 扩展宿主 App（用于安装与签名）
- `Extension`: Quick Look Preview Extension
- `Shared`: 预览高亮逻辑
- `scripts/generate_project.sh`: 生成 `.xcodeproj`

## 快速开始

1. 安装 XcodeGen

```bash
brew install xcodegen
```

2. 生成工程

```bash
cd /Users/cozozjw/Projects/DLC-Manager/tools/editor/dc-quicklook
./scripts/generate_project.sh
```

3. 打开工程

```bash
open /Users/cozozjw/Projects/DLC-Manager/tools/editor/dc-quicklook/DCQuickLook.xcodeproj
```

4. Xcode 内配置

- 给 `DCQuickLookHost` 和 `DCQuickLookExtension` 选择同一 Team 进行签名
- 运行 `DCQuickLookHost` 一次，Host 会自动执行扩展注册并自动退出
- 如未生效：`System Settings -> Privacy & Security -> Extensions -> Quick Look` 中启用

5. 验证

```bash
qlmanage -r
qlmanage -r cache
qlmanage -p /path/to/test.dc
```

## 当前高亮覆盖

- 节头：`[basic]`
- 键值：`key = value` 的 key 与 `=`/`:`
- 字符串、数字、布尔值
- 注释：`#`、`//`
- 三引号字符串行（`"""`）

## 说明

- 该扩展是完整 Quick Look 预览扩展，不是纯文本回退方案。
- 如果你的 macOS 或 Xcode 版本较旧，建议优先升级到 Xcode 15+。
