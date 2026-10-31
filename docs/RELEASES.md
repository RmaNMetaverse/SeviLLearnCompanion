# Development releases

Every successful push to `main` creates a separate GitHub prerelease after API/contract checks, browser tests/build, Android build/emulator tests and iOS simulator tests/device build pass. Pull requests build/test without publishing.

- `SeviL-Android-debug.apk`: installable Android 8+ test app, debug-signed.
- `SeviL-iOS-simulator.zip`: compiled app for a compatible Mac's iOS 17+ simulator.
- `SeviL-iOS-unsigned.ipa`: compiled physical-device app; cannot be installed directly on an iPhone without Apple signing/provisioning.
- `SeviL-web-server.tar.gz`: static app and small Node.js 24 file server. Extract and run `node start-local.cjs`; open localhost:3000. No Python API is needed.
- `INSTALLATION.md`, `build-manifest.json`, `SHA256SUMS.txt`: installation steps, source commit and checksums.

Each client has a separate device library and directly contacts its selected AI provider. Enter your key in its Settings. No secrets, classroom files or laptop data are included. iOS signing, TestFlight and store uploads are not configured.

## Stable Android test updates

Store a Base64-encoded debug keystore as the repository Actions secret `ANDROID_DEBUG_KEYSTORE_BASE64` to keep the test signing certificate stable. The workflow restores it to the runner's default debug keystore location. Use the standard debug keystore credentials (`android`, alias `androiddebugkey`) for development only. Never commit the keystore or use it for production distribution.

Without the secret, GitHub runners generate different test certificates. Android then rejects an update over the previous build; uninstalling deletes that installation's local library. Export important files first. The local laptop's debug keystore is separate unless the same keystore is configured for GitHub. The build manifest identifies source and build number, not signing continuity.

## iOS simulator

Unzip the simulator download on a Mac, boot a compatible simulator and run:

```bash
xcrun simctl install booted /absolute/path/to/SeviL.app
xcrun simctl launch booted com.sevil.learncompanion
```

For an iPhone/iPad, build the source with your own signing team or sign/provision the unsigned IPA with your Apple tools. No Mac or Apple credentials are needed merely to download the source and unsigned builds.
