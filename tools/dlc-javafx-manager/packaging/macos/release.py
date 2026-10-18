import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile


MACH_O_MAGICS = {
    b"\xfe\xed\xfa\xce", b"\xce\xfa\xed\xfe", b"\xfe\xed\xfa\xcf", b"\xcf\xfa\xed\xfe",
    b"\xca\xfe\xba\xbe", b"\xbe\xba\xfe\xca", b"\xca\xfe\xba\xbf", b"\xbf\xba\xfe\xca",
}


def run(command, **kwargs):
    return subprocess.run(command, check=True, **kwargs)


def signature(path, authority):
    details = run(["codesign", "--display", "--verbose=4", str(path)], capture_output=True, text=True).stderr
    if f"Authority={authority}:" not in details or "flags=0x10000(runtime)" not in details or "Timestamp=" not in details:
        raise RuntimeError(f"Missing Developer ID signature, hardened runtime, or secure timestamp: {path}")
    run(["codesign", "--verify", "--deep", "--strict", "--verbose=2", str(path)])


def sign_jar(path, identity, keychain, work_dir):
    with zipfile.ZipFile(path) as source:
        native_entries = []
        for entry in source.infolist():
            if entry.filename.lower().endswith((".dylib", ".jnilib")):
                with source.open(entry) as stream:
                    if stream.read(4) in MACH_O_MAGICS:
                        native_entries.append(entry.filename)
        if not native_entries:
            return
        if any(name.upper().startswith("META-INF/") and name.upper().endswith((".SF", ".RSA", ".DSA", ".EC")) for name in source.namelist()):
            raise RuntimeError(f"Cannot modify a signed JAR: {path}")
        signed = {}
        for name in native_entries:
            native = work_dir / f"native-{len(list(work_dir.iterdir()))}.dylib"
            native.write_bytes(source.read(name))
            command = ["codesign", "--force", "--timestamp", "--options", "runtime", "--sign", identity]
            if keychain:
                command += ["--keychain", keychain]
            run(command + [str(native)])
            signature(native, "Developer ID Application")
            signed[name] = native.read_bytes()
        rewritten = work_dir / path.name
        with zipfile.ZipFile(rewritten, "w") as output:
            output.comment = source.comment
            for entry in source.infolist():
                output.writestr(entry, signed.get(entry.filename, source.read(entry)))
    shutil.copy2(rewritten, path)
    print(f"Signed {len(signed)} native libraries in {path.name}", flush=True)


def package(args):
    command = args.command
    if command and command[0] == "--":
        command = command[1:]
    if "--input" not in command or "--module-path" not in command or "--mac-sign" not in command:
        raise RuntimeError("Expected a signed jpackage command with --input and --module-path")
    input_index = command.index("--input") + 1
    module_index = command.index("--module-path") + 1
    if command[module_index] != command[input_index]:
        raise RuntimeError("Signing expects the module path and input directory to match")
    args.work_dir.mkdir(parents=True, exist_ok=True)
    work_dir = Path(tempfile.mkdtemp(prefix="package-", dir=args.work_dir))
    signed_input = work_dir / "input"
    shutil.copytree(command[input_index], signed_input)
    native_dir = work_dir / "native"
    native_dir.mkdir()
    for jar in sorted(signed_input.glob("*.jar")):
        sign_jar(jar, f"Developer ID Application: {args.identity}", args.keychain, native_dir)
    command[input_index] = str(signed_input)
    command[module_index] = str(signed_input)
    run(command)


def verify_dmg(path, work_dir):
    run(["hdiutil", "verify", str(path)])
    mount_dir = work_dir / "mount"
    mount_dir.mkdir()
    run(["hdiutil", "attach", str(path), "-readonly", "-nobrowse", "-mountpoint", str(mount_dir)])
    try:
        app = mount_dir / "DLC Manager.app"
        signature(app, "Developer ID Application")
        signature(app / "Contents/runtime/Contents/Home/bin/java", "Developer ID Application")
        if not (mount_dir / "Applications").is_symlink():
            raise RuntimeError("DMG is missing the Applications link")
        for jar in (app / "Contents/app").glob("*.jar"):
            with zipfile.ZipFile(jar) as archive:
                for entry in archive.infolist():
                    if entry.filename.lower().endswith((".dylib", ".jnilib")):
                        native = work_dir / "verify-native.dylib"
                        native.write_bytes(archive.read(entry))
                        signature(native, "Developer ID Application")
    finally:
        run(["hdiutil", "detach", str(mount_dir)])


