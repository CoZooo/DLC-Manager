#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_VERSION="$(grep -E '^mod_version=' "${ROOT_DIR}/gradle.properties" | cut -d= -f2)"
MODE="${1:-export}"
RELEASE_DIR="${ROOT_DIR}/output/${MOD_VERSION}/release"

run_export() {
  printf "==> Export mode: build + jar-obfuscator\n"
  "${ROOT_DIR}/gradlew" obfuscateAllYguard --parallel

  if [[ ! -f "${ROOT_DIR}/jar-obfuscator.jar" ]]; then
    echo "Missing file: ${ROOT_DIR}/jar-obfuscator.jar"
    exit 1
  fi
  if [[ ! -f "${ROOT_DIR}/config.yaml" ]]; then
    echo "Missing file: ${ROOT_DIR}/config.yaml"
    exit 1
  fi

  mkdir -p "${ROOT_DIR}/jar-obf-lib"
  mkdir -p "${RELEASE_DIR}"

  copy_merged_intermediary_jar() {
    local mcver="$1"
    local dest_dir="$2"
    local base="${HOME}/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft"
    local candidate=""

    if [[ ! -d "${base}" ]]; then
      return 0
    fi

    shopt -s nullglob
    local primary=(
      "${base}/minecraft-merged-intermediary/${mcver}-loom.mappings."*/"minecraft-merged-intermediary-${mcver}-loom.mappings."*.jar
    )
    local forge_variant=(
      "${base}/forge-${mcver}-"*"-minecraft-merged-intermediary/${mcver}-loom.mappings."*/"forge-${mcver}-"*-minecraft-merged-intermediary-${mcver}-loom.mappings."*.jar
    )
    local neoforge_variant=(
      "${base}/neoforge-"*"-minecraft-merged-intermediary/${mcver}-loom.mappings."*/"neoforge-"*-minecraft-merged-intermediary-${mcver}-loom.mappings."*.jar
    )
    shopt -u nullglob

    if (( ${#primary[@]} > 0 )); then
      candidate="${primary[$(( ${#primary[@]} - 1 ))]}"
    elif (( ${#forge_variant[@]} > 0 )); then
      candidate="${forge_variant[$(( ${#forge_variant[@]} - 1 ))]}"
    elif (( ${#neoforge_variant[@]} > 0 )); then
      candidate="${neoforge_variant[$(( ${#neoforge_variant[@]} - 1 ))]}"
    fi

    if [[ -n "${candidate}" ]]; then
      cp -f "${candidate}" "${dest_dir}/"
      printf "    + merged intermediary: %s\n" "$(basename "${candidate}")"
    fi
  }

  run_one() {
    local loader="$1"
    local mcver="$2"
    local libdir="$3"
    local input_jar="${ROOT_DIR}/output/${MOD_VERSION}/yGuard/DLC-Manager-${loader}-${mcver}-${MOD_VERSION}.jar"
    local obf_jar="${ROOT_DIR}/DLC-Manager-${loader}-${mcver}-${MOD_VERSION}_obf.jar"
    local release_jar="${RELEASE_DIR}/DLC-Manager-${loader}-${mcver}-${MOD_VERSION}.jar"

    if [[ ! -f "${input_jar}" ]]; then
      echo "Missing input jar: ${input_jar}"
      exit 1
    fi
    if [[ ! -d "${ROOT_DIR}/obf-lib/${libdir}" ]]; then
      echo "Missing obf libraries: ${ROOT_DIR}/obf-lib/${libdir}"
      exit 1
    fi

    printf "==> jar-obfuscator: %s\n" "$(basename "${input_jar}")"
    rm -rf "${ROOT_DIR}/jar-obf-lib"/*
    cp -a "${ROOT_DIR}/obf-lib/${libdir}/." "${ROOT_DIR}/jar-obf-lib/"
    copy_merged_intermediary_jar "${mcver}" "${ROOT_DIR}/jar-obf-lib"

    rm -f "${obf_jar}"
    (cd "${ROOT_DIR}" && java -jar jar-obfuscator.jar --jar "${input_jar}" --config config.yaml)

    if [[ ! -f "${obf_jar}" ]]; then
      echo "Obfuscated jar not found: ${obf_jar}"
      exit 1
    fi

    cp -f "${obf_jar}" "${release_jar}"
    rm -f "${obf_jar}"
    rm -rf "${ROOT_DIR}/jar-obf-lib"/*
  }

  run_one "forge" "1.20.1" "forge-1.20.1"
  run_one "fabric" "1.20.1" "fabric-1.20.1"
  run_one "fabric" "1.21.1" "fabric-1.21.1"
  run_one "neoforge" "1.21.1" "neoforge-1.21.1"

  printf "==> Export finished. Release jars in: %s\n" "${RELEASE_DIR}"
  ls -la "${RELEASE_DIR}"
}

case "${MODE}" in
  build)
    TASKS=(build)
    ;;
  obfuscate)
    TASKS=(obfuscateAllYguard)
    ;;
  buildObf)
    TASKS=(build obfuscateAllYguard)
    ;;
  check)
    TASKS=(check)
    ;;
  archBuild)
    TASKS=(archBuild)
    ;;
  archCheck)
    TASKS=(archCheck)
    ;;
  publish)
    TASKS=(archPublish)
    ;;
  archPublish)
    TASKS=(archPublish)
    ;;
  all)
    TASKS=(check build obfuscateAllYguard)
    ;;
  export)
    run_export
    exit 0
    ;;
  *)
    echo "Usage: ./build.sh [build|obfuscate|buildObf|check|archBuild|archCheck|publish|archPublish|all|export]"
    exit 1
    ;;
esac

printf "==> Run tasks: %s\n" "${TASKS[*]}"
"${ROOT_DIR}/gradlew" "${TASKS[@]}" --parallel

printf "==> Done. Output in: %s/output/%s\n" "$ROOT_DIR" "$MOD_VERSION"
printf "==> yGuard output: %s/output/%s/yGuard\n" "$ROOT_DIR" "$MOD_VERSION"
