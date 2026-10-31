"""Validate downloaded CI assets and write truthful installation/build metadata."""
import hashlib
import json
import os
from pathlib import Path

ASSETS = {
    "SeviL-Android-debug.apk": "Android test build; installable APK, debug-signed, not a Play Store release.",
    "SeviL-iOS-simulator.zip": "Compiled .app for a compatible Mac's iOS Simulator; not for physical iPhones.",
    "SeviL-iOS-unsigned.ipa": "Compiled iOS device app; UNSIGNED. Requires Apple signing/provisioning before installation.",
    "SeviL-web-server.tar.gz": "Static web app and localhost file server. Requires Node.js 24; no SeviL backend required.",
}

def prepare(folder: Path, notes_path: Path, env=os.environ):
    sha = env["GITHUB_SHA"]
    build = env["GITHUB_RUN_NUMBER"]
    repo = env["GITHUB_REPOSITORY"]
    run = env["GITHUB_RUN_ID"]
    manifest = {"commit": sha, "build_number": build, "run_url": f"https://github.com/{repo}/actions/runs/{run}", "assets": []}
    for name, description in ASSETS.items():
        path = folder / name
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f"Required build artifact is missing or empty: {name}")
        with path.open("rb") as file:
            digest = hashlib.file_digest(file, "sha256").hexdigest()
        manifest["assets"].append({"name":name,"description":description,"bytes":path.stat().st_size,"sha256":digest})
    (folder / "build-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    source = f"https://github.com/{repo}/blob/{sha}"
    notes = f"""# SeviL development build {build}

Source commit: `{sha}`

All shared contract checks, browser tests, Android emulator tests, iOS simulator tests and platform builds passed before publication.
These are development builds, not store releases or evidence of physical-device testing.

| Download | Purpose |
|---|---|
""" + "\n".join(f"| `{name}` | {description} |" for name, description in ASSETS.items()) + f"""

## Android

Install the APK on Android 8.0 or later. It is debug-signed. Without a persistent test signing key, different builds may require uninstalling the previous version; uninstalling deletes local recordings. Export or upload pending recordings first. See the repository's `ANDROID_DEBUG_KEYSTORE_BASE64` instructions to keep test signatures stable.

The app keeps its library and files on the phone. Open Settings, paste your own Gemini/OpenAI/Claude key and save. Gemini is the default. No laptop, server URL or cloud database is required. AI processing needs internet; recording and existing handouts work locally.

## iOS Simulator

Unzip `SeviL-iOS-simulator.zip` on a compatible Mac with Xcode installed. Boot an iOS 17+ simulator and run:

```bash
xcrun simctl install booted /absolute/path/to/SeviL.app
xcrun simctl launch booted com.sevil.learncompanion
```

The simulator uses its own local library. Enter your provider key in Settings for live AI.

## Physical iPhone/iPad

`SeviL-iOS-unsigned.ipa` is a genuine compiled device build, but it cannot be installed directly on an iPhone. It has no Apple certificate, signature, or provisioning profile. Sign/provision it with your own Apple development/distribution setup, or build the source with your signing team selected in Xcode. No TestFlight or App Store upload is performed by this workflow.

## Web

Extract `SeviL-web-server.tar.gz`, install Node.js 24, and run `node start-local.cjs` from the extracted folder; open `http://localhost:3000`. This launcher serves static app files only and binds to your own computer. No Python API is needed. The browser stores the library in IndexedDB. Enter your provider key in Settings; browser keys clear on reload/close. Export PDF uses Print/Save as PDF. For online hosting deploy `out/` at the root of a static HTTPS host.

Architecture and deployment instructions: [README]({source}/README.md) and [deployment guide]({source}/docs/DEPLOYMENT.md).

## Integrity and limitations

`SHA256SUMS.txt` and `build-manifest.json` identify the binaries and exact source commit. The standard GitHub source archives also contain the backend and deployment configuration. No classroom files, API keys, or signing credentials are included.

Each device keeps a separate library; cloud sync and full library backup are future work. Clearing storage or uninstalling deletes the local library. Sample notes are explicitly synthetic. Live AI needs your selected provider key on that device (Claude audio requires an explicit Gemini/OpenAI choice). Camera, microphone, background operation and real-classroom accuracy still need physical-device validation.
"""
    notes_path.write_text(notes, encoding="utf-8")
    (folder / "INSTALLATION.md").write_text(notes, encoding="utf-8")
    return manifest

if __name__ == "__main__":
    prepare(Path("release"), Path("release-notes.md"))
