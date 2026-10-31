# Automatic development releases

Every push to `main` builds its pushed HEAD revision on GitHub Actions. API checks and web, Android and iOS builds must all succeed before publication. Each successful push receives a separate prerelease named `build-<workflow run number>-<short commit SHA>`. Older pushed revisions finish even if a newer push arrives. A single push containing multiple commits builds its final revision; commits not pushed do not trigger GitHub Actions. Pull requests build and test but never publish releases. The workflow can also be run manually against `main`.

Downloads: https://github.com/RmaNMetaverse/SeviLLearnCompanion/releases

| Asset | Use |
|---|---|
| `SeviL-Android-debug.apk` | Installable Android test build; debug signed, not a Play Store package. |
| `SeviL-iOS-simulator.zip` | Compiled simulator `.app` for a compatible Mac with Xcode. |
| `SeviL-iOS-unsigned.ipa` | Compiled device app; must be signed and provisioned before installation on an iPhone/iPad. |
| `SeviL-web-server.tar.gz` | Compiled Next.js standalone web server, including static assets. Requires Node.js 24 and the separate API. |
| `INSTALLATION.md` | Platform-specific setup and signing limitations. |
| `build-manifest.json` | Exact source revision, build run, sizes and SHA-256 hashes. |
| `SHA256SUMS.txt` | Download integrity checks. |

GitHub also provides the source archives for each release, including the backend, scripts and Docker deployment configuration. Applications still need a running API server; publishing binary downloads does not deploy the backend or web site.

The web package includes `start-local.cjs`. Run `node start-local.cjs` to bind it only to this computer; use the separate Docker deployment instructions for remote hosting.

## iOS signing

GitHub's macOS runners can compile iOS applications without your owning a Mac. They cannot create your Apple developer identity or provisioning permission. The unsigned IPA is deliberately labeled, and the simulator bundle cannot run on a physical iPhone.

To add an installable device IPA later, configure an Apple Developer team, an app identifier, a signing certificate/private key, a provisioning profile and the intended distribution method (registered devices, TestFlight or App Store). Signing material must be held in GitHub Actions secrets, never Git. This workflow currently performs no Apple signing, TestFlight upload or App Store submission.

## Android updates and signing

The workflow uses Android debug signing. Without a persistent key, a runner may produce a different signing identity on another run, preventing an in-place update. Uninstalling loses app-local data, including pending recordings; export/upload those first.

For stable **test** signatures, set the optional repository secret `ANDROID_DEBUG_KEYSTORE_BASE64` to the base64 contents of a standard Android debug keystore (alias `androiddebugkey`, store/key password `android`). This can be the development laptop's existing debug keystore. The workflow restores it privately before the build. Do not put a production signing key in this secret; Play Store release signing needs a separate release configuration.

Do not paste the keystore contents into chat, commit it, or include it in a release. If no secret is present, builds still succeed with a runner-generated debug key. CI build numbers increment Android `versionCode` and iOS `CFBundleVersion`; local builds default to 1.

## Publication behavior

Only the publication job has repository write permission. It receives binaries from the same successful workflow run, verifies all required files exist and are nonempty, creates an exact-commit draft release, uploads binaries/metadata/checksums, then publishes it as a prerelease. Retrying the same run reuses the same tag and replaces its assets. Failed builds do not create a published release. Prereleases are not marked as the latest stable release; use the Releases page rather than GitHub's `/releases/latest` endpoint.
