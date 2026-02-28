#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
import json
import re
import subprocess
import sys
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


CHANGELOG_CANDIDATES = [
    "changelog",
    "CHANGELOG",
    "changelog.md",
    "CHANGELOG.md",
    "Changelog",
    "Changelog.md",
]


def run(cmd: list[str], *, text: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, check=True, capture_output=True, text=text)


def git(*args: str, text: bool = True) -> subprocess.CompletedProcess:
    return run(["git", *args], text=text)


def normalize_relpath(path: str) -> str:
    normalized = path.replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    if normalized.startswith("/"):
        normalized = normalized[1:]
    return normalized


def read_file_at_ref(ref: str, rel_path: str) -> bytes:
    cp = subprocess.run(
        ["git", "show", f"{ref}:{rel_path}"],
        check=True,
        capture_output=True,
        text=False,
    )
    return cp.stdout


def find_changelog_text(ref: str) -> str:
    for name in CHANGELOG_CANDIDATES:
        try:
            raw = read_file_at_ref(ref, name)
            text = raw.decode("utf-8", errors="replace").strip()
            if text:
                return text
        except subprocess.CalledProcessError:
            continue
    return ""


def parse_diff(from_ref: str, to_ref: str) -> tuple[set[str], set[str]]:
    cp = subprocess.run(
        [
            "git",
            "-c",
            "core.quotepath=false",
            "diff",
            "--name-status",
            "-z",
            "-M",
            "--diff-filter=ACDMRTUXB",
            from_ref,
            to_ref,
            "--",
        ],
        check=True,
        capture_output=True,
        text=False,
    )
    add_or_modify: set[str] = set()
    deleted: set[str] = set()
    fields = cp.stdout.split(b"\0")
    i = 0
    while i < len(fields):
        raw_status = fields[i]
        i += 1
        if not raw_status:
            continue
        status = raw_status.decode("utf-8", errors="surrogateescape")
        code = status[0]

        def decode_path(raw: bytes) -> str:
            return normalize_relpath(raw.decode("utf-8", errors="surrogateescape"))

        if code == "D":
            if i < len(fields) and fields[i]:
                deleted.add(decode_path(fields[i]))
            i += 1
            continue

        if code == "R":
            if i + 1 < len(fields):
                old_path = fields[i]
                new_path = fields[i + 1]
                if old_path:
                    deleted.add(decode_path(old_path))
                if new_path:
                    add_or_modify.add(decode_path(new_path))
            i += 2
            continue

        if code == "C":
            if i + 1 < len(fields):
                new_path = fields[i + 1]
                if new_path:
                    add_or_modify.add(decode_path(new_path))
            i += 2
            continue

        if i < len(fields) and fields[i]:
            add_or_modify.add(decode_path(fields[i]))
        i += 1
    return add_or_modify, deleted


def filter_paths(
    add_or_modify: set[str],
    deleted: set[str],
) -> tuple[list[str], list[str]]:
    def allowed(path: str) -> bool:
        low = path.lower()
        segments = [seg for seg in low.split("/") if seg]
        basename = segments[-1] if segments else low
        if low in {"changelog", "changelog.md"}:
            return False
        if basename == "version.dc":
            return False
        if ".github" in segments:
            return False
        if basename in {".gitignore", ".gitattributes", ".gitmodules", ".gitkeep"}:
            return False
        if basename.startswith(".git"):
            return False
        return True

    adds = sorted(p for p in add_or_modify if allowed(p))
    dels = sorted(p for p in deleted if allowed(p))
    return adds, dels


def dc_quote(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def dc_string(value: str) -> str:
    normalized = value.replace("\r\n", "\n").replace("\r", "\n")
    if "\n" in normalized and '"""' not in normalized:
        return f'"""{normalized}"""'
    return dc_quote(normalized)


def dc_value(value: object) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, str):
        return dc_string(value)
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False)
    return dc_string(str(value))


def strip_config_extension(name: str) -> str:
    low = (name or "").lower()
    for suffix in (".dc", ".json5", ".json"):
        if low.endswith(suffix):
            return name[: -len(suffix)]
    return name


def detect_remote_required_kind(path: str) -> str | None:
    normalized = normalize_relpath(path).lower()
    parts = [p for p in normalized.split("/") if p]
    if len(parts) < 2 or not parts[-1].endswith((".dc", ".json5", ".json")):
        return None
    kind: str | None = None
    for seg in parts[:-1]:
        if seg in {"remote", "required"}:
            kind = seg
    return kind


