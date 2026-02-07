# Architectury Loom Trial

This directory contains **experimental** multi-platform build entry points for DLC Manager:

- `1.20.1`: `common + fabric + forge`
- `1.21.1`: `common + fabric + neoforge`

These builds reuse existing source directories and do not replace current ForgeGradle/Fabric build flows.

## Run

```bash
./gradlew -p architectury/1.20.1 :fabric:build :forge:build
./gradlew -p architectury/1.21.1 :fabric:build :neoforge:build
```

If plugin APIs change, adjust `architectury_plugin_version` / `architectury_loom_version` in each local `gradle.properties`.
