# Architectury Loom

This directory contains multi-platform build entry points for DLC Manager:

- `1.20.1`: `common + fabric + forge`
- `1.21.1`: `common + fabric + neoforge`

## Run

```bash
./gradlew -p architectury/1.20.1 :fabric:build :forge:build
./gradlew -p architectury/1.21.1 :fabric:build :neoforge:build
```