def is_remote_required_config(path: str) -> bool:
    return detect_remote_required_kind(path) is not None


def path_exists_at_ref(ref: str, rel_path: str) -> bool:
    cp = subprocess.run(
        ["git", "cat-file", "-e", f"{ref}:{rel_path}"],
        capture_output=True,
        text=False,
    )
    return cp.returncode == 0


def decode_text(raw: bytes) -> str:
    return raw.decode("utf-8", errors="replace")


def parse_scalar(token: str):
    raw = (token or "").strip()
    if not raw:
        return ""
    if raw.endswith(","):
        raw = raw[:-1].rstrip()
    if raw.startswith('"') and raw.endswith('"') and len(raw) >= 2:
        try:
            return ast.literal_eval(raw)
        except Exception:
            return raw[1:-1]
    if raw.startswith("'") and raw.endswith("'") and len(raw) >= 2:
        try:
            return ast.literal_eval(raw)
        except Exception:
            return raw[1:-1]
    if (raw.startswith("[") and raw.endswith("]")) or (raw.startswith("{") and raw.endswith("}")):
        try:
            return json.loads(raw)
        except Exception:
            try:
                return ast.literal_eval(raw)
            except Exception:
                return raw
    raw = re.split(r"\s+#", raw, maxsplit=1)[0]
    raw = re.split(r"\s+//", raw, maxsplit=1)[0]
    raw = raw.strip()
    if not raw:
        return ""
    low = raw.lower()
    if low == "true":
        return True
    if low == "false":
        return False
    if low == "null":
        return None
    try:
        if "." in raw:
            return float(raw)
        return int(raw)
    except ValueError:
        return raw


def extract_key_value(text: str, keys: list[str]):
    if not text or not keys:
        return None
    value = None
    for key in keys:
        pattern = re.compile(
            rf'(?im)^\s*(?:"{re.escape(key)}"|\'{re.escape(key)}\'|{re.escape(key)})\s*[:=]\s*(.+?)\s*$'
        )
        for m in pattern.finditer(text):
            value = parse_scalar(m.group(1))
    return value


def extract_identifier_from_config(text: str, path: str) -> str:
    identifier = extract_key_value(text, ["identifier", "dlc_identifier"])
    if isinstance(identifier, str) and identifier.strip():
        return identifier.strip()
    return strip_config_extension(Path(path).name)


def parse_dc_flat_map(text: str) -> dict[str, object]:
    out: dict[str, object] = {}
    if not text:
        return out
    lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    section: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or stripped.startswith("//"):
            i += 1
            continue
        section_match = re.match(r"^\s*\[(.+)]\s*$", line)
        if section_match:
            raw = section_match.group(1).strip()
            if not raw:
                section = []
            else:
                parts = [p.strip() for p in raw.split(".") if p.strip()]
                section = []
                for part in parts:
                    if (part.startswith('"') and part.endswith('"')) or (part.startswith("'") and part.endswith("'")):
                        v = parse_scalar(part)
                        section.append("" if v is None else str(v))
                    else:
                        section.append(part)
            i += 1
            continue
        kv = re.match(
            r'^\s*(?:"([^"\\]*(?:\\.[^"\\]*)*)"|\'([^\'\\]*(?:\\.[^\'\\]*)*)\'|([A-Za-z0-9_.-]+))\s*=\s*(.*)$',
            line,
        )
        if not kv:
            i += 1
            continue
        if kv.group(1) is not None:
            key = bytes(kv.group(1), "utf-8").decode("unicode_escape")
        elif kv.group(2) is not None:
            key = kv.group(2)
        else:
            key = kv.group(3) or ""
        rhs = (kv.group(4) or "").strip()
        if rhs.startswith('"""'):
            remain = rhs[3:]
            chunks: list[str] = []
            if '"""' in remain:
                chunks.append(remain.split('"""', 1)[0])
            else:
                chunks.append(remain)
                i += 1
                while i < len(lines):
                    seg = lines[i]
                    if '"""' in seg:
                        chunks.append(seg.split('"""', 1)[0])
                        break
                    chunks.append(seg)
                    i += 1
            value: object = "\n".join(chunks)
        else:
            value = parse_scalar(rhs)
        path = ".".join([*section, key]) if section else key
        out[path] = value
        i += 1
    return out