def notarize(args):
    if not args.profile.strip():
        raise RuntimeError("Specify a notarytool Keychain profile with --profile")
    artifact = args.artifact.resolve(strict=True)
    if artifact.suffix not in (".dmg", ".pkg"):
        raise RuntimeError("Only DMG and PKG installers are supported")
    args.log_dir.mkdir(parents=True, exist_ok=True)
    work_dir = Path(tempfile.mkdtemp(prefix=f"{artifact.stem}-", dir=args.log_dir))
    if artifact.suffix == ".dmg":
        verify_dmg(artifact, work_dir)
    else:
        details = run(["pkgutil", "--check-signature", str(artifact)], capture_output=True, text=True).stdout
        if "Developer ID Installer:" not in details:
            raise RuntimeError("PKG requires a Developer ID Installer signature")
    staged = work_dir / artifact.name
    shutil.copy2(artifact, staged)
    credentials = ["--keychain-profile", args.profile]
    if args.keychain:
        credentials += ["--keychain", args.keychain]
    response = subprocess.run(["xcrun", "notarytool", "submit", str(staged), *credentials,
        "--wait", "--timeout", "30m", "--output-format", "json"], capture_output=True, text=True, check=False)
    (work_dir / "submission.json").write_text(response.stdout, encoding="utf-8")
    (work_dir / "submission.stderr.txt").write_text(response.stderr, encoding="utf-8")
    if response.returncode:
        raise RuntimeError(f"notarytool failed or timed out; see {work_dir}")
    result = json.loads(response.stdout)
    submission_id = result.get("id")
    print(f"Notarization {submission_id}: {result.get('status')}", flush=True)
    if result.get("status") != "Accepted":
        if submission_id:
            subprocess.run(["xcrun", "notarytool", "log", submission_id, *credentials,
                str(work_dir / "notary-log.json")], check=False)
        raise RuntimeError(f"Notarization was not accepted; see {work_dir}")
    run(["xcrun", "stapler", "staple", str(staged)])
    run(["xcrun", "stapler", "validate", str(staged)])
    if staged.suffix == ".dmg":
        run(["spctl", "--assess", "--type", "open", "--context", "context:primary-signature", "--verbose=2", str(staged)])
    else:
        run(["spctl", "--assess", "--type", "install", "--verbose=2", str(staged)])
    args.output_dir.mkdir(parents=True, exist_ok=True)
    destination = args.output_dir / artifact.name
    temporary_output = args.output_dir / f".{artifact.name}.{work_dir.name}.tmp"
    shutil.copy2(staged, temporary_output)
    os.replace(temporary_output, destination)
    print(f"Notarized installer: {destination}", flush=True)


def main():
    parser = argparse.ArgumentParser(description="Sign embedded JavaFX libraries and notarize macOS installers.")
    subparsers = parser.add_subparsers(dest="action", required=True)
    signing = subparsers.add_parser("package")
    signing.add_argument("--identity", required=True)
    signing.add_argument("--keychain", default="")
    signing.add_argument("--work-dir", type=Path, required=True)
    signing.add_argument("command", nargs=argparse.REMAINDER)
    signing.set_defaults(handler=package)
    notary = subparsers.add_parser("notarize")
    notary.add_argument("--profile", required=True)
    notary.add_argument("--keychain", default="")
    notary.add_argument("--output-dir", type=Path, required=True)
    notary.add_argument("--log-dir", type=Path, required=True)
    notary.add_argument("artifact", type=Path)
    notary.set_defaults(handler=notarize)
    args = parser.parse_args()
    if sys.platform != "darwin":
        parser.error("macOS is required")
    args.handler(args)


if __name__ == "__main__":
    main()
