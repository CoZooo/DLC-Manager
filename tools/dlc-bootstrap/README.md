# 必要资源的早期安装

Forge 1.20.1 和 NeoForge 1.21.1 将早期安装服务与主模组打包在同一个分发 JAR 中。无需额外 bootstrap JAR、JVM 参数或加载器修改。

## 安装与构建

将对应平台的 `DLC-Manager-<加载器>-<Minecraft版本>-<版本>.jar` 放进实例的 `mods` 目录即可。ApricityUI 仍为外部依赖，不包含在分发文件中。

常规 `remapJar`、`build` 和发布任务自动生成或使用单 JAR 分发文件，输出在 `output/<版本>/`。内部服务构建文件仅存放于 `build/`，不需要分发。客户端和服务端开发运行任务也自动准备单 JAR。

外层 JAR 提供独立的早期服务；经过重映射的主模组完整保存在 `META-INF/dlc-manager/mod.jar` 中。启动时按 SHA-256 解包到实例的 `.dlc-manager/runtime/<哈希>/dlc-manager.jar`，由 Forge/NeoForge 原生候选模组发现接口加入模组扫描。这是自动维护的运行缓存，用户不需要管理，且不会在 `mods` 中生成第二个 DLC Manager 文件。缓存损坏时自动恢复。

服务与游戏类保持隔离。分发文件保留主模组元数据，内部主模组保留原有 Mixin、资源和嵌套依赖，并在 Jar-in-Jar 依赖扫描前提交。开发启动使用 Loom 注册的源文件，不再重复提交内部主模组。更新管理器识别内部缓存与原始分发文件的关系，更新时替换 `mods` 中的外层 JAR。

## 启动流程

```text
ModLauncher 发现早期服务
  → Java 服务 initialize
  → 加载内嵌安装器与 Kotlin 标准库（Kotlin 模式）
  → 解包内部主模组到运行缓存
  → 读取 config/dlc_manager/config.dc 和配置中的所有 DLC 根目录
  → 检查 required/*.dc 及嵌套依赖
  → 下载缺失文件并安装到 appliedTarget
  → 验证必要资源已就绪
  → 记录当前实例的安装完成状态
  → ModLauncher 开始扫描模组
  → 原生候选模组发现接口提交内部主模组
  → 加载器进行依赖检查和 Mixin 初始化
  → 主模组初始化时确认完成状态并复核必要资源
  → 游戏启动
```

ModLauncher 会完成所有服务的 `initialize` 后再调用 `beginScanning`。内置早期服务利用这个顺序，安装成功后直接返回，不创建新的 JVM，也不重新运行加载器。

客户端和专用服务端均同步等待下载、安装及最终校验完成，`runClient` 和 `runServer` 使用同样的流程。下载源、优先级、镜像和重试设置复用现有安装逻辑。服务端读取与现有服务端相同的配置。已有目标文件不会被替换；目标缺失则重新安装。SKIP、FORCE 和旧 UserRemoved 字段均不能绕过检查。

Forge/NeoForge 停用游戏内的必要资源安装提示及专用服务端启动后的自动安装线程。只有早期安装完整成功才会记录完成状态；主模组初始化时检查该状态属于当前实例，并重新检查资源缓存和应用目标都是实际文件。没有运行早期服务、安装未完成或校验失败时均中止加载，不在游戏内补装。运行中更新引入的必要资源在下次启动时安装。

进度写入启动日志，前缀为 `[dlc-manager-bootstrap]`。此阶段不能使用游戏内 ApricityUI。配置无效、下载失败、安装失败或线程中断都会阻止启动，异常明确包含“必要资源安装失败”；修复问题后重新启动实例重试。

Java 早期入口只依赖 JDK 和加载器 API；安装器代码与重定位后的 Gson 位于 `META-INF/dlc-manager/early.jar`。Kotlin JVM 标准库保持原样，独立类加载器优先从内嵌运行库加载 Kotlin 及安装器类，避免与游戏层或其他早期服务共享运行库状态。早期服务不依赖 Kotlin for Forge 已经加载。

## Kotlin 运行库与第三方授权

