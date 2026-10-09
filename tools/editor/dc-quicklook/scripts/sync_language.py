#!/usr/bin/env python3
import argparse
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description="Bundle the MPS-generated DC syntax for native Quick Look previews.")
parser.add_argument("--source", type=Path, default=root / "../../dclanguage/src/generated/resources/com/cozooo/dclanguage/language-definition.json")
parser.add_argument("--output", type=Path, default=root / "Shared/Resources/language-definition.json")
parser.add_argument("--check", action="store_true")
args = parser.parse_args()
definition = json.loads(args.source.read_text(encoding="utf-8"))
resource = {key: definition[key] for key in ("formatVersion", "language", "syntax", "policies", "contentHash", "definitionHash")}
if resource["formatVersion"] != 1 or resource["language"] != "dc":
    raise SystemExit("Unsupported DC language definition")
content = json.dumps(resource, ensure_ascii=False, indent=2) + "\n"
current = args.output.read_text(encoding="utf-8") if args.output.exists() else None
if current != content:
    if args.check:
        raise SystemExit("Quick Look language resource is stale; run scripts/sync_language.py")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(content, encoding="utf-8")