def parse_object_block_values(text: str, object_key: str) -> dict[str, object]:
    out: dict[str, object] = {}
    if not text:
        return out
    pattern = re.compile(rf'(?is)(?:"{re.escape(object_key)}"|{re.escape(object_key)})\s*:\s*\{{(.*?)\}}')
    match = None
    for m in pattern.finditer(text):
        match = m
    if match is None:
        return out
    body = match.group(1) or ""
    pair = re.compile(
        r'(?im)^\s*(?:"([^"\\]*(?:\\.[^"\\]*)*)"|\'([^\'\\]*(?:\\.[^\'\\]*)*)\'|([A-Za-z0-9_.-]+))\s*[:=]\s*(.+?)\s*,?\s*$'
    )
    for m in pair.finditer(body):
        if m.group(1) is not None:
            key = bytes(m.group(1), "utf-8").decode("unicode_escape")
        elif m.group(2) is not None:
            key = m.group(2)
        else:
            key = m.group(3) or ""
        out[f"{object_key}.{key}"] = parse_scalar(m.group(4))
    return out


def parse_legacy_flat_map(text: str, path: str) -> dict[str, object]:
    out: dict[str, object] = {}
    if not text:
        return out
    out["basic.identifier"] = extract_identifier_from_config(text, path)
    for key in (
        "modrinth_versionid",
        "curseforge_fileid",
        "directUrl",
        "priority",
        "type",
        "file_name",
        "isInList",
        "isHidden",
        "AllowEnableByAll",
        "dependencies",
        "conflicts",
        "alarm",
    ):
        value = extract_key_value(text, [key])
        if value is None:
            continue
        if key in {"modrinth_versionid", "curseforge_fileid", "directUrl", "priority"}:
            out[f"download.{key}"] = value
        else:
            out[f"basic.{key}"] = value
    out.update(parse_object_block_values(text, "DisplayName"))
    out.update(parse_object_block_values(text, "tip"))
    return out


def parse_config_flat_map(text: str, path: str) -> dict[str, object]:
    if re.search(r"(?m)^\s*\[[^\]]+]\s*$", text or ""):
        parsed = parse_dc_flat_map(text)
        if parsed:
            return parsed
    return parse_legacy_flat_map(text, path)


def canonicalize_config_map(raw: dict[str, object], path: str) -> dict[str, object]:
    canonical: dict[str, object] = {}
    identifier = None
    for key in ("basic.identifier", "identifier", "dlc_identifier", "basic.dlc_identifier"):
        value = raw.get(key)
        if isinstance(value, str) and value.strip():
            identifier = value.strip()
            break
    if not identifier:
        identifier = strip_config_extension(Path(path).name)
    canonical["basic.identifier"] = identifier

    basic_keys = {
        "identifier": "identifier",
        "dlc_identifier": "identifier",
        "file_name": "file_name",
        "type": "type",
        "isinlist": "isInList",
        "ishidden": "isHidden",
        "allowenablebyall": "AllowEnableByAll",
        "dependencies": "dependencies",
        "conflicts": "conflicts",
        "alarm": "alarm",
    }
    download_keys = {
        "modrinth_versionid": "download.modrinth_versionid",
        "curseforge_fileid": "download.curseforge_fileid",
        "directurl": "download.directUrl",
        "priority": "download.priority",
        "download.modrinth_versionid": "download.modrinth_versionid",
        "download.curseforge_fileid": "download.curseforge_fileid",
        "download.directurl": "download.directUrl",
        "download.priority": "download.priority",
    }
    for key, value in raw.items():
        if value is None:
            continue
        low = key.lower()
        if low in {"identifier", "dlc_identifier", "basic.identifier", "basic.dlc_identifier"}:
            continue
        if low in download_keys:
            canonical[download_keys[low]] = value
            continue
        if low.startswith("displayname."):
            canonical["DisplayName." + key.split(".", 1)[1]] = value
            continue
        if low.startswith("tip."):
            canonical["tip." + key.split(".", 1)[1]] = value
            continue
        base_low = low[6:] if low.startswith("basic.") else low
        if base_low in basic_keys:
            canonical[f"basic.{basic_keys[base_low]}"] = value
    return canonical


def extract_update_fields_from_diff(before: dict[str, object], after: dict[str, object]) -> dict[str, object]:
    fields: dict[str, object] = {}
    keys = set(before.keys()) | set(after.keys())
    for key in sorted(keys):
        old_value = before.get(key)
        new_value = after.get(key)
        if old_value == new_value:
            continue
        if new_value is None:
            continue
        if key == "download.curseforge_fileid":
            value = str(new_value).strip()
            if value:
                fields["curseforge"] = value
            continue
        if key == "download.modrinth_versionid":
            value = str(new_value).strip()
            if value:
                fields["modrinth"] = value
            continue
        if key.startswith("download.") or key.startswith("basic.") or key.startswith("DisplayName.") or key.startswith("tip."):
            fields[key] = new_value
    return fields


