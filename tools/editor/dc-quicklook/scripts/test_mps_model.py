#!/usr/bin/env python3
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
library = (root / "../../dclanguage").resolve()
parser = argparse.ArgumentParser(description="Verify native highlighting against a mutated MPS model and its generated definition.")
parser.add_argument("--mps-home", default=os.environ.get("MPS_HOME"))
args = parser.parse_args()
if not args.mps_home:
    parser.error("Set MPS_HOME or pass --mps-home")

def gradle(*tasks):
    subprocess.run([str(library / "gradlew"), "-p", str(library / "mps"), f"-PmpsHome={args.mps_home}", *tasks], check=True)

gradle("prepareDefinitionLanguage")
(root / "build").mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix="mps-mutation-", dir=root / "build") as temporary:
    project = Path(temporary) / "project"
    for relative in ("languages/definition", "solutions/definition", "definition-project"):
        shutil.copytree(library / "mps" / relative, project / relative)
    model_path = project / "solutions/definition/models/dc.mps"
    model = ET.parse(model_path)
    mutations = {
        "C2002": ("P200201", {"assignment": ":=", "comment": "%%", "categoryOpen": "<", "categoryClose": ">"}),
        "C2012": ("P201201", {"true": "yes"}),
    }
    applied = 0
    for node in model.iter("node"):
        mutation = mutations.get(node.get("concept"))
        if not mutation:
            continue
        role, values = mutation
        properties = {prop.get("role"): prop for prop in node.findall("property")}
        name = properties.get("Name")
        if name is not None and name.get("value") in values:
            properties[role].set("value", values[name.get("value")])
            applied += 1
    if applied != 5:
        raise SystemExit("MPS model structure changed; update the mutation fixture")
    model.write(model_path, encoding="UTF-8", xml_declaration=True)
    generated = Path(temporary) / "generated"
    gradle("generateLanguage", f"-PdefinitionProject={project}", f"-PgeneratedOutput={generated}")
    resource = Path(temporary) / "language-definition.json"
    subprocess.run(["python3", str(root / "scripts/sync_language.py"), "--source", str(generated / "resources/com/cozooo/dclanguage/language-definition.json"), "--output", str(resource)], check=True)
    environment = dict(os.environ, DC_MUTATED_DEFINITION=str(resource))
    subprocess.run(["swift", "test", "--package-path", str(root), "--scratch-path", str(root / "build/swift-tests"), "--configuration", "release"], check=True, env=environment)
