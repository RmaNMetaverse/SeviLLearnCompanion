"""Validate downloaded CI assets and write truthful installation/build metadata."""
import hashlib
import json
import os
from pathlib import Path

ASSETS = {
    "SeviL-Android-debug.apk": "Android test build; installable APK, debug-signed, not a Play Store release.",
    "SeviL-iOS-simulator.zip": "Compiled .app for a compatible Mac's iOS Simulator; not for physical iPhones.",
    "SeviL-iOS-unsigned.ipa": "Compiled iOS device app; UNSIGNED. Requires Apple signing/provisioning before installation.",
    "SeviL-web-server.tar.gz": "Compiled Next.js standalone web server. Requires Node.js 24 and the separately running SeviL API.",
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

All shared API checks and web, Android and iOS builds passed before publication.
These are development builds, not store releases or evidence of physical-device testing.

| Download | Purpose |
|---|---|
""" + "\n".join(f"| `{name}` | {description} |" for name, description in ASSETS.items()) + f"""

## Android

Install the APK on Android 8.0 or later. It is debug-signed. Without a persistent test signing key, different builds may require uninstalling the previous version; uninstalling deletes local recordings. Export or upload pending recordings first. See the repository's `ANDROID_DEBUG_KEYSTORE_BASE64` instructions to keep test signatures stable.

The app needs a running SeviL API. For a USB-connected phone, run `adb reverse tcp:8000 tcp:8000` on the computer hosting the API, then use `http://127.0.0.1:8000` in the app's Settings. Use an HTTPS API address for remote access.

## iOS Simulator

Unzip `SeviL-iOS-simulator.zip` on a compatible Mac with Xcode installed. Boot an iOS 17+ simulator and run:

```bash
xcrun simctl install booted /absolute/path/to/SeviL.app
xcrun simctl launch booted com.sevil.learncompanion
```

The default simulator API address is `http://localhost:8000` on that Mac. Set another API URL in Settings if needed.

## Physical iPhone/iPad

`SeviL-iOS-unsigned.ipa` is a genuine compiled device build, but it cannot be installed directly on an iPhone. It has no Apple certificate, signature, or provisioning profile. Sign/provision it with your own Apple development/distribution setup, or build the source with your signing team selected in Xcode. No TestFlight or App Store upload is performed by this workflow.

## Web

Extract `SeviL-web-server.tar.gz`, install Node.js 24, and start the separate SeviL API on `127.0.0.1:8000`. From the extracted folder run `node start-local.cjs`; open `http://localhost:3000`. This launcher binds the web server only to your own computer. The compiled API proxy targets `http://127.0.0.1:8000`. For remote hosting or a different internal API address, use the documented Docker setup with a server access token and HTTPS, then rebuild.

Local server setup, API credentials, and deployment instructions: [README]({source}/README.md) and [deployment guide]({source}/docs/DEPLOYMENT.md).

## Integrity and limitations

`SHA256SUMS.txt` and `build-manifest.json` identify the binaries and exact source commit. The standard GitHub source archives also contain the backend and deployment configuration. No classroom files, API keys, or signing credentials are included.

The app remains a single-user pilot. Demo mode uses explicitly synthetic notes. Live AI needs a server-side OpenAI key. Camera, microphone, background operation and real-classroom accuracy still need physical-device validation.
"""
    notes_path.write_text(notes, encoding="utf-8")
    (folder / "INSTALLATION.md").write_text(notes, encoding="utf-8")
    return manifest

if __name__ == "__main__":
    prepare(Path("release"), Path("release-notes.md"))