默认 `enable_kotlin=true` 时，EarlyInstaller 使用 Kotlin 实现，早期入口始终使用 Java。Kotlin JVM 标准库 2.2.20 以原始 JAR 保存在 `META-INF/jarjar/kotlin-stdlib-2.2.20.jar`，附带标准 JarJar `metadata.json`。标准库的类、包名、模块描述符和多版本元数据均不修改。

Forge/NeoForge 的常规 JarJar 依赖发现晚于早期服务初始化，因此不能仅依靠加载器自动解析这个运行库。Java 入口先将安装器与标准库解包到 `.dlc-manager/runtime/early/<哈希>/`，验证缓存完整性后，通过私有类加载器加载标准库与安装器，再继续执行必要资源检查。此过程自动进行，不增加外部分发文件或 JVM 参数。

早期阶段不需要 Kotlin for Forge；游戏主模组仍按原有声明使用外部 Kotlin for Forge。设置 `-Penable_kotlin=false` 时 EarlyInstaller 和主模组都使用 Java 实现，早期运行库不包含 Kotlin 标准库。

分发 JAR 的 `META-INF/NOTICE.txt` 列出实际内嵌的第三方组件，`META-INF/dlc-manager/licenses/` 保存 Apache-2.0 授权正文、版权与重定位修改说明。Kotlin 模式还保留 JVM 标准库涉及的 GWT、Guava、ThreeTen 和 Boost 归属及相应授权正文。它们适用于所标明的第三方组件，主模组自身的许可证不变。

早期服务不包含 Kotlin 编译器、Kotlin/Native 运行时、Minecraft、Forge/NeoForge 或 ApricityUI。加载器 API 仅用于编译；运行时由已安装的加载器提供。

## 支持范围

本次支持将普通模组 JAR、资源包及其他文件在模组扫描前安装到配置指定的位置。它不执行热加载、不替换已存在的 JAR，也不更改必要资源的安装目标。

ModLauncher 的早期服务列表在本服务执行前已确定。如果必要资源自身提供 `ITransformationService` 等早期加载器服务，首次安装后仍可能需要重新启动，让这些服务参与下一次早期发现。已运行的 JVM 所需原生库、启动参数和加载器文件也不属于本次接入范围。

## Fabric 评估

项目使用 Fabric Loader 0.18.4，其顺序为 `load()` → `freeze()` → Mixin 初始化 → `preLaunch`。普通模组的 `main`、`preLaunch` 和语言适配器均不能作为公开的模组发现前安装入口。普通 Mixin 也不能可靠地拦截已经运行的加载器发现过程。

因此 Fabric 1.20.1 和 1.21.1 保留现有安装流程。本次不添加宣称可以继续加载新增模组的 Fabric 早期服务。满足“仅放 mods、无 JVM 参数、无定制 Loader”的同时，尚无已验证的可靠接入方案。

## 验证

根项目的 `test` 包含早期服务及单 JAR 分发测试。Java 与 Kotlin 可分别使用默认参数和 `-Penable_kotlin=false` 验证。

测试覆盖下载完成后才返回、缓存安装、重复启动、目标删除后恢复、多根目录、嵌套依赖、失败阻断、中断阻断和平台上下文恢复。启动校验还覆盖完成状态缺失、其他实例的完成状态、安装后目标丢失、目录冒充目标文件，以及失败后清除完成状态。单 JAR 打包、缓存解包与修复、自更新目标映射均有自动化验证。实际打包产物通过隔离类加载器和 Forge/NeoForge 各自的 ModLauncher API 验证，运行环境不提供 Minecraft、Architectury、ApricityUI 或外部 Kotlin。

依据：[ModLauncher 服务初始化顺序](https://github.com/McModLauncher/modlauncher/blob/main/src/main/java/cpw/mods/modlauncher/TransformationServicesHandler.java)、[Forge 早期服务发现](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/fmlloader/src/main/java/net/minecraftforge/fml/loading/ModDirTransformerDiscoverer.java)、[Fabric Loader 0.18.4 启动顺序](https://github.com/FabricMC/fabric-loader/blob/0.18.4/src/main/java/net/fabricmc/loader/impl/launch/knot/Knot.java)。
