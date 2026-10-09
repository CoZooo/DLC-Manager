# Required Resource Bootstrap Development

[简体中文](DEVELOPMENT.zh.md)

Forge 1.20.1 and NeoForge 1.21.1 package the early installation service and main mod in a single distribution JAR. No extra bootstrap JAR, JVM arguments, or loader changes are needed.

## Installation and packaging

Place the appropriate `DLC-Manager-<loader>-<Minecraft-version>-<version>.jar` in the instance's `mods` directory. ApricityUI remains an external dependency and is not included in the distribution.

The `remapJar` and `build` tasks generate or use the single-JAR distribution in `output/<version>/`. Internal service artifacts stay in `build/` and are not distributed separately. Client and server development launch tasks also prepare the single JAR automatically.

The outer JAR supplies an independent early service and stores the complete remapped main mod at `META-INF/dlc-manager/mod.jar`. At startup, it extracts the payload by SHA-256 into `.dlc-manager/runtime/<hash>/dlc-manager.jar` in the instance. Forge or NeoForge's native candidate discovery interface adds it to mod scanning. This runtime cache is maintained automatically, does not create a second DLC Manager file in `mods`, and is repaired if damaged.

The service is isolated from game classes. The distribution retains main-mod metadata; the inner mod retains its Mixin configuration, resources, and nested dependencies and is submitted before Jar-in-Jar dependency scanning. Development launches use source entries registered by Loom without submitting the inner mod again. The update manager maps the cached payload to the original distribution and replaces the outer JAR in `mods` during an update.

## Startup flow

```text
ModLauncher discovers the early service
  → Java service initialize
  → Load the embedded installer and Kotlin standard library (Kotlin mode)
  → Extract the inner main mod into the runtime cache
  → Read config/dlc_manager/config.dc and all configured DLC roots
  → Check required/*.dc and nested dependencies
  → Download missing files and install them to appliedTarget
  → Verify required resources are ready
  → Record completion for the current instance
  → ModLauncher begins mod scanning
  → Native candidate discovery submits the inner main mod
  → The loader checks dependencies and initializes Mixin
  → Main-mod initialization confirms completion and rechecks resources
  → The game starts
```

ModLauncher completes every service's `initialize` before calling `beginScanning`. The built-in service uses this order and returns after successful installation, without creating a new JVM or running the loader again.

Clients and dedicated servers wait synchronously for downloads, installation, and final validation. `runClient` and `runServer` use the same flow. Download sources, priorities, mirrors, and retries reuse the existing installation logic. The server reads its existing configuration. Existing target files are not replaced; missing targets are installed again. SKIP, FORCE, and the legacy UserRemoved field cannot bypass the checks.

Forge and NeoForge disable the in-game required-resource prompt and the dedicated server's post-startup installation thread. Completion is recorded only after early installation fully succeeds. Main-mod initialization verifies that the record belongs to the current instance and that both cached resources and applied targets are regular files. A missing early-service run, incomplete installation, or failed validation aborts loading instead of installing resources in-game. Required resources introduced by a runtime update are installed on the next launch.

Progress is logged with the `[dlc-manager-bootstrap]` prefix. In-game ApricityUI is unavailable at this stage. Invalid configuration, download or installation failure, and thread interruption stop startup with a required-resource installation error. Fix the cause and restart the instance to retry.

The Java entry point depends only on the JDK and loader APIs. Installer code and relocated Gson are in `META-INF/dlc-manager/early.jar`. The Kotlin JVM standard library is preserved unchanged. An isolated class loader prefers the embedded Kotlin runtime and installer classes, avoiding shared runtime state with the game or other early services. The service does not require Kotlin for Forge to have loaded.

## Kotlin runtime and third-party licensing

With the default `enable_kotlin=true`, EarlyInstaller uses the Kotlin implementation while the early entry point remains Java. Kotlin JVM standard library 2.2.20 is stored as its original JAR at `META-INF/jarjar/kotlin-stdlib-2.2.20.jar`, accompanied by standard JarJar `metadata.json`. Its classes, package names, module descriptors, and multi-release metadata are unchanged.

Forge and NeoForge discover normal JarJar dependencies after early-service initialization, so the service cannot rely on the loader to resolve this runtime. The Java entry point first extracts the installer and standard library into `.dlc-manager/runtime/early/<hash>/`, validates the cache, and loads them through a private class loader before checking required resources. This is automatic and adds no external distribution files or JVM arguments.

The early stage does not need Kotlin for Forge; the main game mod retains its declared external Kotlin for Forge dependency. With `-Penable_kotlin=false`, both EarlyInstaller and the main mod use their Java implementations, and the early runtime does not include the Kotlin standard library.

`META-INF/NOTICE.txt` lists the third-party components actually embedded in the distribution. `META-INF/dlc-manager/licenses/` holds the Apache-2.0 text, copyright notices, and relocation statements. Kotlin mode also preserves the GWT, Guava, ThreeTen, and Boost attributions and license texts used by the JVM standard library. These notices apply to the identified third-party components and do not replace the main mod's license.

The early service does not include the Kotlin compiler, Kotlin/Native runtime, Minecraft, Forge/NeoForge, or ApricityUI. Loader APIs are compile-only dependencies supplied by the installed loader at runtime.

## Supported scope

The service installs ordinary mod JARs, resource packs, and other files into configured locations before mod scanning. It does not perform hot loading, replace existing JARs, or change required-resource destinations.

ModLauncher's early-service list is already fixed when this service runs. If a required resource supplies `ITransformationService` or another early-loader service, the first installation may still require a restart so that its service can be discovered on the next launch. Native libraries needed by an already-running JVM, launch arguments, and loader files are outside this integration's scope.

## Fabric assessment

The project uses Fabric Loader 0.18.4, whose order is `load()` → `freeze()` → Mixin initialization → `preLaunch`. A normal mod's `main`, `preLaunch`, and language adapters do not provide a public installation hook before mod discovery. Ordinary Mixins also cannot reliably intercept loader discovery that has already run.

Fabric 1.20.1 and 1.21.1 retain their existing installation flow. This module provides early installation for Forge and NeoForge; it does not add a custom Fabric Loader or require extra JVM arguments.

## Verification

The root project's `test` task includes early-service and single-JAR distribution tests. Use the default parameters for Kotlin and `-Penable_kotlin=false` for Java.

Tests cover waiting for completed downloads, cached installation, repeated starts, restoring deleted targets, multiple roots, nested dependencies, failure and interruption handling, and platform-context restoration. Startup validation also covers missing completion records, records from another instance, targets removed after installation, directories masquerading as files, and clearing completion after failure. Single-JAR packaging, extraction and cache repair, and self-update target mapping have automated coverage. Tests verify actual packaged artifacts through isolated class loaders and the respective Forge and NeoForge ModLauncher APIs without providing Minecraft, Architectury, ApricityUI, or external Kotlin.

References: [ModLauncher service initialization order](https://github.com/McModLauncher/modlauncher/blob/main/src/main/java/cpw/mods/modlauncher/TransformationServicesHandler.java), [Forge early-service discovery](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/fmlloader/src/main/java/net/minecraftforge/fml/loading/ModDirTransformerDiscoverer.java), and [Fabric Loader 0.18.4 startup order](https://github.com/FabricMC/fabric-loader/blob/0.18.4/src/main/java/net/fabricmc/loader/impl/launch/knot/Knot.java).