def build_auto_update_ops(
    from_ref: str,
    to_ref: str,
    add_paths: list[str],
) -> tuple[dict[str, dict[str, object]], dict[str, dict[str, object]], set[str]]:
    remote_updates: dict[str, dict[str, object]] = {}
    required_updates: dict[str, dict[str, object]] = {}
    converted_paths: set[str] = set()

    for path in add_paths:
        kind = detect_remote_required_kind(path)
        if kind is None:
            continue
        if not path_exists_at_ref(from_ref, path):
            continue
        if not path_exists_at_ref(to_ref, path):
            continue
        old_text = decode_text(read_file_at_ref(from_ref, path))
        new_text = decode_text(read_file_at_ref(to_ref, path))
        before = canonicalize_config_map(parse_config_flat_map(old_text, path), path)
        after = canonicalize_config_map(parse_config_flat_map(new_text, path), path)
        old_identifier = str(before.get("basic.identifier") or "").strip()
        new_identifier = str(after.get("basic.identifier") or "").strip()
        identifier = old_identifier or new_identifier
        if not identifier:
            continue
        fields = extract_update_fields_from_diff(before, after)
        if not fields:
            continue
        target = remote_updates if kind == "remote" else required_updates
        bucket = target.setdefault(identifier, {})
        bucket.update(fields)
        converted_paths.add(path)

    return remote_updates, required_updates, converted_paths


def render_update_dc(
    *,
    platform: str,
    base: str,
    base_new: str,
    dlc: str,
    dlc_new: str,
    changelog: str,
    add_paths: list[str],
    del_paths: list[str],
    remote_updates: dict[str, dict[str, object]],
    required_updates: dict[str, dict[str, object]],
) -> str:
    lines: list[str] = []
    same_base = base.strip() == base_new.strip()
    same_dlc = dlc.strip() == dlc_new.strip()
    lines.append("isHidden = false")
    lines.append("")
    lines.append("[version]")
    lines.append(f"platform = {dc_quote(platform)}")
    lines.append(f"base = {dc_quote(base)}")
    if not same_base:
        lines.append(f"base_new = {dc_quote(base_new)}")
    lines.append(f"dlc = {dc_quote(dlc)}")
    if not same_dlc:
        lines.append(f"dlc_new = {dc_quote(dlc_new)}")
    lines.append("")
    lines.append("[changelog]")
    lines.append(f"en_us = {dc_string(changelog)}")
    lines.append("")
    lines.append("[file]")
    if del_paths:
        lines.append("  [del]")
        for path in del_paths:
            lines.append(f"    {dc_quote(path)}")
    if add_paths:
        if del_paths:
            lines.append("")
        lines.append("  [add]")
        for path in add_paths:
            lines.append(f"    {dc_quote(path)}:")
            lines.append(f"      appliedTarget = {dc_quote(path)}")
            lines.append("      override = true")
            lines.append("")
        if lines and lines[-1] == "":
            lines.pop()
    if not add_paths and not del_paths:
        lines.append("  [del]")
    if remote_updates or required_updates:
        lines.append("")
        lines.append("[update]")
        if remote_updates:
            lines.append("  [remote]")
            for identifier, fields in remote_updates.items():
                lines.append(f"    {dc_quote(identifier)}:")
                for key, value in fields.items():
                    lines.append(f"      {key} = {dc_value(value)}")
                lines.append("")
            if lines and lines[-1] == "":
                lines.pop()
        if required_updates:
            if remote_updates:
                lines.append("")
            lines.append("  [required]")
            for identifier, fields in required_updates.items():
                lines.append(f"    {dc_quote(identifier)}:")
                for key, value in fields.items():
                    lines.append(f"      {key} = {dc_value(value)}")
                lines.append("")
            if lines and lines[-1] == "":
                lines.pop()
    lines.append("")
    return "\n".join(lines)


def copy_added_files(to_ref: str, add_paths: list[str], stage_dir: Path) -> None:
    for rel in add_paths:
        rel_path = Path(rel)
        target = (stage_dir / rel_path).resolve()
        target.parent.mkdir(parents=True, exist_ok=True)
        data = read_file_at_ref(to_ref, rel)
        target.write_bytes(data)


