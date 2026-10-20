# DLC 外部管理器

[English](README.md)

不启动 Minecraft，也能在独立窗口中查看和管理 DLC。支持管理本地实例，以及连接已经启用 DLC Manager 服务的服务器。

## 功能

- 查看、搜索、分组和启停本地 DLC。
- 补全可下载内容，应用预设，管理拓展包和更新。
- 查看公告，调整实例的 DLC Manager 设置。
- 连接服务器，管理远程 DLC、下载、预设、拓展包、更新和文件。
- 在整合包作者工具中内嵌 DC Editor，创建、校验和导出 `.dc` 配置。
- 使用中文或英文界面。

## 获取与启动

在 [Releases](https://github.com/CoZooo/DLC-Manager/releases) 中选择适合操作系统和处理器的外部管理器附件。可用平台以该次发布的附件为准。

安装包内置 Java，无需另行安装运行环境：Windows 使用 `.exe` 或 `.msi`，安装时可选择创建桌面快捷方式；macOS 使用 `.dmg` 或 `.pkg`；Linux 使用 `.deb` 或 `.rpm`。

如果下载的是应用包，解压后打开其中的应用。如果下载的是 `.jar`，先安装 Java 25，再用 Java 打开该文件。无法双击启动时，可在文件所在目录执行：

```sh
java -jar DLC-Manager-<版本>-<平台>.jar
```

将文件名替换成实际下载的名称。外部管理器单独运行，无需放进 `mods` 文件夹。

## 管理本地实例

1. 打开管理器，选择游戏实例目录，也可以将目录拖入窗口。
2. 选择包含 `mods`、`config` 等文件夹的实例根目录，避免只选中 `DLC` 子目录。
3. 查看 DLC 列表，按需要调整启停状态或应用预设。
4. 点击应用，并处理界面显示的前置、冲突或确认提示。
5. 完成后启动游戏；如果游戏已经运行，按提示重启或重新加载资源。

请避免在游戏内管理器和外部管理器中同时修改同一个实例。

## 管理服务器

在服务器页面填写服务器地址、DLC Manager 管理端口和密码，然后连接。服务器需要已经安装 DLC Manager 并启用对应服务；管理端口由服务器管理员提供。

连接后，可用的操作会按服务器设置显示。进行更新、删除文件或重启前，请确认当前连接的是目标服务器。

## 常见问题

**没有读到本地内容。** 重新选择实例根目录，并检查该实例的 DLC 路径设置。

**无法连接服务器。** 检查地址、管理端口、密码和服务器是否允许连接。游戏端口与管理端口可能不同。

**下载的程序打不开。** 检查操作系统和处理器是否匹配；`.jar` 版本需要 Java 25。

[返回 DLC Manager](../../README.zh.md)

## 构建安装包

在仓库根目录执行以下命令，生成当前系统支持的两种安装包：

```sh
./gradlew --project-dir tools/dlc-javafx-manager jpackageInstallers
```

`jpackageInstaller` 只生成当前系统的默认格式（Windows EXE、macOS DMG、Linux DEB）。也可以单独执行 `jpackageExe`、`jpackageMsi`、`jpackageDmg`、`jpackagePkg`、`jpackageDeb` 或 `jpackageRpm`。根项目提供 `javafxManagerInstaller` 和 `javafxManagerInstallers` 两个入口。

安装包必须在对应操作系统和处理器架构上构建。需要 JDK 25，以及 Node.js 22 或更高版本；先在 `tools/dc-editor` 执行 `npm ci`，用于生成内嵌的 DC Editor 页面。Windows 还需将 WiX 3.14 的 `bin` 目录加入 PATH，Linux 需要 `fakeroot` 和 `rpm` 打包工具。版本读取主项目的 `mod_version`，输出为 `output/<版本>/DLC-Manager-<版本>-<平台>.<格式>`。安装器内部使用不含预发布后缀的数字版本，文件名和应用 JAR 保留完整版本号。

GitHub Actions 的 **Build External Manager Installers** 工作流支持手动构建，并在发布 Release 时构建和上传安装包。覆盖 Windows x64、Linux x64/ARM64、macOS Intel/Apple Silicon。macOS 构建须通过 Developer ID 签名、公证和票据装订后才能上传。Windows 暂未配置签名。

### macOS 签名与公证

本地签名时，将 `MAC_SIGNING_IDENTITY` 设置为 Developer ID Application 和 Developer ID Installer 证书共有的名称与团队部分，例如 `Your Name (TEAMID)`。钥匙串中须同时包含这两张证书及其私钥；如使用独立钥匙串，可通过 `MAC_SIGNING_KEYCHAIN` 指定路径。然后执行：

```sh
./gradlew --project-dir tools/dlc-javafx-manager jpackageInstallers -PmacSign=true
```

构建会先在独立输入副本中签名 JavaFX JAR 内的原生库，再签名应用、内置 Java 和安装包。签名结果位于 `output/<版本>/signed/`。不传 `-PmacSign=true` 时，本地仍可构建未做发行签名的安装包。

公证前，使用 `xcrun notarytool store-credentials <配置名称>` 将凭据保存至钥匙串，设置 `MAC_NOTARY_PROFILE` 为该配置名称，再执行：

```sh
./gradlew --project-dir tools/dlc-javafx-manager notarizeInstallers -PmacSign=true
```

可以使用 `notarizeDmg` 或 `notarizePkg` 只处理一种格式，也可以从根项目执行 `javafxManagerNotarizedInstallers` 处理两种格式。独立公证钥匙串通过 `MAC_NOTARY_KEYCHAIN` 指定。只有公证结果为 `Accepted`，且票据装订、校验与 Gatekeeper 检查全部成功后，才会写入 `output/<版本>/notarized/`。提交结果和拒绝日志保存在 `build/dlc-javafx-manager/notarization/`。

使用 GitHub Actions 时，先配置以下仓库 Secrets，再触发工作流：

- `MAC_SIGNING_IDENTITY`：两张 Developer ID 证书共有的名称与团队部分。
- `MAC_CERTIFICATES_P12_BASE64`：同时包含两张 Developer ID 证书及其私钥的 PKCS#12 导出文件，使用 Base64 编码。
- `MAC_CERTIFICATES_PASSWORD`：该导出文件的密码。
- `MAC_NOTARY_KEY_BASE64`：App Store Connect 团队 API 私钥（`.p8`），使用 Base64 编码。
- `MAC_NOTARY_KEY_ID` 与 `MAC_NOTARY_ISSUER_ID`：API Key ID 和 Issuer ID。

工作流会将凭据导入临时钥匙串并校验，为两种 macOS 安装包签名、公证，构建失败时也会清理临时凭据。凭据操作的命令输出会被屏蔽，解码后的私钥内容也会注册日志遮罩，原始公证日志不会作为附件上传。凭据缺失或公证失败会中止 macOS 任务，不会继续上传安装包。

针对本软件自身收费须事先获得书面授权；再分发须保留许可并提供对应源码。详见 [许可说明](../../LICENSING.zh.md)。