def zip_stage(stage_dir: Path, zip_path: Path) -> None:
    zip_path.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(zip_path, "w", compression=ZIP_DEFLATED) as zf:
        for file_path in sorted(stage_dir.rglob("*")):
            if file_path.is_file():
                arcname = file_path.relative_to(stage_dir).as_posix()
                zf.write(file_path, arcname)


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate DLC update package zip from git diff")
    parser.add_argument("--from-ref", required=True, help="Base git ref of previous update")
    parser.add_argument("--to-ref", default="HEAD", help="Target git ref (default: HEAD)")
    parser.add_argument("--platform", required=True, choices=["client", "server", "universal"])
    parser.add_argument("--base", required=True)
    parser.add_argument("--base-new", required=True)
    parser.add_argument("--dlc", required=True)
    parser.add_argument("--dlc-new", required=True)
    parser.add_argument("--out-dir", default="out")
    parser.add_argument("--work-dir", default="work")
    args = parser.parse_args()

    if args.base.strip() == args.base_new.strip() and args.dlc.strip() == args.dlc_new.strip():
        print("Invalid version input: base/base_new and dlc/dlc_new are both unchanged.", file=sys.stderr)
        return 2

    platform_label = {
        "client": "Client",
        "server": "Server",
        "universal": "Universal",
    }[args.platform]
    version_pair = f"{args.base_new}+{args.dlc_new}"
    release_tag = f"{args.base}+{args.dlc}/{args.base_new}+{args.dlc_new}"

    add_or_modify, deleted = parse_diff(args.from_ref, args.to_ref)
    add_paths, del_paths = filter_paths(add_or_modify, deleted)
    remote_updates, required_updates, converted_paths = build_auto_update_ops(args.from_ref, args.to_ref, add_paths)
    if converted_paths:
        add_paths = [p for p in add_paths if p not in converted_paths]
        del_paths = [p for p in del_paths if p not in converted_paths]

    if not add_paths and not del_paths and not remote_updates and not required_updates:
        print("No changed files after filtering; nothing to package.", file=sys.stderr)
        return 2

    changelog = find_changelog_text(args.to_ref)
    if not changelog:
        changelog = f"Auto-generated update package for {release_tag}"

    stage_dir = Path(args.work_dir) / f"{platform_label}_{version_pair}"
    if stage_dir.exists():
        for p in sorted(stage_dir.rglob("*"), reverse=True):
            if p.is_file():
                p.unlink()
            elif p.is_dir():
                p.rmdir()
    stage_dir.mkdir(parents=True, exist_ok=True)

    update_dc = render_update_dc(
        platform=args.platform,
        base=args.base,
        base_new=args.base_new,
        dlc=args.dlc,
        dlc_new=args.dlc_new,
        changelog=changelog,
        add_paths=add_paths,
        del_paths=del_paths,
        remote_updates=remote_updates,
        required_updates=required_updates,
    )
    (stage_dir / "update.dc").write_text(update_dc, encoding="utf-8")
    copy_added_files(args.to_ref, add_paths, stage_dir)

    out_dir = Path(args.out_dir)
    zip_name = f"{platform_label}_{version_pair}.zip"
    zip_path = out_dir / zip_name
    zip_stage(stage_dir, zip_path)

    summary_path = out_dir / f"{platform_label}_{version_pair}.summary.txt"
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(
        "\n".join(
            [
                f"from_ref={args.from_ref}",
                f"to_ref={args.to_ref}",
                f"platform={args.platform}",
                f"release_tag={release_tag}",
                f"zip_path={zip_path.as_posix()}",
                f"add_count={len(add_paths)}",
                f"del_count={len(del_paths)}",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    changelog_path = out_dir / f"{platform_label}_{version_pair}.changelog.txt"
    changelog_path.write_text(changelog + "\n", encoding="utf-8")

    notes_path = out_dir / f"{platform_label}_{version_pair}.release-notes.md"
    notes_path.write_text(changelog + "\n", encoding="utf-8")

    print(f"zip_path={zip_path.as_posix()}")
    print(f"asset_name={zip_name}")
    print(f"release_tag={release_tag}")
    print(f"add_count={len(add_paths)}")
    print(f"del_count={len(del_paths)}")
    print(f"update_remote_count={len(remote_updates)}")
    print(f"update_required_count={len(required_updates)}")
    print(f"summary_path={summary_path.as_posix()}")
    print(f"changelog_path={changelog_path.as_posix()}")
    print(f"release_notes_path={notes_path.as_posix()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
